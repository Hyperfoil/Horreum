package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import org.junit.After;
import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.entity.json.Access;
import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.entity.json.Extractor;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Schema;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.entity.json.Transformer;
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
   private static final int POLL_DURATION_SECONDS = 10;

   @org.junit.jupiter.api.Test
   public void testTransformationNoSchemaInData(TestInfo info) throws InterruptedException {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);
      Extractor path = new Extractor("foo", "$.value", false);
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

      Extractor path = new Extractor("foo", "$.value", false);
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

      Extractor acmePath = new Extractor("foo", "$.value", false);
      Transformer acmeTransformer = createTransformerWithJsonPaths("acme", acmeSchema, "value => ({ acme: value })", acmePath);
      Extractor roadRunnerPath = new Extractor("bah", "$.value", false);
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
      Extractor arrayPath = new Extractor("mheep", "$.values", false);
      String arrayFunction = "mheep => { return mheep.map(x => ({ \"outcome\": x }))}";

      Extractor scalarPath = new Extractor("sheep", "$.value", false);
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
      Extractor path = new Extractor("value", "$.value", false);
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
      Extractor scalarPath = new Extractor("sheep", "$.duff", false);
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

      Extractor firstMatch = new Extractor("foo", "$.foo", false);
      Extractor allMatches = new Extractor("bar", "$.bar[*].x", false);
      allMatches.array = true;
      Extractor value = new Extractor("value", "$.value", false);
      Extractor values = new Extractor("values", "$.values[*]", false);
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

   private void validate(String expected, JsonNode node) {
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

   private Transformer createTransformerWithJsonPaths(String name, Schema schema, String function, Extractor... paths) {
      Transformer transformer = new Transformer();
      transformer.name = name;
      transformer.extractors = new ArrayList<>();
      for (Extractor path : paths) {
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
