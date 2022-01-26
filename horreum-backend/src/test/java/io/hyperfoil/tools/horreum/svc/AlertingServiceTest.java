package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Schema;
import io.hyperfoil.tools.horreum.entity.json.Test;
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
      addVariableToTest(test, "Value", "value");

      ObjectNode runJson = JsonNodeFactory.instance.objectNode();
      runJson.put("$schema", schema.uri);
      runJson.put("value", 42);

      BlockingQueue<DataPoint.Event> dpe = eventConsumerQueue(DataPoint.Event.class, DataPoint.EVENT_NEW);
      uploadRun(runJson.toString(), test.name);

      DataPoint.Event event1 = dpe.poll(10, TimeUnit.SECONDS);
      assertNotNull(event1);
      assertEquals(42d, event1.dataPoint.value);
      assertTrue(event1.notify);

      RestAssured.given().auth().oauth2(TESTER_TOKEN)
            .post("/api/test/" + test.id + "/notifications?enabled=false")
            .then().statusCode(204);

      runJson.put("value", 0);
      uploadRun(runJson.toString(), test.name);

      DataPoint.Event event2 = dpe.poll(10, TimeUnit.SECONDS);
      assertNotNull(event2);
      assertEquals(0, event2.dataPoint.value);
      assertFalse(event2.notify);
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
      addVariableToTest(test, "Value", "value");

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

}
