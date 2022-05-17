package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.DatasetService;
import io.hyperfoil.tools.horreum.api.QueryResult;
import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.entity.json.Extractor;
import io.hyperfoil.tools.horreum.entity.json.Label;
import io.hyperfoil.tools.horreum.entity.json.Schema;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.entity.json.View;
import io.hyperfoil.tools.horreum.entity.json.ViewComponent;
import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.test.NoGrafanaProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(NoGrafanaProfile.class)
public class DatasetServiceTest extends BaseServiceTest {
   @Inject
   DatasetService datasetService;


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
      AtomicReference<String> result = new AtomicReference<>();
      withExampleSchemas(schemas -> result.set(withExampleDataset(createTest(createExampleTest("dummy")), createABData(), ds -> {
         QueryResult queryResult = datasetService.queryDataSet(ds.id, jsonPath, array, schemaUri);
         assertTrue(queryResult.valid);
         return queryResult.value;
      })), "A", "B");
      return result.get();
   }

   @org.junit.jupiter.api.Test
   public void testDatasetLabelsSingle() {
      withExampleSchemas((schemas) -> {
         int labelA = addLabel(schemas[0], "value", null, new Extractor("value", "$.value", false));
         int labelB = addLabel(schemas[1], "value", "v => v + 1", new Extractor("value", "$.value", false));
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
               new Extractor("a", "$.a", false), new Extractor("b", "$.b", false));
         int labelSingle = addLabel(schemas[0], "Single", null,
               new Extractor("a", "$.a", false));
         int labelObject = addLabel(schemas[0], "Object", null,
               new Extractor("x", "$.a", false), new Extractor("y", "$.b", false));
         int labelArray = addLabel(schemas[1], "Array", null,
               new Extractor("array", "$.array[*].y", true));
         int labelReduce = addLabel(schemas[1], "Reduce", "array => array.reduce((a, b) => a + b)",
               new Extractor("array", "$.array[*].y", true));
         int labelNoExtractor = addLabel(schemas[0], "Nothing", "empty => 42");

         List<Label.Value> values = withLabelValues(createXYData());
         assertEquals(6, values.size());
         assertEquals(3, values.stream().filter(v -> v.labelId == labelSum).map(v -> v.value.numberValue()).findFirst().orElse(null));
         assertEquals(1, values.stream().filter(v -> v.labelId == labelSingle).map(v -> v.value.numberValue()).findFirst().orElse(null));
         assertEquals(JsonNodeFactory.instance.objectNode().put("x", 1).put("y", 2), values.stream().filter(v -> v.labelId == labelObject).map(v -> v.value).findFirst().orElse(null));
         assertEquals(JsonNodeFactory.instance.arrayNode().add(3).add(4), values.stream().filter(v -> v.labelId == labelArray).map(v -> v.value).findFirst().orElse(null));
         assertEquals(7, values.stream().filter(v -> v.labelId == labelReduce).map(v -> v.value.numberValue()).findFirst().orElse(null));
         assertEquals(42, values.stream().filter(v -> v.labelId == labelNoExtractor).map(v -> v.value.numberValue()).findFirst().orElse(null));

      }, "X", "Y");
   }

   @org.junit.jupiter.api.Test
   public void testDatasetLabelsNotFound() {
      withExampleSchemas((schemas) -> {
         int labelSingle = addLabel(schemas[0], "Single", null,
               new Extractor("value", "$.thisPathDoesNotExist", false));
         int labelSingleFunc = addLabel(schemas[0], "SingleFunc", "x => x === null",
               new Extractor("value", "$.thisPathDoesNotExist", false));
         int labelSingleArray = addLabel(schemas[0], "SingleArray", "x => Array.isArray(x) && x.length === 0",
               new Extractor("value", "$.thisPathDoesNotExist", true));
         int labelMulti = addLabel(schemas[0], "Multi", null, new Extractor("a", "$.a", false),
               new Extractor("value", "$.thisPathDoesNotExist", false));
         int labelMultiFunc = addLabel(schemas[0], "MultiFunc", "({a, value}) => a === 1 && value === null",
               new Extractor("a", "$.a", false), new Extractor("value", "$.thisPathDoesNotExist", false));
         int labelMultiArray = addLabel(schemas[0], "MultiArray", "({a, value}) => a === 1 && Array.isArray(value) && value.length === 0",
               new Extractor("a", "$.a", false), new Extractor("value", "$.thisPathDoesNotExist", true));

         List<Label.Value> values = withLabelValues(createXYData());
         assertEquals(6, values.size());
         Label.Value singleValue = values.stream().filter(v -> v.labelId == labelSingle).findFirst().orElseThrow();
         assertEquals(JsonNodeFactory.instance.nullNode(), singleValue.value);
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
         int labelA = addLabel(schemas[0], "A", null, new Extractor("value", "$.value", false));
         int labelB = addLabel(schemas[1], "B", "v => v + 1", new Extractor("value", "$.value", false));
         int labelC = addLabel(schemas[1], "C", null, new Extractor("value", "$.value", false));
         BlockingQueue<DataSet.LabelsUpdatedEvent> updateQueue = eventConsumerQueue(DataSet.LabelsUpdatedEvent.class, DataSet.EVENT_LABELS_UPDATED);
         withExampleDataset(createTest(createExampleTest("dummy")), createABData(), ds -> {
            waitForUpdate(updateQueue, ds);
            List<Label.Value> values = Label.Value.<Label.Value>find("dataset_id", ds.id).list();
            assertEquals(3, values.size());
            assertEquals(24, values.stream().filter(v -> v.labelId == labelA).map(v -> v.value.numberValue()).findFirst().orElse(null));
            assertEquals(43, values.stream().filter(v -> v.labelId == labelB).map(v -> v.value.numberValue()).findFirst().orElse(null));
            assertEquals(42, values.stream().filter(v -> v.labelId == labelC).map(v -> v.value.numberValue()).findFirst().orElse(null));
            em.clear();

            updateLabel(schemas[0], labelA, "value", null, new Extractor("value", "$.value", true));
            updateLabel(schemas[1], labelB, "value", "({ x, y }) => x + y", new Extractor("x", "$.value", false), new Extractor("y", "$.value", false));
            deleteLabel(schemas[1], labelC);
            waitForUpdate(updateQueue, ds);
            waitForUpdate(updateQueue, ds);
            // delete does not cause any update

            values = Label.Value.<Label.Value>find("dataset_id", ds.id).list();
            assertEquals(2, values.size());
            assertEquals(JsonNodeFactory.instance.arrayNode().add(24), values.stream().filter(v -> v.labelId == labelA).map(v -> v.value).findFirst().orElse(null));
            assertEquals(84, values.stream().filter(v -> v.labelId == labelB).map(v -> v.value.numberValue()).findFirst().orElse(null));
            return null;
         });


      }, "A", "B");
   }

   private List<Label.Value> withLabelValues(ArrayNode data) {
      BlockingQueue<DataSet.LabelsUpdatedEvent> updateQueue = eventConsumerQueue(DataSet.LabelsUpdatedEvent.class, DataSet.EVENT_LABELS_UPDATED);
      return withExampleDataset(createTest(createExampleTest("dummy")), data, ds -> {
         waitForUpdate(updateQueue, ds);
         return Label.Value.<Label.Value>find("dataset_id", ds.id).list();
      });
   }

   @org.junit.jupiter.api.Test
   public void testSchemaAfterData() throws InterruptedException {
      Test test = createTest(createExampleTest("xxx"));
      BlockingQueue<DataSet.EventNew> dsQueue = eventConsumerQueue(DataSet.EventNew.class, DataSet.EVENT_NEW);
      BlockingQueue<DataSet.LabelsUpdatedEvent> labelQueue = eventConsumerQueue(DataSet.LabelsUpdatedEvent.class, DataSet.EVENT_LABELS_UPDATED);
      JsonNode data = JsonNodeFactory.instance.arrayNode()
            .add(JsonNodeFactory.instance.objectNode().put("$schema", "another"))
            .add(JsonNodeFactory.instance.objectNode().put("$schema", "foobar").put("value", 42));
      int runId = uploadRun(data, test.name);
      DataSet.EventNew firstEvent = dsQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(firstEvent);
      assertEquals(runId, firstEvent.dataset.run.id);
      assertEmptyArray(firstEvent.dataset.data);
      // this update is for no label values - there's no schema
      DataSet.LabelsUpdatedEvent firstUpdate = labelQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(firstUpdate);
      assertEquals(firstEvent.dataset.id, firstUpdate.datasetId);

      assertEquals(0, ((Number) em.createNativeQuery("SELECT count(*) FROM dataset_schemas").getSingleResult()).intValue());
      Schema schema = createSchema("Foobar", "foobar");

      DataSet.EventNew secondEvent = dsQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(secondEvent);
      assertEquals(runId, secondEvent.dataset.run.id);
      // empty again - we have schema but no labels defined
      DataSet.LabelsUpdatedEvent secondUpdate = labelQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(secondUpdate);
      assertEquals(secondEvent.dataset.id, secondUpdate.datasetId);

      @SuppressWarnings("unchecked") List<Object[]> ds =
            em.createNativeQuery("SELECT dataset_id, index FROM dataset_schemas").getResultList();
      assertEquals(1, ds.size());
      assertEquals(secondEvent.dataset.id, ds.get(0)[0]);
      assertEquals(0, ds.get(0)[1]);
      assertEquals(0, ((Number) em.createNativeQuery("SELECT count(*) FROM label_values").getSingleResult()).intValue());

      addLabel(schema, "value", null, new Extractor("value", "$.value", false));
      // not empty anymore
      DataSet.LabelsUpdatedEvent thirdUpdate = labelQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(thirdUpdate);
      assertEquals(secondEvent.dataset.id, thirdUpdate.datasetId);

      List<Label.Value> values = Label.Value.listAll();
      assertEquals(1, values.size());
      assertEquals(42, values.get(0).value.asInt());
   }

   @org.junit.jupiter.api.Test
   public void testDatasetView() {
      Test test = createTest(createExampleTest("dummy"));
      Util.withTx(tm, () -> {
         try (CloseMe ignored = roleManager.withRoles(em, Arrays.asList(TESTER_ROLES))) {
            View view = View.findById(test.defaultView.id);
            view.components.clear();
            ViewComponent vc1 = new ViewComponent();
            vc1.view = view;
            vc1.headerName = "X";
            vc1.labels = jsonArray("a");
            view.components.add(vc1);
            ViewComponent vc2 = new ViewComponent();
            vc2.view = view;
            vc2.headerName = "Y";
            vc2.headerOrder = 1;
            vc2.labels = jsonArray("a", "b");
            view.components.add(vc2);
            view.persistAndFlush();
         }
         return null;
      });
      withExampleSchemas((schemas) -> {
         Extractor valuePath = new Extractor("value", "$.value", false);
         int labelA = addLabel(schemas[0], "a", null, valuePath);
         int labelB = addLabel(schemas[1], "b", null, valuePath);
         // view update should happen in the same transaction as labels update so we can use the event
         BlockingQueue<DataSet.LabelsUpdatedEvent> updateQueue = eventConsumerQueue(DataSet.LabelsUpdatedEvent.class, DataSet.EVENT_LABELS_UPDATED);
         withExampleDataset(test, createABData(), ds -> {
            waitForUpdate(updateQueue, ds);
            JsonNode datasets = fetchDatasetsByTest(test.id);
            assertEquals(1, datasets.get("total").asInt());
            assertEquals(1, datasets.get("datasets").size());
            JsonNode dsJson = datasets.get("datasets").get(0);
            assertEquals(2, dsJson.get("schemas").size());
            JsonNode view = dsJson.get("view");
            assertEquals(2, view.size());
            JsonNode vc1 = StreamSupport.stream(view.spliterator(), false).filter(vc -> vc.size() == 1).findFirst().orElseThrow();
            assertEquals(24, vc1.get("a").asInt());
            JsonNode vc2 = StreamSupport.stream(view.spliterator(), false).filter(vc -> vc.size() == 2).findFirst().orElseThrow();
            assertEquals(24, vc2.get("a").asInt());
            assertEquals(42, vc2.get("b").asInt());

            String labelIds = (String) em.createNativeQuery("SELECT to_json(label_ids)::::text FROM dataset_view WHERE dataset_id = ?1")
                  .setParameter(1, ds.id).getSingleResult();
            Set<Integer> ids = new HashSet<>();
            StreamSupport.stream(Util.toJsonNode(labelIds).spliterator(), false).mapToInt(JsonNode::asInt).forEach(ids::add);
            assertEquals(2, ids.size());
            assertTrue(ids.contains(labelA));
            assertTrue(ids.contains(labelB));

            Util.withTx(tm, () -> {
               try (CloseMe ignored = roleManager.withRoles(em, Arrays.asList(TESTER_ROLES))) {
                  int vcs = em.createNativeQuery("UPDATE viewcomponent SET labels = '[\"a\",\"b\"]'").executeUpdate();
                  assertEquals(2, vcs);
               }
               return null;
            });

            JsonNode updated = fetchDatasetsByTest(test.id);
            JsonNode updatedView = updated.get("datasets").get(0).get("view");
            assertEquals(2, updatedView.size());
            assertTrue(StreamSupport.stream(updatedView.spliterator(), false).allMatch(vc -> vc.size() == 2), updated.toPrettyString());

            return null;
         });
      }, "A", "B");
   }

   private JsonNode fetchDatasetsByTest(int testId) {
      JsonNode datasets = Util.toJsonNode(jsonRequest().get("/api/dataset/list/" + testId).then().statusCode(200).extract().body().asString());
      assertNotNull(datasets);
      return datasets;
   }

   private void withExampleSchemas(Consumer<Schema[]> testLogic, String... schemas) {
      Schema[] instances = Arrays.stream(schemas).map(s -> createSchema(s, s)).toArray(Schema[]::new);
      try {
         testLogic.accept(instances);
      } finally {
         Arrays.stream(instances).forEach(this::deleteSchema);
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

   private void waitForUpdate(BlockingQueue<DataSet.LabelsUpdatedEvent> updateQueue, DataSet ds) {
      try {
         DataSet.LabelsUpdatedEvent event = updateQueue.poll(10, TimeUnit.SECONDS);
         assertNotNull(event);
         assertEquals(ds.id, event.datasetId);
      } catch (InterruptedException e) {
         fail(e);
      }
   }
}
