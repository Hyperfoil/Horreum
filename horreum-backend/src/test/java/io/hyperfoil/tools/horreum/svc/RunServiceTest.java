package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Status;
import javax.transaction.TransactionManager;

import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.RunService;
import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.entity.json.Label;
import io.hyperfoil.tools.horreum.entity.json.NamedJsonPath;
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
      BlockingQueue<E> queue = new LinkedBlockingDeque<>();
      eventBus.consumer(eventType, msg -> {
         if (eventClass.isInstance(msg.body())) {
            queue.add(eventClass.cast(msg.body()));
         }
      });
      return queue;
   }

   @org.junit.jupiter.api.Test
   public void testDataSetQueryNoSchema() {
      String value = testDataSetQuery("$.value", false, null);
      assertEquals("24", value);
   }

   @org.junit.jupiter.api.Test
   public void testDataSetQueryNoSchemaStrict() {
      String value = testDataSetQuery("$[1].value", false, null);
      assertEquals("42", value);
   }

   @org.junit.jupiter.api.Test
   public void testDataSetQueryNoSchemaArray() {
      String value = testDataSetQuery("$.value", true, null);
      assertEquals("[24, 42]", value);
   }

   @org.junit.jupiter.api.Test
   public void testDataSetQuerySchema() {
      String value = testDataSetQuery("$.value", false, "B");
      assertEquals("42", value);
   }

   @org.junit.jupiter.api.Test
   public void testDataSetQuerySchemaArray() {
      String value = testDataSetQuery("$.value", true, "B");
      assertEquals("[42]", value);
   }

   private String testDataSetQuery(String jsonPath, boolean array, String schemaUri) {
      return withExampleDataset(createABData(), ds -> {
         RunService.QueryResult queryResult = runService.queryDataSet(ds.id, jsonPath, array, schemaUri);
         assertTrue(queryResult.valid);
         return queryResult.value;
      });
   }

   private <T> T withExampleDataset(JsonNode data, Function<DataSet, T> testLogic) {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);
      try {
         Run run = new Run();
         tm.begin();
         try (CloseMe ignored = roleManager.withRoles(em, Arrays.asList(UPLOADER_ROLES))) {
            run.data = data;
            run.testid = 0;
            run.start = run.stop = Instant.now();
            run.owner = UPLOADER_ROLES[0];
            run.persistAndFlush();
         } finally {
            if (tm.getTransaction().getStatus() == Status.STATUS_ACTIVE) {
               tm.commit();
            } else {
               tm.rollback();
               fail();
            }
         }
         DataSet ds = dataSetQueue.poll(10, TimeUnit.SECONDS);
         assertNotNull(ds);
         T value = testLogic.apply(ds);
         tm.begin();
         Throwable error = null;
         try (CloseMe ignored = roleManager.withRoles(em, Collections.singletonList(Roles.HORREUM_SYSTEM))) {
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
      } catch (Exception e) {
         fail(e);
         return null;
      }
   }

   private ArrayNode createABData() {
      ArrayNode data = JsonNodeFactory.instance.arrayNode();
      ObjectNode a = JsonNodeFactory.instance.objectNode();
      ObjectNode b = JsonNodeFactory.instance.objectNode();
      a.put("$schema", "A");
      a.put("value", 24);
      b.put("$schema", "B");
      b.put("value", 42);
      data.add(a).add(b);
      return data;
   }

   @org.junit.jupiter.api.Test
   public void testDatasetLabelsSingle() {
      withExampleSchemas((schemas) -> {
         int labelA = addLabel(schemas[0], "value", null, new NamedJsonPath("value", "$.value", false));
         int labelB = addLabel(schemas[1], "value", "v => v + 1", new NamedJsonPath("value", "$.value", false));
         List<Label.Value> values = withLabelValues(createABData());
         assertEquals(2, values.size());
         assertEquals(24, values.stream().filter(v -> v.labelId == labelA).map(v -> v.value.numberValue()).findFirst().orElse(null));
         assertEquals(43, values.stream().filter(v -> v.labelId == labelB).map(v -> v.value.numberValue()).findFirst().orElse(null));

      }, "A", "B");
   }

   private ArrayNode createXYData() {
      ArrayNode data = JsonNodeFactory.instance.arrayNode();
      ObjectNode a = JsonNodeFactory.instance.objectNode();
      ObjectNode b = JsonNodeFactory.instance.objectNode();
      a.put("$schema", "X");
      a.put("a", 1);
      a.put("b", 2);
      b.put("$schema", "Y");
      ArrayNode array = JsonNodeFactory.instance.arrayNode();
      array.add(JsonNodeFactory.instance.objectNode().put("y", 3));
      array.add(JsonNodeFactory.instance.objectNode().put("y", 4));
      b.set("array", array);
      data.add(a).add(b);
      return data;
   }

   @org.junit.jupiter.api.Test
   public void testDatasetLabelsMulti() {
      withExampleSchemas((schemas) -> {
         int labelSum = addLabel(schemas[0], "Sum", "({ a, b }) => a + b",
               new NamedJsonPath("a", "$.a", false), new NamedJsonPath("b", "$.b", false));
         int labelSingle = addLabel(schemas[0], "Single", null,
               new NamedJsonPath("a", "$.a", false));
         int labelObject = addLabel(schemas[0], "Object", null,
               new NamedJsonPath("x", "$.a", false), new NamedJsonPath("y", "$.b", false));
         int labelArray = addLabel(schemas[1], "Array", null,
               new NamedJsonPath("array", "$.array[*].y", true));
         int labelReduce = addLabel(schemas[1], "Reduce", "array => array.reduce((a, b) => a + b)",
               new NamedJsonPath("array", "$.array[*].y", true));

         List<Label.Value> values = withLabelValues(createXYData());
         assertEquals(5, values.size());
         assertEquals(3, values.stream().filter(v -> v.labelId == labelSum).map(v -> v.value.numberValue()).findFirst().orElse(null));
         assertEquals(1, values.stream().filter(v -> v.labelId == labelSingle).map(v -> v.value.numberValue()).findFirst().orElse(null));
         assertEquals(JsonNodeFactory.instance.objectNode().put("x", 1).put("y", 2), values.stream().filter(v -> v.labelId == labelObject).map(v -> v.value).findFirst().orElse(null));
         assertEquals(JsonNodeFactory.instance.arrayNode().add(3).add(4), values.stream().filter(v -> v.labelId == labelArray).map(v -> v.value).findFirst().orElse(null));
         assertEquals(7, values.stream().filter(v -> v.labelId == labelReduce).map(v -> v.value.numberValue()).findFirst().orElse(null));

      }, "X", "Y");
   }

   @org.junit.jupiter.api.Test
   public void testDatasetLabelsNotFound() {
      withExampleSchemas((schemas) -> {
         int labelSingle = addLabel(schemas[0], "Single", null,
               new NamedJsonPath("value", "$.thisPathDoesNotExist", false));
         int labelSingleFunc = addLabel(schemas[0], "SingleFunc", "x => x === null",
               new NamedJsonPath("value", "$.thisPathDoesNotExist", false));
         int labelSingleArray = addLabel(schemas[0], "SingleArray", "x => Array.isArray(x) && x.length === 0",
               new NamedJsonPath("value", "$.thisPathDoesNotExist", true));
         int labelMulti = addLabel(schemas[0], "value", null, new NamedJsonPath("a", "$.a", false),
               new NamedJsonPath("value", "$.thisPathDoesNotExist", false));
         int labelMultiFunc = addLabel(schemas[0], "value", "({a, value}) => a === 1 && value === null",
               new NamedJsonPath("a", "$.a", false), new NamedJsonPath("value", "$.thisPathDoesNotExist", false));
         int labelMultiArray = addLabel(schemas[0], "value", "({a, value}) => a === 1 && Array.isArray(value) && value.length === 0",
               new NamedJsonPath("a", "$.a", false), new NamedJsonPath("value", "$.thisPathDoesNotExist", true));

         List<Label.Value> values = withLabelValues(createXYData());
         assertEquals(6, values.size());
         Label.Value singleValue = values.stream().filter(v -> v.labelId == labelSingle).findFirst().orElseThrow();
         assertNull(singleValue.value);
         BooleanNode trueNode = JsonNodeFactory.instance.booleanNode(true);
         assertEquals(trueNode, values.stream().filter(v -> v.labelId == labelSingleFunc).map(v -> v.value).findFirst().orElse(null));
         assertEquals(trueNode, values.stream().filter(v -> v.labelId == labelSingleArray).map(v -> v.value).findFirst().orElse(null));
         assertEquals(JsonNodeFactory.instance.objectNode().put("a", 1).putNull("value"), values.stream().filter(v -> v.labelId == labelMulti).map(v -> v.value).findFirst().orElse(null));
         assertEquals(trueNode, values.stream().filter(v -> v.labelId == labelMultiFunc).map(v -> v.value).findFirst().orElse(null));
         assertEquals(trueNode, values.stream().filter(v -> v.labelId == labelMultiArray).map(v -> v.value).findFirst().orElse(null));
      }, "X");
   }

   @org.junit.jupiter.api.Test
   public void testDatasetLabelChanged() {
      withExampleSchemas((schemas) -> {
         int labelA = addLabel(schemas[0], "value", null, new NamedJsonPath("value", "$.value", false));
         int labelB = addLabel(schemas[1], "value", "v => v + 1", new NamedJsonPath("value", "$.value", false));
         int labelC = addLabel(schemas[1], "value", null, new NamedJsonPath("value", "$.value", false));
         BlockingQueue<DataSet.LabelsUpdatedEvent> updateQueue = eventConsumerQueue(DataSet.LabelsUpdatedEvent.class, DataSet.EVENT_LABELS_UPDATED);
         withExampleDataset(createABData(), ds -> {
            try {
               waitForUpdate(updateQueue, ds);
               List<Label.Value> values = Label.Value.<Label.Value>find("dataset_id", ds.id).list();
               assertEquals(3, values.size());
               assertEquals(24, values.stream().filter(v -> v.labelId == labelA).map(v -> v.value.numberValue()).findFirst().orElse(null));
               assertEquals(43, values.stream().filter(v -> v.labelId == labelB).map(v -> v.value.numberValue()).findFirst().orElse(null));
               assertEquals(42, values.stream().filter(v -> v.labelId == labelC).map(v -> v.value.numberValue()).findFirst().orElse(null));
               em.clear();

               updateLabel(schemas[0], labelA, "value", null, new NamedJsonPath("value", "$.value", true));
               updateLabel(schemas[1], labelB, "value", "({ x, y }) => x + y", new NamedJsonPath("x", "$.value", false), new NamedJsonPath("y", "$.value", false));
               deleteLabel(schemas[1], labelC);
               waitForUpdate(updateQueue, ds);
               waitForUpdate(updateQueue, ds);
               // delete does not cause any update

               values = Label.Value.<Label.Value>find("dataset_id", ds.id).list();
               assertEquals(2, values.size());
               assertEquals(JsonNodeFactory.instance.arrayNode().add(24), values.stream().filter(v -> v.labelId == labelA).map(v -> v.value).findFirst().orElse(null));
               assertEquals(84, values.stream().filter(v -> v.labelId == labelB).map(v -> v.value.numberValue()).findFirst().orElse(null));
            } catch (InterruptedException e) {
               fail(e);
            }
            return null;
         });


      }, "A", "B");
   }

   private void waitForUpdate(BlockingQueue<DataSet.LabelsUpdatedEvent> updateQueue, DataSet ds) throws InterruptedException {
      DataSet.LabelsUpdatedEvent event = updateQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(event);
      assertEquals(ds.id, event.datasetId);
   }

   private List<Label.Value> withLabelValues(ArrayNode data) {
      BlockingQueue<DataSet.LabelsUpdatedEvent> updateQueue = eventConsumerQueue(DataSet.LabelsUpdatedEvent.class, DataSet.EVENT_LABELS_UPDATED);
      return withExampleDataset(data, ds -> {
         try {
            waitForUpdate(updateQueue, ds);
            return Label.Value.<Label.Value>find("dataset_id", ds.id).list();
         } catch (InterruptedException e) {
            fail(e);
            return null;
         }
      });
   }

   private void withExampleSchemas(Consumer<Schema[]> testLogic, String... schemas) {
      Schema[] instances = Arrays.stream(schemas).map(s -> createSchema(s, s)).toArray(Schema[]::new);
      try {
         testLogic.accept(instances);
      } finally {
         Arrays.stream(instances).forEach(this::deleteSchema);
      }
   }
}
