package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hyperfoil.tools.horreum.api.alerting.Variable;
import io.hyperfoil.tools.horreum.api.services.RunService;
import jakarta.ws.rs.core.HttpHeaders;

import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.api.data.*;
import io.hyperfoil.tools.horreum.api.data.Extractor;
import io.hyperfoil.tools.horreum.entity.data.*;
import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.hyperfoil.tools.horreum.test.TestUtil;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class RunServiceTest extends BaseServiceTest {
   private static final int POLL_DURATION_SECONDS = 10;

   @org.junit.jupiter.api.Test
   public void testTransformationNoSchemaInData(TestInfo info) throws InterruptedException {
      Test exampleTest = createExampleTest(getTestName(info));
      Test test = createTest(exampleTest);

      BlockingQueue<DataSet.EventNew> dataSetQueue = eventConsumerQueue(DataSet.EventNew.class, DataSetDAO.EVENT_NEW, e -> e.dataset.testid.equals(test.id));
      Extractor path = new Extractor("foo", "$.value", false);
      Schema schema = createExampleSchema(info);

      Transformer transformer = createTransformer("acme", schema, "", path);
      addTransformer(test, transformer);
      uploadRun("{\"corporation\":\"acme\"}", test.name);

      DataSet.EventNew event = dataSetQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(event);
      TestUtil.assertEmptyArray(event.dataset.data);
   }

   @org.junit.jupiter.api.Test
   public void testTransformationWithoutSchema(TestInfo info) throws InterruptedException {
      Test test = createTest(createExampleTest(getTestName(info)));
      BlockingQueue<DataSet.EventNew> dataSetQueue = eventConsumerQueue(DataSet.EventNew.class, DataSetDAO.EVENT_NEW, e -> e.dataset.testid.equals(test.id));

      Schema schema = createExampleSchema(info);

      int runId = uploadRun(runWithValue(42, schema).toString(), test.name);

      assertNewDataset(dataSetQueue, runId);
      em.clear();

      BlockingQueue<Integer> trashedQueue = trashRun(runId);

      RunDAO run = RunDAO.findById(runId);
      assertNotNull(run);
      assertTrue(run.trashed);
      assertEquals(0, DataSetDAO.count());

      em.clear();

      // reinstate the run
      jsonRequest().post("/api/run/" + runId + "/trash?isTrashed=false").then().statusCode(204);
      assertNull(trashedQueue.poll(50, TimeUnit.MILLISECONDS));
      run = RunDAO.findById(runId);
      assertFalse(run.trashed);
      assertNewDataset(dataSetQueue, runId);
   }

   private void assertNewDataset(BlockingQueue<DataSet.EventNew> dataSetQueue, int runId) throws InterruptedException {
      DataSet.EventNew event = dataSetQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(event);
      assertNotNull(event.dataset);
      assertNotNull(event.dataset.id);
      assertEquals(runId, event.dataset.runId);
      DataSetDAO ds = DataSetDAO.findById(event.dataset.id);
      assertNotNull(ds);
      assertEquals(runId, ds.run.id);
   }

   @org.junit.jupiter.api.Test
   public void testTransformationWithoutSchemaInUpload(TestInfo info) throws InterruptedException {
      Test test = createTest(createExampleTest(getTestName(info)));
      BlockingQueue<DataSet.EventNew> dataSetQueue = eventConsumerQueue(DataSet.EventNew.class, DataSetDAO.EVENT_NEW, e -> e.dataset.testid.equals(test.id));

      setTestVariables(test, "Value", "value");

      uploadRun( "{ \"foo\":\"bar\"}", test.name);

      DataSet.EventNew event = dataSetQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(event);
      TestUtil.assertEmptyArray(event.dataset.data);
   }

   @org.junit.jupiter.api.Test
   public void testTransformationWithoutExtractorsAndBlankFunction(TestInfo info) throws InterruptedException {
      Test exampleTest = createExampleTest(getTestName(info));
      Test test = createTest(exampleTest);

      BlockingQueue<DataSet.EventNew> dataSetQueue = eventConsumerQueue(DataSet.EventNew.class, DataSetDAO.EVENT_NEW, e -> e.dataset.testid.equals(test.id));
      Schema schema = createExampleSchema(info);

      Transformer transformer = createTransformer("acme", schema, "");
      addTransformer(test, transformer);
      uploadRun(runWithValue(42.0d, schema), test.name);

      DataSet.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
      assertNotNull(event);
      JsonNode node = event.dataset.data;
      assertTrue(node.isArray());
      assertEquals(1, node.size());
      assertEquals(1, node.get(0).size());
      assertTrue(node.get(0).hasNonNull("$schema"));
   }

   @org.junit.jupiter.api.Test
   public void testTransformationWithExtractorAndBlankFunction(TestInfo info) throws InterruptedException {
      Test exampleTest = createExampleTest(getTestName(info));
      Test test = createTest(exampleTest);

      BlockingQueue<DataSet.EventNew> dataSetQueue = eventConsumerQueue(DataSet.EventNew.class, DataSetDAO.EVENT_NEW, e -> e.dataset.testid.equals(test.id));
      Schema schema = createExampleSchema("AcneCorp", "AcneInc", "AcneRrUs", false);

      Extractor path = new Extractor("foo", "$.value", false);
      Transformer transformer = createTransformer("acme", schema, "", path); // blank function
      addTransformer(test, transformer);
      uploadRun(runWithValue(42.0d, schema), test.name);

      DataSet.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
      assertNotNull(event);
      assertTrue(event.dataset.data.isArray());
      assertEquals(1, event.dataset.data.size());
      // the result of single extractor is 42, hence this needs to be wrapped into an object (using `value`) before adding schema
      assertEquals(42, event.dataset.data.path(0).path("value").intValue());
   }

   @org.junit.jupiter.api.Test
   public void testTransformationWithNestedSchema(TestInfo info) throws InterruptedException {
      Schema acmeSchema = createExampleSchema("AcmeCorp", "AcmeInc", "AcmeRrUs", false);
      Schema roadRunnerSchema = createExampleSchema("RoadRunnerCorp", "RoadRunnerInc", "RoadRunnerRrUs", false);

      Extractor acmePath = new Extractor("foo", "$.value", false);
      Transformer acmeTransformer = createTransformer("acme", acmeSchema, "value => ({ acme: value })", acmePath);
      Extractor roadRunnerPath = new Extractor("bah", "$.value", false);
      Transformer roadRunnerTransformer = createTransformer("roadrunner", roadRunnerSchema, "value => ({ outcome: value })", roadRunnerPath);

      Test exampleTest = createExampleTest(getTestName(info));
      Test test = createTest(exampleTest);
      addTransformer(test, acmeTransformer, roadRunnerTransformer);

      BlockingQueue<DataSet.EventNew> dataSetQueue = eventConsumerQueue(DataSet.EventNew.class, DataSetDAO.EVENT_NEW, e -> e.dataset.testid.equals(test.id));

      String data = runWithValue(42.0d, acmeSchema, roadRunnerSchema).toString();
      int runId = uploadRun(data, test.name);

      DataSet.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);

      assertNotNull(event);
      JsonNode node = event.dataset.data;
      assertTrue(node.isArray());
      assertEquals(2 , node.size());
      validate("42", node.path(0).path("acme"));
      validate("42", node.path(1).path("outcome"));
      RunDAO run = RunDAO.findById(runId);
      assertEquals(1, run.datasets.size());
   }

   @org.junit.jupiter.api.Test
   public void testTransformationSingleSchemaTestWithoutTransformer(TestInfo info) throws InterruptedException {
      Test exampleTest = createExampleTest(getTestName(info));
      Test test = createTest(exampleTest);

      BlockingQueue<DataSet.EventNew> dataSetQueue = eventConsumerQueue(DataSet.EventNew.class, DataSetDAO.EVENT_NEW, e -> e.dataset.testid.equals(test.id));
      Schema acmeSchema = createExampleSchema("AceCorp", "AceInc", "AceRrUs", false);

      uploadRun(runWithValue(42.0d, acmeSchema), test.name);

      DataSet.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);

      assertNotNull(event);
      JsonNode node = event.dataset.data;
      assertTrue(node.isArray());
      ObjectNode object = (ObjectNode)node.path(0);
      JsonNode schema = object.path("$schema");
      assertEquals("urn:AceInc:AceRrUs:1.0", schema.textValue());
      JsonNode value = object.path("value");
      assertEquals(42, value.intValue());
   }

   @org.junit.jupiter.api.Test
   public void testTransformationNestedSchemasWithoutTransformers(TestInfo info) throws InterruptedException {
      Test test = createTest(createExampleTest(getTestName(info)));
      BlockingQueue<DataSet.EventNew> dataSetQueue = eventConsumerQueue(DataSet.EventNew.class, DataSetDAO.EVENT_NEW, e -> e.dataset.testid.equals(test.id));
      Schema schemaA = createExampleSchema("Ada", "Ada", "Ada", false);
      Schema schemaB = createExampleSchema("Bdb", "Bdb", "Bdb", false);
      Schema schemaC = createExampleSchema("Cdc", "Cdc", "Cdc", false);

      ObjectNode data = runWithValue(1, schemaA);
      data.set("nestedB", runWithValue(2, schemaB));
      data.set("nestedC", runWithValue(3, schemaC));
      uploadRun(data, test.name);

      DataSet.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);

      assertNotNull(event);
      DataSet dataset = event.dataset;
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
      Test exampleTest = createExampleTest(getTestName(info));
      Test test = createTest(exampleTest);

      BlockingQueue<DataSet.EventNew> dataSetQueue = eventConsumerQueue(DataSet.EventNew.class, DataSetDAO.EVENT_NEW, e -> e.dataset.testid.equals(test.id));

      Schema appleSchema = createExampleSchema("AppleCorp", "AppleInc", "AppleRrUs", false);

      ObjectNode data = runWithValue(42.0d, appleSchema);
      ObjectNode nested = runWithValue(52.0d, appleSchema);
      data.set("field_" + appleSchema.name, nested);

      uploadRun(data, test.name);

      DataSet.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);

      assertNotNull(event);
      JsonNode node = event.dataset.data;
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
      Schema schema = createExampleSchema("ArrayCorp", "ArrayInc", "ArrayRrUs", false);
      Extractor arrayPath = new Extractor("mheep", "$.values", false);
      String arrayFunction = "mheep => { return mheep.map(x => ({ \"outcome\": x }))}";

      Extractor scalarPath = new Extractor("sheep", "$.value", false);
      String scalarFunction = "sheep => { return ({  \"outcome\": { sheep } }) }";

      Transformer arrayTransformer = createTransformer("arrayT", schema, arrayFunction, arrayPath);
      Transformer scalarTransformer = createTransformer("scalarT", schema, scalarFunction, scalarPath);

      Test exampleTest = createExampleTest(getTestName(info));
      Test test = createTest(exampleTest);
      addTransformer(test, arrayTransformer, scalarTransformer);

      BlockingQueue<DataSet.EventNew> dataSetQueue = eventConsumerQueue(DataSet.EventNew.class, DataSetDAO.EVENT_NEW, e -> e.dataset.testid.equals(test.id));

      ObjectNode data = runWithValue(42.0d, schema);

      uploadRun(data,test.name);

      DataSet.EventNew first = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
      DataSet.EventNew second = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
      DataSet.EventNew third = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);

      assertNotNull(first);
      assertNotNull(second);
      assertNotNull(third);
      assertTrue(first.dataset.data.isArray());
      String target = postFunctionSchemaUri(schema);
      validateScalarArray(first.dataset, target);
      validateScalarArray(second.dataset, target);
      validateScalarArray(third.dataset, target);
   }

   @org.junit.jupiter.api.Test
   public void testTransformationChoosingSchema(TestInfo info) throws InterruptedException {
      Schema schemaA = createExampleSchema("Aba", "Aba", "Aba", false);
      Extractor path = new Extractor("value", "$.value", false);
      Transformer transformerA = createTransformer("A", schemaA, "value => ({\"by\": \"A\"})", path);

      Schema schemaB = createExampleSchema("Bcb", "Bcb", "Bcb", false);
      Transformer transformerB = createTransformer("B", schemaB, "value => ({\"by\": \"B\"})");

      Test test = createTest(createExampleTest(getTestName(info)));
      addTransformer(test, transformerA, transformerB);

      BlockingQueue<DataSet.EventNew> dataSetQueue = eventConsumerQueue(DataSet.EventNew.class, DataSetDAO.EVENT_NEW, e -> e.dataset.testid.equals(test.id));

      uploadRun(runWithValue(42, schemaB), test.name);
      DataSet.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
      assertNotNull(event);
      DataSet dataset = event.dataset;
      assertTrue(dataset.data.isArray());
      assertEquals(1, dataset.data.size());
      assertEquals("B", dataset.data.get(0).path("by").asText());

      assertNull(dataSetQueue.poll(50, TimeUnit.MILLISECONDS));
   }

   @org.junit.jupiter.api.Test
   public void testTransformationWithoutMatchFirstLevel(TestInfo info) throws InterruptedException {
      Schema schema = createExampleSchema("Aca", "Aca", "Aca", false);
      testTransformationWithoutMatch(info, schema, runWithValue(42, schema));
   }

   @org.junit.jupiter.api.Test
   public void testTransformationWithoutMatchSecondLevel(TestInfo info) throws InterruptedException {
      Schema schema = createExampleSchema("B", "B", "B", false);
      testTransformationWithoutMatch(info, schema, JsonNodeFactory.instance.objectNode().set("nested", runWithValue(42, schema)));
   }

   @org.junit.jupiter.api.Test
   public void testSchemaTransformerWithExtractorProducingNullValue(TestInfo info) throws InterruptedException {
      Schema schema = createExampleSchema("DDDD", "DDDDInc", "DDDDRrUs", true);
      Extractor scalarPath = new Extractor("sheep", "$.duff", false);
      Transformer scalarTransformer = createTransformer("tranProcessNullExtractorValue", schema, "sheep => ({ outcome: { sheep }})", scalarPath);

      Test exampleTest = createExampleTest(getTestName(info));
      Test test = createTest(exampleTest);
      addTransformer(test, scalarTransformer);

      BlockingQueue<DataSet.EventNew> dataSetQueue = eventConsumerQueue(DataSet.EventNew.class, DataSetDAO.EVENT_NEW, e -> e.dataset.testid.equals(test.id));

      ObjectNode data = runWithValue(42.0d, schema);

      uploadRun(data,test.name);

      DataSet.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
      assertNotNull(event);
      JsonNode eventData = event.dataset.data;
      assertTrue(eventData.isArray());
      assertEquals(1, eventData.size());
      JsonNode sheep = eventData.path(0).path("outcome").path("sheep");
      assertTrue(sheep.isEmpty());
   }

   private void testTransformationWithoutMatch(TestInfo info, Schema schema, ObjectNode data) throws InterruptedException {
      Extractor firstMatch = new Extractor("foo", "$.foo", false);
      Extractor allMatches = new Extractor("bar", "$.bar[*].x", false);
      allMatches.array = true;
      Extractor value = new Extractor("value", "$.value", false);
      Extractor values = new Extractor("values", "$.values[*]", false);
      values.array = true;

      Transformer transformerNoFunc = createTransformer("noFunc", schema, null, firstMatch, allMatches);
      Transformer transformerFunc = createTransformer("func", schema, "({foo, bar}) => ({ foo, bar })", firstMatch, allMatches);
      Transformer transformerCombined = createTransformer("combined", schema, null, firstMatch, allMatches, value, values);

      Test test = createTest(createExampleTest(getTestName(info)));
      addTransformer(test, transformerNoFunc, transformerFunc, transformerCombined);
      BlockingQueue<DataSet.EventNew> dataSetQueue = eventConsumerQueue(DataSet.EventNew.class, DataSetDAO.EVENT_NEW, e -> e.dataset.testid.equals(test.id));

      uploadRun(data, test.name);
      DataSet.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);

      assertNotNull(event);
      DataSet dataset = event.dataset;
      assertTrue(dataset.data.isArray());
      assertEquals(3, dataset.data.size());
      dataset.data.forEach(item -> {
         assertTrue(item.path("foo").isNull());
         TestUtil.assertEmptyArray(item.path("bar"));
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

   @org.junit.jupiter.api.Test
   public void testRecalculateDatasets() {
      withExampleDataset(createTest(createExampleTest("dummy")), JsonNodeFactory.instance.objectNode(), ds -> {
         Util.withTx(tm, () -> {
            try (CloseMe ignored = roleManager.withRoles(SYSTEM_ROLES)) {
               DataSetDAO dbDs = DataSetDAO.findById(ds.id);
               assertNotNull(dbDs);
               dbDs.delete();
               em.flush();
               em.clear();
            }
            return null;
         });
         List<Integer> dsIds1 = recalculateDataset(ds.runId);
         assertEquals(1, dsIds1.size());
         try (CloseMe ignored = roleManager.withRoles(SYSTEM_ROLES)) {
            List<DataSetDAO> dataSets = DataSetDAO.find("run.id", ds.runId).list();
            assertEquals(1, dataSets.size());
            assertEquals(dsIds1.get(0), dataSets.get(0).id);
            em.clear();
         }
         List<Integer> dsIds2 = recalculateDataset(ds.runId);
         try (CloseMe ignored = roleManager.withRoles(SYSTEM_ROLES)) {
            List<DataSetDAO> dataSets = DataSetDAO.find("run.id", ds.runId).list();
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

   @org.junit.jupiter.api.Test
   public void testUploadToPrivateTest() {
      Test test = createExampleTest("supersecret");
      test.access = Access.PRIVATE;
      test = createTest(test);

      long now = System.currentTimeMillis();
      int runID = uploadRun(now, now, JsonNodeFactory.instance.objectNode(), test.name, test.owner, Access.PRIVATE);

      RunService.RunExtended response = RestAssured.given().auth().oauth2(getTesterToken())
              .header(HttpHeaders.CONTENT_TYPE, "application/json")
              .body(org.testcontainers.shaded.com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
              .get("/api/run/" + runID)
              .then()
              .statusCode(200)
              .extract().as(RunService.RunExtended.class);
      assertNotNull(response);
      assertEquals(test.name, response.testname);
   }

   @org.junit.jupiter.api.Test
   public void testUploadToPrivateUsingToken() {
      final String MY_SECRET_TOKEN = "mySecretToken";
      Test test = createExampleTest("supersecret");
      test.access = Access.PRIVATE;
      test = createTest(test);

      // TestToken.value is not readable, therefore we can't pass it in.
      addToken(test, TestTokenDAO.READ + TestTokenDAO.UPLOAD, MY_SECRET_TOKEN);

      long now = System.currentTimeMillis();
      RestAssured.given()
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .body(org.testcontainers.shaded.com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
            .post("/api/run/data?start=" + now + "&stop=" + now + "&test=" + test.name + "&owner=" + UPLOADER_ROLES[0] +
                  "&access=" + Access.PRIVATE + "&token=" + MY_SECRET_TOKEN)
            .then()
            .statusCode(200)
            .extract().asString();
   }

   @org.junit.jupiter.api.Test
   public void testRetrieveData() {
      Test test = createTest(createExampleTest("dummy"));
      Schema schemaA = createExampleSchema("A", "A", "A", false);
      Schema schemaB = createExampleSchema("B", "B", "B", false);

      ObjectNode data1 = JsonNodeFactory.instance.objectNode()
            .put("$schema", schemaA.uri).put("value", 42);
      int run1 = uploadRun(data1, test.name);

      JsonNode data1Full = getData(run1, null);
      assertEquals(data1, data1Full);
      JsonNode data1A = getData(run1, schemaA);
      assertEquals(data1, data1A);

      ArrayNode data2 = JsonNodeFactory.instance.arrayNode();
      data2.addObject().put("$schema", schemaA.uri).put("value", 43);
      data2.addObject().put("$schema", schemaB.uri).put("value", 44);
      int run2 = uploadRun(data2, test.name);

      JsonNode data2Full = getData(run2, null);
      assertEquals(data2, data2Full);
      JsonNode data2A = getData(run2, schemaA);
      assertEquals(data2.get(0), data2A);
      JsonNode data2B = getData(run2, schemaB);
      assertEquals(data2.get(1), data2B);

      ObjectNode data3 = JsonNodeFactory.instance.objectNode();
      data3.putObject("foo").put("$schema", schemaA.uri).put("value", 45);
      data3.putObject("bar").put("$schema", schemaB.uri).put("value", 46);
      int run3 = uploadRun(data3, test.name);

      JsonNode data3Full = getData(run3, null);
      assertEquals(data3, data3Full);
      JsonNode data3A = getData(run3, schemaA);
      assertEquals(data3.get("foo"), data3A);
      JsonNode data3B = getData(run3, schemaB);
      assertEquals(data3.get("bar"), data3B);
   }

   @org.junit.jupiter.api.Test
   public void testUploadWithMetadata() throws InterruptedException {
      Test test = createTest(createExampleTest("with_meta"));
      createSchema("Foo", "urn:foo");
      createSchema("Bar", "urn:bar");
      createSchema("Q", "urn:q");
      Schema gooSchema = createSchema("Goo", "urn:goo");
      Transformer transformer = createTransformer("ttt", gooSchema, "goo => ({ oog: goo })", new Extractor("goo", "$.goo", false));
      addTransformer(test, transformer);
      Schema postSchema = createSchema("Post", "uri:Goo-post-function");

      long now = System.currentTimeMillis();
      ObjectNode data = simpleObject("urn:foo", "foo", "xxx");
      ArrayNode metadata = JsonNodeFactory.instance.arrayNode();
      metadata.add(simpleObject("urn:bar", "bar", "yyy"));
      metadata.add(simpleObject("urn:goo", "goo", "zzz"));

      BlockingQueue<DataSet.EventNew> dsQueue = eventConsumerQueue(DataSet.EventNew.class, DataSetDAO.EVENT_NEW, e -> e.dataset.testid == (int) test.id);

      int run1 = uploadRun(now, data, metadata, test.name);

      DataSet.EventNew event1 = dsQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(event1);
      assertEquals(run1, event1.dataset.runId);
      assertEquals(3, event1.dataset.data.size());
      JsonNode foo = getBySchema(event1.dataset.data, "urn:foo");
      assertEquals("xxx", foo.path("foo").asText());
      JsonNode bar = getBySchema(event1.dataset.data, "urn:bar");
      assertEquals("yyy", bar.path("bar").asText());
      JsonNode goo = getBySchema(event1.dataset.data, postSchema.uri);
      assertEquals("zzz", goo.path("oog").asText());

      // test auto-wrapping of object metadata into array
      int run2 = uploadRun(now + 1, data, simpleObject("urn:q", "qqq", "xxx"), test.name);
      DataSet.EventNew event2 = dsQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(event2);
      assertEquals(run2, event2.dataset.runId);
      assertEquals(2, event2.dataset.data.size());
      JsonNode qqq = getBySchema(event2.dataset.data, "urn:q");
      assertEquals("xxx", qqq.path("qqq").asText());
   }

   @org.junit.jupiter.api.Test
   public void testListAllRuns() throws IOException {
      Test test = createTest(createExampleTest("with_meta"));
      createSchema("Foo", "urn:foo");
      createSchema("Bar", "urn:bar");
      createSchema("Q", "urn:q");
      Schema gooSchema = createSchema("Goo", "urn:goo");
      Transformer transformer = createTransformer("ttt", gooSchema, "goo => ({ oog: goo })", new Extractor("goo", "$.goo", false));
      addTransformer(test, transformer);
      Schema postSchema = createSchema("Post", "uri:Goo-post-function");

      long now = System.currentTimeMillis();
      ObjectNode data = simpleObject("urn:foo", "foo", "xxx");
      ArrayNode metadata = JsonNodeFactory.instance.arrayNode();
      metadata.add(simpleObject("urn:bar", "bar", "yyy"));
      metadata.add(simpleObject("urn:goo", "goo", "zzz"));

      int run1 = uploadRun(now, data, metadata, test.name);

      RunService.RunsSummary runs = jsonRequest()
            .get("/api/run/list?limit=10&page=1&query=$.*")
              .then()
              .statusCode(200)
              .extract()
              .as(RunService.RunsSummary.class);

      assertEquals(1, runs.runs.size());
      assertEquals(test.name, runs.runs.get(0).testname);
   }

   @org.junit.jupiter.api.Test
   public void testListAllRunsFromFiles() throws IOException {
      populateDateFromFiles();

      RunService.RunsSummary runs = jsonRequest()
              .get("/api/run/list?limit=10&page=1&"+
                      "query=$.buildHash ? (@ == \"defec8eddeadbeafcafebabeb16b00b5\")"
              )
              .then()
              .statusCode(200)
              .extract()
              .as(RunService.RunsSummary.class);

      assertEquals(1, runs.runs.size());
   }

   private JsonNode getBySchema(JsonNode data, String schema) {
      JsonNode foo = StreamSupport.stream(data.spliterator(), false)
            .filter(item -> schema.equals(item.path("$schema").asText())).findFirst().orElse(null);
      assertNotNull(foo);
      return foo;
   }

   private ObjectNode simpleObject(String schema, String key, String value) {
      ObjectNode data = JsonNodeFactory.instance.objectNode();
      data.put("$schema", schema);
      data.put(key, value);
      return data;
   }

   private JsonNode getData(int runId, Schema schema) {
      RequestSpecification request = jsonRequest();
      if (schema != null) {
         request = request.queryParam("schemaUri", schema.uri);
      }
      return request.get("/api/run/" + runId + "/data").then().extract().body().as(JsonNode.class);
   }
}
