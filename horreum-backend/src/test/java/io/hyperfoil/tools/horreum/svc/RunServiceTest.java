package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Status;
import javax.transaction.TransactionManager;

import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.RunService;
import io.hyperfoil.tools.horreum.entity.json.DataSet;
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
import io.vertx.core.eventbus.EventBus;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(NoGrafanaProfile.class)
public class RunServiceTest extends BaseServiceTest {

   @Inject
   EntityManager em;

   @Inject
   TransactionManager tm;

   @Inject
   RoleManager roleManager;

   @Inject
   RunService runService;

   @Inject
   EventBus eventBus;

   @org.junit.jupiter.api.Test
   public void testDataSetCreated(TestInfo info) throws InterruptedException {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);
      Test test = createTest(createExampleTest(getTestName(info)));
      Schema schema = createExampleSchema(info);
      setTestVariables(test, "Value", "value");

      uploadRun(runWithValue(schema, 42).toString(), test.name);

      try (CloseMe ignored = roleManager.withRoles(em, Arrays.asList(TESTER_ROLES))) {
         DataSet event = dataSetQueue.poll(10, TimeUnit.SECONDS);
         assertNotNull(event);
         assertNotNull(event.id);
         Integer id = event.id;
         DataSet ds = DataSet.findById(id);
         assertNotNull(ds);
      }
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
   public void testDataSetQueryNoSchema(TestInfo info) throws Exception {
      String value = testDataSetQuery("$.value", false, null);
      assertEquals("24", value);
   }

   @org.junit.jupiter.api.Test
   public void testDataSetQueryNoSchemaStrict(TestInfo info) throws Exception {
      String value = testDataSetQuery("$[1].value", false, null);
      assertEquals("42", value);
   }

   @org.junit.jupiter.api.Test
   public void testDataSetQueryNoSchemaArray(TestInfo info) throws Exception {
      String value = testDataSetQuery("$.value", true, null);
      assertEquals("[24, 42]", value);
   }

   @org.junit.jupiter.api.Test
   public void testDataSetQuerySchema(TestInfo info) throws Exception {
      String value = testDataSetQuery("$.value", false, "B");
      assertEquals("42", value);
   }

   @org.junit.jupiter.api.Test
   public void testDataSetQuerySchemaArray(TestInfo info) throws Exception {
      String value = testDataSetQuery("$.value", true, "B");
      assertEquals("[42]", value);
   }

   private String testDataSetQuery(String jsonPath, boolean array, String schemaUri) throws Exception {
      ArrayNode data = JsonNodeFactory.instance.arrayNode();
      ObjectNode a = JsonNodeFactory.instance.objectNode();
      ObjectNode b = JsonNodeFactory.instance.objectNode();
      a.put("$schema", "A");
      a.put("value", 24);
      b.put("$schema", "B");
      b.put("value", 42);
      data.add(a).add(b);

      Run run = new Run();
      DataSet ds = new DataSet();
      tm.begin();
      try (CloseMe ignored = roleManager.withRoles(em, Arrays.asList(UPLOADER_ROLES))) {
         run.data = JsonNodeFactory.instance.objectNode();
         ds.run = run;
         ds.testid = run.testid = 0;
         ds.start = ds.stop = run.start = run.stop = Instant.now();
         ds.data = data;
         ds.owner = run.owner = UPLOADER_ROLES[0];
         run.persistAndFlush();
         ds.persistAndFlush();
      } finally {
         if (tm.getTransaction().getStatus() == Status.STATUS_ACTIVE) {
            tm.commit();
         } else {
            tm.rollback();
            fail();
         }
      }
      RunService.QueryResult queryResult = runService.queryDataSet(ds.id, jsonPath, array, schemaUri);
      assertTrue(queryResult.valid);
      String value = queryResult.value;
      tm.begin();
      Throwable error = null;
      try (CloseMe ignored = roleManager.withRoles(em, Arrays.asList(Roles.HORREUM_SYSTEM))) {
         DataSet.findById(ds.id).delete();
         Run.findById(run.id).delete();
      } catch (Throwable t) {
         error = t;
      } finally {
         if (tm.getTransaction().getStatus() == Status.STATUS_ACTIVE) {
            tm.commit();
         } else {
            tm.rollback();
            fail(error);
         }
      }
      return value;
   }
}
