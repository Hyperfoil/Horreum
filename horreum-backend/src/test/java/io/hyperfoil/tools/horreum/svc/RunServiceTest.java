package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import javax.inject.Inject;
import javax.transaction.Status;

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
public class RunServiceTest extends BaseServiceTest {

   @Inject
   RunService runService;

   @org.junit.jupiter.api.Test
   public void testDataSetCreated(TestInfo info) throws InterruptedException {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);
      Test test = createTest(createExampleTest(getTestName(info)));
      Schema schema = createExampleSchema(info);

      int runId = uploadRun(runWithValue(schema, 42).toString(), test.name);

      assertNewDataset(dataSetQueue, runId);
      em.clear();

      BlockingQueue<Integer> trashedQueue = trashRun(runId);

      Run run = Run.findById(runId);
      assertNotNull(run);
      assertTrue(run.trashed);
      assertEquals(0, DataSet.count());

      em.clear();

      // reinstate the run
      jsonRequest().post("/api/run/" + runId + "/trash?isTrashed=false").then().statusCode(204);
      assertNull(trashedQueue.poll(50, TimeUnit.MILLISECONDS));
      run = Run.findById(runId);
      assertFalse(run.trashed);
      assertNewDataset(dataSetQueue, runId);
   }

   private void assertNewDataset(BlockingQueue<DataSet> dataSetQueue, int runId) throws InterruptedException {
      DataSet event = dataSetQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(event);
      assertNotNull(event.id);
      assertEquals(runId, event.run.id);
      DataSet ds = DataSet.findById(event.id);
      assertNotNull(ds);
      assertEquals(runId, ds.run.id);
   }

   private ObjectNode runWithValue(Schema schema, double value) {
      ObjectNode runJson = JsonNodeFactory.instance.objectNode();
      runJson.put("$schema", schema.uri);
      runJson.put("value", value);
      return runJson;
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
            DataSet oldDs = DataSet.findById(ds.id);
            if (oldDs != null) {
               oldDs.delete();
            }
            DataSet.delete("runid", run.id);
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
         int labelMulti = addLabel(schemas[0], "Multi", null, new NamedJsonPath("a", "$.a", false),
               new NamedJsonPath("value", "$.thisPathDoesNotExist", false));
         int labelMultiFunc = addLabel(schemas[0], "MultiFunc", "({a, value}) => a === 1 && value === null",
               new NamedJsonPath("a", "$.a", false), new NamedJsonPath("value", "$.thisPathDoesNotExist", false));
         int labelMultiArray = addLabel(schemas[0], "MultiArray", "({a, value}) => a === 1 && Array.isArray(value) && value.length === 0",
               new NamedJsonPath("a", "$.a", false), new NamedJsonPath("value", "$.thisPathDoesNotExist", true));

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
         int labelA = addLabel(schemas[0], "A", null, new NamedJsonPath("value", "$.value", false));
         int labelB = addLabel(schemas[1], "B", "v => v + 1", new NamedJsonPath("value", "$.value", false));
         int labelC = addLabel(schemas[1], "C", null, new NamedJsonPath("value", "$.value", false));
         BlockingQueue<DataSet.LabelsUpdatedEvent> updateQueue = eventConsumerQueue(DataSet.LabelsUpdatedEvent.class, DataSet.EVENT_LABELS_UPDATED);
         withExampleDataset(createABData(), ds -> {
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
            return null;
         });


      }, "A", "B");
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

   private List<Label.Value> withLabelValues(ArrayNode data) {
      BlockingQueue<DataSet.LabelsUpdatedEvent> updateQueue = eventConsumerQueue(DataSet.LabelsUpdatedEvent.class, DataSet.EVENT_LABELS_UPDATED);
      return withExampleDataset(data, ds -> {
         waitForUpdate(updateQueue, ds);
         return Label.Value.<Label.Value>find("dataset_id", ds.id).list();
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

   @org.junit.jupiter.api.Test
   public void testRecalculateDatasets() {
      withExampleDataset(JsonNodeFactory.instance.objectNode(), ds -> {
         Util.withTx(tm, () -> {
            try (CloseMe ignored = roleManager.withRoles(em, Collections.singletonList(Roles.HORREUM_SYSTEM))) {
               DataSet dbDs = DataSet.findById(ds.id);
               assertNotNull(dbDs);
               dbDs.delete();
               em.flush();
               em.clear();
            }
            return null;
         });
         List<Integer> dsIds1 = recalculateDataset(ds.run.id);
         assertEquals(1, dsIds1.size());
         try (CloseMe ignored = roleManager.withRoles(em, Collections.singletonList(Roles.HORREUM_SYSTEM))) {
            List<DataSet> dataSets = DataSet.find("runid", ds.run.id).list();
            assertEquals(1, dataSets.size());
            assertEquals(dsIds1.get(0), dataSets.get(0).id);
            em.clear();
         }
         List<Integer> dsIds2 = recalculateDataset(ds.run.id);
         try (CloseMe ignored = roleManager.withRoles(em, Collections.singletonList(Roles.HORREUM_SYSTEM))) {
            List<DataSet> dataSets = DataSet.find("runid", ds.run.id).list();
            assertEquals(1, dataSets.size());
            assertEquals(dsIds2.get(0), dataSets.get(0).id);
         }
         return null;
      });
   }

   protected List<Integer> recalculateDataset(int runId) {
      ArrayNode json = jsonRequest().post("/api/run/" + runId + "/recalculate").then().statusCode(200).extract().body().as(ArrayNode.class);
      ArrayList<Integer> list = new ArrayList<>(json.size());
      json.forEach(item -> list.add(item.asInt()));
      return list;
   }

   @org.junit.jupiter.api.Test
   public void testDatasetView() {
      Util.withTx(tm, () -> {
         try (CloseMe ignored = roleManager.withRoles(em, Arrays.asList(TESTER_ROLES))) {
            // we insert test directly to let it have ID=0, for simplicity
            em.createNativeQuery("INSERT INTO test(id, name, owner, access) VALUES (0, 'foo', ?1, 0)")
                  .setParameter(1, TESTER_ROLES[0]).executeUpdate();
            View view = new View();
            view.test = em.getReference(Test.class, 0);
            view.name = "default";
            view.components = new ArrayList<>();
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
            em.createNativeQuery("UPDATE test SET defaultview_id = ?1 WHERE id = 0").setParameter(1, view.id).executeUpdate();
            em.flush();
         }
         return null;
      });
      withExampleSchemas((schemas) -> {
         NamedJsonPath valuePath = new NamedJsonPath("value", "$.value", false);
         int labelA = addLabel(schemas[0], "a", null, valuePath);
         int labelB = addLabel(schemas[1], "b", null, valuePath);
         // view update should happen in the same transaction as labels update so we can use the event
         BlockingQueue<DataSet.LabelsUpdatedEvent> updateQueue = eventConsumerQueue(DataSet.LabelsUpdatedEvent.class, DataSet.EVENT_LABELS_UPDATED);
         withExampleDataset(createABData(), ds -> {
            waitForUpdate(updateQueue, ds);
            JsonNode datasets = fetchDatasetsByTest(0);
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

            String labelIds = (String) em.createNativeQuery("SELECT to_json(label_ids)::::text FROM dataset_view WHERE dataset_id = ?1").setParameter(1, ds.id).getSingleResult();
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

            JsonNode updated = fetchDatasetsByTest(0);
            JsonNode updatedView = updated.get("datasets").get(0).get("view");
            assertEquals(2, updatedView.size());
            assertTrue(StreamSupport.stream(updatedView.spliterator(), false).allMatch(vc -> vc.size() == 2), updated.toPrettyString());

            return null;
         });
      }, "A", "B");
   }

   private JsonNode fetchDatasetsByTest(int testId) {
      JsonNode datasets = Util.toJsonNode(jsonRequest().get("/api/run/dataset/list/" + testId).then().statusCode(200).extract().body().asString());
      assertNotNull(datasets);
      return datasets;
   }

   @org.junit.jupiter.api.Test
   public void testSchemaAfterData() throws InterruptedException {
      Test test = createTest(createExampleTest("xxx"));
      BlockingQueue<DataSet> dsQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);
      BlockingQueue<DataSet.LabelsUpdatedEvent> labelQueue = eventConsumerQueue(DataSet.LabelsUpdatedEvent.class, DataSet.EVENT_LABELS_UPDATED);
      JsonNode data = JsonNodeFactory.instance.arrayNode()
            .add(JsonNodeFactory.instance.objectNode().put("$schema", "another"))
            .add(JsonNodeFactory.instance.objectNode().put("$schema", "foobar").put("value", 42));
      int runId = uploadRun(data, test.name);
      DataSet eventDs = dsQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(eventDs);
      assertEquals(runId, eventDs.run.id);
      int datasetId = eventDs.id;
      DataSet.LabelsUpdatedEvent eventLabels = labelQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(eventLabels);
      assertEquals(datasetId, eventLabels.datasetId);

      assertEquals(0, ((Number) em.createNativeQuery("SELECT count(*) FROM dataset_schemas").getSingleResult()).intValue());
      Schema schema = createSchema("Foobar", "foobar");
      @SuppressWarnings("unchecked") List<Object[]> ds =
            em.createNativeQuery("SELECT dataset_id, index FROM dataset_schemas").getResultList();
      assertEquals(1, ds.size());
      assertEquals(datasetId, ds.get(0)[0]);
      assertEquals(1, ds.get(0)[1]);
      assertEquals(0, ((Number) em.createNativeQuery("SELECT count(*) FROM label_values").getSingleResult()).intValue());

      addLabel(schema, "value", null, new NamedJsonPath("value", "$.value", false));
      assertNotNull(labelQueue.poll(10, TimeUnit.SECONDS));
      List<Label.Value> values = Label.Value.listAll();
      assertEquals(1, values.size());
      assertEquals(42, values.get(0).value.asInt());
   }
}
