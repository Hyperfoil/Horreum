package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;
import javax.persistence.EntityManager;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.entity.alerting.CalculationLog;
import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;
import io.hyperfoil.tools.horreum.entity.alerting.RegressionDetection;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Schema;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.regression.RelativeDifferenceRegressionModel;
import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.server.RoleManager;
import io.hyperfoil.tools.horreum.test.NoGrafanaProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.RestAssured;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(NoGrafanaProfile.class)
public class AlertingServiceTest extends BaseServiceTest {
   private static final Logger log = Logger.getLogger(AlertingServiceTest.class);

   @Inject
   EventBus eventBus;

   @Inject
   EntityManager em;

   @Inject
   RoleManager roleManager;

   @Inject
   Vertx vertx;

   @org.junit.jupiter.api.Test
   public void testNotifications(TestInfo info) throws InterruptedException {
      Test test = createTest(createExampleTest(getTestName(info)));
      Schema schema = createExampleSchema(info);
      setTestVariables(test, "Value", "value");

      BlockingQueue<DataPoint.Event> dpe = eventConsumerQueue(DataPoint.Event.class, DataPoint.EVENT_NEW);
      uploadRun(runWithValue(schema, 42).toString(), test.name);

      DataPoint.Event event1 = dpe.poll(10, TimeUnit.SECONDS);
      assertNotNull(event1);
      assertEquals(42d, event1.dataPoint.value);
      assertTrue(event1.notify);

      RestAssured.given().auth().oauth2(TESTER_TOKEN)
            .post("/api/test/" + test.id + "/notifications?enabled=false")
            .then().statusCode(204);

      uploadRun(runWithValue(schema, 0).toString(), test.name);

      DataPoint.Event event2 = dpe.poll(10, TimeUnit.SECONDS);
      assertNotNull(event2);
      assertEquals(0, event2.dataPoint.value);
      assertFalse(event2.notify);
   }

   private ObjectNode runWithValue(Schema schema, double value) {
      ObjectNode runJson = JsonNodeFactory.instance.objectNode();
      runJson.put("$schema", schema.uri);
      runJson.put("value", value);
      return runJson;
   }

   private <E> BlockingQueue<E> eventConsumerQueue(Class<? extends E> eventClass, String eventType) {
      BlockingQueue<E> dpe = new LinkedBlockingDeque<>();
      eventBus.consumer(eventType, msg -> {
         if (eventClass.isInstance(msg.body())) {
            dpe.add(eventClass.cast(msg.body()));
         }
      });
      return dpe;
   }

   @org.junit.jupiter.api.Test
   public void testLogging(TestInfo info) throws InterruptedException, ExecutionException, TimeoutException {
      Test test = createTest(createExampleTest(getTestName(info)));
      Schema schema = createExampleSchema(info);
      setTestVariables(test, "Value", "value");

      // This run won't contain the 'value'
      ObjectNode runJson = JsonNodeFactory.instance.objectNode();
      runJson.put("$schema", schema.uri);

      BlockingQueue<MissingRunValuesEvent> missingQueue = eventConsumerQueue(MissingRunValuesEvent.class, Run.EVENT_MISSING_VALUES);
      int runId = uploadRun(runJson, test.name);

      assertNotNull(missingQueue.poll(10, TimeUnit.SECONDS));

      try (CloseMe ignored = roleManager.withRoles(em, Arrays.asList(TESTER_ROLES))) {
         List<CalculationLog> logs = CalculationLog.find("runid", runId).list();
         // If this fails this might be a race - I thought it's fixed with quarkus.datasource.jdbc.transaction-isolation-level=serializable
         assertTrue(logs.size() > 0);

         deleteTest(test);

         // ordered execution should postpone us until all events are processed
         vertx.executeBlocking(Promise::complete, true).toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

         em.clear();
         logs = CalculationLog.find("runid", runId).list();
         assertEquals(0, logs.size());
      }
   }

   @org.junit.jupiter.api.Test
   public void testRegressionDetection(TestInfo info) throws InterruptedException {
      Test test = createTest(createExampleTest(getTestName(info)));
      Schema schema = createExampleSchema(info);
      RegressionDetection rd = new RegressionDetection();
      rd.model = RelativeDifferenceRegressionModel.NAME;
      rd.config = JsonNodeFactory.instance.objectNode().put("threshold", 0.1).put("minPrevious", 2).put("window", 2).put("filter", "mean");
      setTestVariables(test, "Value", "value", rd);

      BlockingQueue<DataPoint.Event> datapointQueue = eventConsumerQueue(DataPoint.Event.class, DataPoint.EVENT_NEW);
      BlockingQueue<Change.Event> changeQueue = eventConsumerQueue(Change.Event.class, Change.EVENT_NEW);

      long ts = System.currentTimeMillis();
      uploadRun(ts, ts, runWithValue(schema, 1), test.name);
      uploadRun(ts + 1, ts + 1, runWithValue(schema, 2), test.name);
      int run3 = uploadRun(ts + 2, ts + 2, runWithValue(schema, 1), test.name);
      int run4 = uploadRun(ts + 3, ts + 3, runWithValue(schema, 2), test.name);

      assertValue(datapointQueue, 1);
      assertValue(datapointQueue, 2);
      assertValue(datapointQueue, 1);
      assertValue(datapointQueue, 2);

      assertNull(changeQueue.poll(50, TimeUnit.MILLISECONDS));

      uploadRun(ts + 4, ts + 4, runWithValue(schema, 3), test.name);
      assertValue(datapointQueue, 3);

      Change.Event changeEvent1 = changeQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(changeEvent1);
      // The change is detected already at run 4 because it's > than the previous mean
      assertEquals(run4, changeEvent1.change.runId);

      ((ObjectNode) rd.config).put("filter", "min");
      setTestVariables(test, "Value", "value", rd);
      // After changing the variable the past datapoints and changes are removed; we need to recalculate them again
      jsonRequest().post("/api/alerting/recalculate?test=" + test.id).then().statusCode(204);

      assertValue(datapointQueue, 1);
      assertValue(datapointQueue, 2);
      assertValue(datapointQueue, 1);
      assertValue(datapointQueue, 2);
      assertValue(datapointQueue, 3);

      // now we'll find a change already at run3
      Change.Event changeEvent2 = changeQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(changeEvent2);
      assertEquals(run3, changeEvent2.change.runId);

      int run6 = uploadRun(ts + 5, ts + 5, runWithValue(schema, 1.5), test.name);
      assertValue(datapointQueue, 1.5);

      // mean of previous values is 1.5, now the min is 1.5 => no change
      assertNull(changeQueue.poll(50, TimeUnit.MILLISECONDS));

      uploadRun(ts + 6, ts + 6, runWithValue(schema, 2), test.name);
      assertValue(datapointQueue, 2);

      // mean of previous is 2, the last value doesn't matter (1.5 is lower than 2 - 10%)
      Change.Event changeEvent3 = changeQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(changeEvent3);
      assertEquals(run6, changeEvent3.change.runId);
   }

   private void assertValue(BlockingQueue<DataPoint.Event> datapointQueue, double value) throws InterruptedException {
      DataPoint.Event dpe = datapointQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(dpe);
      assertEquals(value, dpe.dataPoint.value);

   }

}
