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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import javax.inject.Inject;
import javax.transaction.Status;

import org.junit.After;
import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.RunService;
import io.hyperfoil.tools.horreum.entity.json.Access;
import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.entity.json.Label;
import io.hyperfoil.tools.horreum.entity.json.NamedJsonPath;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Schema;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.entity.json.View;
import io.hyperfoil.tools.horreum.entity.json.ViewComponent;
import io.hyperfoil.tools.horreum.entity.json.Transformer;
import io.hyperfoil.tools.horreum.server.CloseMe;
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
   RunService runService;

   private static final int POLL_DURATION_SECONDS = /*11*/10;

   @org.junit.jupiter.api.Test
   public void testTransformationNoSchemaInData(TestInfo info) throws InterruptedException {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);
      NamedJsonPath path = new NamedJsonPath("foo", "$.value", false);
      Schema schema = createExampleSchema(info);

      Transformer transformer = createTransformerWithJsonPaths("acme", schema, "", path);
      Test exampleTest = createExampleTest(getTestName(info));
      Test test = createTest(exampleTest);
      addTransformer(test, transformer);
      uploadRun("{\"corporation\":\"acme\"}", test.name);

      DataSet event = dataSetQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(event);
      assertEmptyArray(event.data);
   }

   @org.junit.jupiter.api.Test
   public void testTransformationWithoutSchema(TestInfo info) throws InterruptedException {
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

   @org.junit.jupiter.api.Test
   public void testTransformationWithoutSchemaInUpload(TestInfo info) throws InterruptedException {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);
      Test test = createTest(createExampleTest(getTestName(info)));

      setTestVariables(test, "Value", "value");

      uploadRun( "{ \"foo\":\"bar\"}", test.name);

      DataSet event = dataSetQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(event);
      assertEmptyArray(event.data);
   }

   @org.junit.jupiter.api.Test
   public void testTransformationWithoutExtractorsAndBlankFunction(TestInfo info) throws InterruptedException {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);
      Schema schema = createExampleSchema(info);

      Transformer transformer = createTransformerWithJsonPaths("acme", schema, "");
      Test exampleTest = createExampleTest(getTestName(info));
      Test test = createTest(exampleTest);
      addTransformer(test, transformer);
      uploadRun(runWithValue(schema, 42.0d), test.name);

      DataSet event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
      assertNotNull(event);
      JsonNode node = event.data;
      assertTrue(node.isArray());
      assertEquals(1, node.size());
      assertEquals(1, node.get(0).size());
      assertTrue(node.get(0).hasNonNull("$schema"));
   }

   @org.junit.jupiter.api.Test
   public void testTransformationWithExtractorAndBlankFunction(TestInfo info) throws InterruptedException {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);
      Schema schema = createExampleSchema("AcneCorp", "AcneInc", "AcneRrUs", false);

      NamedJsonPath path = new NamedJsonPath("foo", "$.value", false);
      Transformer transformer = createTransformerWithJsonPaths("acme", schema, "", path); // blank function
      Test exampleTest = createExampleTest(getTestName(info));
      Test test = createTest(exampleTest);
      addTransformer(test, transformer);
      uploadRun(runWithValue(schema, 42.0d), test.name);

      DataSet event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
      assertNotNull(event);
      assertTrue(event.data.isArray());
      assertEquals(1, event.data.size());
      // the result of single extractor is 42, hence this needs to be wrapped into an object (using `value`) before adding schema
      assertEquals(42, event.data.path(0).path("value").intValue());
   }

   @org.junit.jupiter.api.Test
   public void testTransformationWithNestedSchema(TestInfo info) throws InterruptedException {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);
      Schema acmeSchema = createExampleSchema("AcmeCorp", "AcmeInc", "AcmeRrUs", false);
      Schema roadRunnerSchema = createExampleSchema("RoadRunnerCorp", "RoadRunnerInc", "RoadRunnerRrUs", false);

      NamedJsonPath acmePath = new NamedJsonPath("foo", "$.value", false);
      Transformer acmeTransformer = createTransformerWithJsonPaths("acme", acmeSchema, "value => ({ acme: value })", acmePath);
      NamedJsonPath roadRunnerPath = new NamedJsonPath("bah", "$.value", false);
      Transformer roadRunnerTransformer = createTransformerWithJsonPaths("roadrunner", roadRunnerSchema, "value => ({ outcome: value })", roadRunnerPath);

      Test exampleTest = createExampleTest(getTestName(info));
      Test test = createTest(exampleTest);
      addTransformer(test, acmeTransformer, roadRunnerTransformer);

      String data = runWithValue(42.0d, acmeSchema, roadRunnerSchema).toString();
      int runId = uploadRun(data, test.name);

      DataSet event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);

      assertNotNull(event);
      JsonNode node = event.data;
      assertTrue(node.isArray());
      assertEquals(2 , node.size());
      validate("42", node.path(0).path("acme"));
      validate("42", node.path(1).path("outcome"));
      Run run = Run.findById(runId);
      assertEquals(1, run.datasets.size());
   }

   @org.junit.jupiter.api.Test
   public void testTransformationSingleSchemaTestWithoutTransformer(TestInfo info) throws InterruptedException {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);
      Schema acmeSchema = createExampleSchema("AceCorp", "AceInc", "AceRrUs", false);

      Test exampleTest = createExampleTest(getTestName(info));
      Test test = createTest(exampleTest);

      uploadRun(runWithValue(42.0d, acmeSchema), test.name);

      DataSet event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);

      assertNotNull(event);
      JsonNode node = event.data;
      assertTrue(node.isArray());
      ObjectNode object = (ObjectNode)node.path(0);
      JsonNode schema = object.path("$schema");
      assertEquals("urn:AceInc:AceRrUs:1.0", schema.textValue());
      JsonNode value = object.path("value");
      assertEquals(42, value.intValue());
   }

   @org.junit.jupiter.api.Test
   public void testTransformationNestedSchemasWithoutTransformers(TestInfo info) throws InterruptedException {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);
      Schema schemaA = createExampleSchema("Ada", "Ada", "Ada", false);
      Schema schemaB = createExampleSchema("Bdb", "Bdb", "Bdb", false);
      Schema schemaC = createExampleSchema("Cdc", "Cdc", "Cdc", false);

      Test test = createTest(createExampleTest(getTestName(info)));

      ObjectNode data = runWithValue(schemaA, 1);
      data.set("nestedB", runWithValue(schemaB, 2));
      data.set("nestedC", runWithValue(schemaC, 3));
      uploadRun(data, test.name);

      DataSet dataset = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);

      assertNotNull(dataset);
      assertTrue(dataset.data.isArray());
      assertEquals(3, dataset.data.size());
      assertEquals(1, getBySchema(dataset, schemaA).path("value").intValue());
      assertEquals(2, getBySchema(dataset, schemaB).path("value").intValue());
      assertEquals(3, getBySchema(dataset, schemaC).path("value").intValue());

      assertNull(dataSetQueue.poll(50, TimeUnit.MILLISECONDS));
   }

   private JsonNode getBySchema(DataSet dataset, Schema schemaA) {
      return StreamSupport.stream(dataset.data.spliterator(), false)
            .filter(item -> schemaA.uri.equals(item.path("$schema").textValue()))
            .findFirst().orElseThrow(AssertionError::new);
   }

   @org.junit.jupiter.api.Test
   public void testTransformationUsingSameSchemaInBothLevelsTestWithoutTransformer(TestInfo info) throws InterruptedException {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);

      Schema appleSchema = createExampleSchema("AppleCorp", "AppleInc", "AppleRrUs", false);

      Test exampleTest = createExampleTest(getTestName(info));
      Test test = createTest(exampleTest);

      ObjectNode data = runWithValue(appleSchema, 42.0d);
      ObjectNode nested = runWithValue(appleSchema, 52.0d);
      data.set("field_" + appleSchema.name, nested);

      uploadRun(data, test.name);

      DataSet event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);

      assertNotNull(event);
      JsonNode node = event.data;
      assertTrue(node.isArray());
      assertEquals(2 , node.size());

      JsonNode first = node.path(0);
      assertEquals("urn:AppleInc:AppleRrUs:1.0", first.path("$schema").textValue());
      assertEquals(42, first.path("value").intValue());

      JsonNode second = node.path(1);
      assertEquals("urn:AppleInc:AppleRrUs:1.0", second.path("$schema").textValue());
      assertEquals(52, second.path("value").intValue());
   }

   @org.junit.jupiter.api.Test
    public void testTransformationUsingSingleSchemaTransformersProcessScalarPlusArray(TestInfo info) throws InterruptedException {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);

      Schema schema = createExampleSchema("ArrayCorp", "ArrayInc", "ArrayRrUs", false);
      NamedJsonPath arrayPath = new NamedJsonPath("mheep", "$.values", false);
      String arrayFunction = "mheep => { return mheep.map(x => ({ \"outcome\": x }))}";

      NamedJsonPath scalarPath = new NamedJsonPath("sheep", "$.value", false);
      String scalarFunction = "sheep => { return ({  \"outcome\": { sheep } }) }";

      Transformer arrayTransformer = createTransformerWithJsonPaths("arrayT", schema, arrayFunction, arrayPath);
      Transformer scalarTransformer = createTransformerWithJsonPaths("scalarT", schema, scalarFunction, scalarPath);

      Test exampleTest = createExampleTest(getTestName(info));
      Test test = createTest(exampleTest);
      addTransformer(test, arrayTransformer, scalarTransformer);

      ObjectNode data = runWithValue(42.0d, schema);

      uploadRun(data,test.name);

      DataSet first = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
      DataSet second = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
      DataSet third = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);

      assertNotNull(first);
      assertTrue(first.data.isArray());
      String target = postFunctionSchemaUri(schema);
      validateScalarArray(first, target);
      validateScalarArray(second, target);
      validateScalarArray(third, target);
   }

   @org.junit.jupiter.api.Test
   public void testTransformationChoosingSchema(TestInfo info) throws InterruptedException {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);

      Schema schemaA = createExampleSchema("Aba", "Aba", "Aba", false);
      NamedJsonPath path = new NamedJsonPath("value", "$.value", false);
      Transformer transformerA = createTransformerWithJsonPaths("A", schemaA, "value => ({\"by\": \"A\"})", path);

      Schema schemaB = createExampleSchema("Bcb", "Bcb", "Bcb", false);
      Transformer transformerB = createTransformerWithJsonPaths("B", schemaB, "value => ({\"by\": \"B\"})");

      Test test = createTest(createExampleTest(getTestName(info)));
      addTransformer(test, transformerA, transformerB);

      uploadRun(runWithValue(schemaB, 42), test.name);
      DataSet dataset = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
      assertNotNull(dataset);
      assertTrue(dataset.data.isArray());
      assertEquals(1, dataset.data.size());
      assertEquals("B", dataset.data.get(0).path("by").asText());

      assertNull(dataSetQueue.poll(50, TimeUnit.MILLISECONDS));
   }

   @org.junit.jupiter.api.Test
   public void testTransformationWithoutMatchFirstLevel(TestInfo info) throws InterruptedException {
      Schema schema = createExampleSchema("Aca", "Aca", "Aca", false);
      testTransformationWithoutMatch(info, schema, runWithValue(schema, 42));
   }

   @org.junit.jupiter.api.Test
   public void testTransformationWithoutMatchSecondLevel(TestInfo info) throws InterruptedException {
      Schema schema = createExampleSchema("B", "B", "B", false);
      testTransformationWithoutMatch(info, schema, JsonNodeFactory.instance.objectNode().set("nested", runWithValue(schema, 42)));
   }

   @org.junit.jupiter.api.Test
   public void testSchemaTransformerWithExtractorProducingNullValue(TestInfo info) throws InterruptedException {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);

      Schema schema = createExampleSchema("DDDD", "DDDDInc", "DDDDRrUs", true);
      NamedJsonPath scalarPath = new NamedJsonPath("sheep", "$.duff", false);
      Transformer scalarTransformer = createTransformerWithJsonPaths("tranProcessNullExtractorValue", schema, "sheep => ({ outcome: { sheep }})", scalarPath);

      Test exampleTest = createExampleTest(getTestName(info));
      Test test = createTest(exampleTest);
      addTransformer(test, scalarTransformer);

      ObjectNode data = runWithValue(42.0d, schema);

      uploadRun(data,test.name);

      DataSet dataSet = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
      JsonNode eventData = dataSet.data;
      assertTrue(eventData.isArray());
      assertEquals(1, eventData.size());
      JsonNode sheep = eventData.path(0).path("outcome").path("sheep");
      assertTrue(sheep.isNull());
   }

   private void testTransformationWithoutMatch(TestInfo info, Schema schema, ObjectNode data) throws InterruptedException {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);

      NamedJsonPath firstMatch = new NamedJsonPath("foo", "$.foo", false);
      NamedJsonPath allMatches = new NamedJsonPath("bar", "$.bar[*].x", false);
      allMatches.array = true;
      NamedJsonPath value = new NamedJsonPath("value", "$.value", false);
      NamedJsonPath values = new NamedJsonPath("values", "$.values[*]", false);
      values.array = true;

      Transformer transformerNoFunc = createTransformerWithJsonPaths("noFunc", schema, null, firstMatch, allMatches);
      Transformer transformerFunc = createTransformerWithJsonPaths("func", schema, "({foo, bar}) => ({ foo, bar })", firstMatch, allMatches);
      Transformer transformerCombined = createTransformerWithJsonPaths("combined", schema, null, firstMatch, allMatches, value, values);

      Test test = createTest(createExampleTest(getTestName(info)));
      addTransformer(test, transformerNoFunc, transformerFunc, transformerCombined);
      uploadRun(data, test.name);
      DataSet dataset = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);

      assertNotNull(dataset);
      assertTrue(dataset.data.isArray());
      assertEquals(3, dataset.data.size());
      dataset.data.forEach(item -> {
         assertTrue(item.path("foo").isNull());
         assertEmptyArray(item.path("bar"));
      });

      JsonNode combined = dataset.data.get(2);
      assertEquals(42, combined.path("value").intValue());
      assertTrue(combined.path("values").isArray());
      assertEquals(3, combined.path("values").size());
   }

   private void validate (String expected, JsonNode node) {
      assertNotNull(node);
      assertFalse(node.isMissingNode());
      assertEquals(expected, node.asText());
   }

   private ObjectNode runWithValue(Schema schema, double value) {
      ObjectNode runJson = JsonNodeFactory.instance.objectNode();
      runJson.put("$schema", schema.uri);
      runJson.put("value", value);
      ArrayNode values = JsonNodeFactory.instance.arrayNode();
      values.add(++value);
      values.add(++value);
      values.add(++value);
      runJson.set("values", values);
      return runJson;
   }

   private ObjectNode runWithValue( double value, Schema... schemas) {
      ObjectNode root = null;
      for ( Schema s : schemas) {
         ObjectNode n = runWithValue (s, value);
         if (root == null ) {
            root = n;
         } else {
            root.set("field_"+s.name, n);
         }
      }
      return root;
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
      AtomicReference<String> result = new AtomicReference<>();
      withExampleSchemas(schemas -> result.set(withExampleDataset(createTest(createExampleTest("dummy")), createABData(), ds -> {
         RunService.QueryResult queryResult = runService.queryDataSet(ds.id, jsonPath, array, schemaUri);
         assertTrue(queryResult.valid);
         return queryResult.value;
      })), "A", "B");
      return result.get();
   }

   private <T> T withExampleDataset(Test test, JsonNode data, Function<DataSet, T> testLogic) {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);
      try {
         Run run = new Run();
         tm.begin();
         try (CloseMe ignored = roleManager.withRoles(em, Arrays.asList(UPLOADER_ROLES))) {
            run.data = data;
            run.testid = test.id;
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
         withExampleDataset(createTest(createExampleTest("dummy")), createABData(), ds -> {
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
      return withExampleDataset(createTest(createExampleTest("dummy")), data, ds -> {
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
      withExampleDataset(createTest(createExampleTest("dummy")), JsonNodeFactory.instance.objectNode(), ds -> {
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
         NamedJsonPath valuePath = new NamedJsonPath("value", "$.value", false);
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
      DataSet firstDataset = dsQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(firstDataset);
      assertEquals(runId, firstDataset.run.id);
      assertEmptyArray(firstDataset.data);
      // this update is for no label values - there's no schema
      DataSet.LabelsUpdatedEvent firstUpdate = labelQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(firstUpdate);
      assertEquals(firstDataset.id, firstUpdate.datasetId);

      assertEquals(0, ((Number) em.createNativeQuery("SELECT count(*) FROM dataset_schemas").getSingleResult()).intValue());
      Schema schema = createSchema("Foobar", "foobar");

      DataSet secondDataset = dsQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(secondDataset);
      assertEquals(runId, secondDataset.run.id);
      // empty again - we have schema but no labels defined
      DataSet.LabelsUpdatedEvent secondUpdate = labelQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(secondUpdate);
      assertEquals(secondDataset.id, secondUpdate.datasetId);

      @SuppressWarnings("unchecked") List<Object[]> ds =
            em.createNativeQuery("SELECT dataset_id, index FROM dataset_schemas").getResultList();
      assertEquals(1, ds.size());
      assertEquals(secondDataset.id, ds.get(0)[0]);
      assertEquals(0, ds.get(0)[1]);
      assertEquals(0, ((Number) em.createNativeQuery("SELECT count(*) FROM label_values").getSingleResult()).intValue());

      addLabel(schema, "value", null, new NamedJsonPath("value", "$.value", false));
      // not empty anymore
      DataSet.LabelsUpdatedEvent thirdUpdate = labelQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(thirdUpdate);
      assertEquals(secondDataset.id, thirdUpdate.datasetId);

      List<Label.Value> values = Label.Value.listAll();
      assertEquals(1, values.size());
      assertEquals(42, values.get(0).value.asInt());
   }

   private static void assertEmptyArray(JsonNode node) {
      assertNotNull(node);
      assertTrue(node.isArray());
      assertTrue(node.isEmpty());
   }

   private Transformer createTransformerWithJsonPaths(String name, Schema schema, String function, NamedJsonPath... paths) {
      Transformer transformer = new Transformer();
      transformer.name = name;
      transformer.extractors = new ArrayList<>();
      for (NamedJsonPath path : paths) {
         if (path != null) {
            transformer.extractors.add(path);
         }
      }
      transformer.owner = TESTER_ROLES[0];
      transformer.access = Access.PUBLIC;
      transformer.schema = schema;
      transformer.function = function;
      transformer.targetSchemaUri = postFunctionSchemaUri(schema);
      Integer id = jsonRequest().body(transformer).post("/api/schema/"+schema.id+"/transformers").then().statusCode(200).extract().as(Integer.class);
      transformer.id = id;
      return transformer;
   }

   private void addTransformer(Test test, Transformer... transformers){
      List<Integer> ids = new ArrayList<>();
      assertNotNull(test.id);
      for (Transformer t : transformers) {
         ids.add(t.id);
      }
      jsonRequest().body(ids).post("/api/test/" + test.id + "/transformers").then().assertThat().statusCode(204);
   }

   private void validateScalarArray(DataSet ds, String expectedTarget) {
      JsonNode n = ds.data;
      int outcome = n.path(0).findValue("outcome").asInt();
      assertTrue(outcome == 43 || outcome == 44 || outcome == 45 );
      int value = n.path(1).path("outcome").path("sheep").asInt();
      assertEquals(42, value);
      String scalarTarget = n.path(0).path("$schema").textValue();
      assertEquals(expectedTarget, scalarTarget);
      String arrayTarget = n.path(1).path("$schema").textValue();
      assertEquals(expectedTarget, arrayTarget);
   }

   private String postFunctionSchemaUri(Schema s) {
      return "uri:" + s.name + "-post-function";
   }

   @After
   public void drain() {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);
      dataSetQueue.drainTo(new ArrayList<>());
   }
}
