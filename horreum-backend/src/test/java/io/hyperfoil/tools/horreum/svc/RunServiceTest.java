package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import org.apache.groovy.util.Maps;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.alerting.ChangeDetection;
import io.hyperfoil.tools.horreum.api.alerting.Variable;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.Dataset;
import io.hyperfoil.tools.horreum.api.data.ExperimentComparison;
import io.hyperfoil.tools.horreum.api.data.ExperimentProfile;
import io.hyperfoil.tools.horreum.api.data.ExportedLabelValues;
import io.hyperfoil.tools.horreum.api.data.Extractor;
import io.hyperfoil.tools.horreum.api.data.Label;
import io.hyperfoil.tools.horreum.api.data.Schema;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.api.data.TestExport;
import io.hyperfoil.tools.horreum.api.data.Transformer;
import io.hyperfoil.tools.horreum.api.data.changeDetection.ChangeDetectionModelType;
import io.hyperfoil.tools.horreum.api.internal.services.AlertingService;
import io.hyperfoil.tools.horreum.api.services.DatasetService;
import io.hyperfoil.tools.horreum.api.services.ExperimentService;
import io.hyperfoil.tools.horreum.api.services.RunService;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.entity.data.DatasetDAO;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import io.hyperfoil.tools.horreum.mapper.DatasetMapper;
import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.test.InMemoryAMQTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.hyperfoil.tools.horreum.test.TestUtil;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(InMemoryAMQTestProfile.class)
public class RunServiceTest extends BaseServiceTest {
    private static final int POLL_DURATION_SECONDS = 10;

    @org.junit.jupiter.api.Test
    public void testTransformationNoSchemaInData(TestInfo info) throws InterruptedException {
        Test exampleTest = createExampleTest(getTestName(info));
        Test test = createTest(exampleTest);

        BlockingQueue<Dataset.EventNew> dataSetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);
        Extractor path = new Extractor("foo", "$.value", false);
        Schema schema = createExampleSchema(info);

        Transformer transformer = createTransformer("acme", schema, "", path);
        addTransformer(test, transformer);
        uploadRun("{\"corporation\":\"acme\"}", test.name);

        Dataset.EventNew event = dataSetQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(event);
        DatasetDAO dataset = DatasetDAO.findById(event.datasetId);
        TestUtil.assertEmptyArray(dataset.data);
    }

    @org.junit.jupiter.api.Test
    public void testTransformationWithoutSchema(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));
        BlockingQueue<Dataset.EventNew> dataSetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);

        Schema schema = createExampleSchema(info);

        int runId = uploadRun(runWithValue(42, schema).toString(), test.name);

        assertNewDataset(dataSetQueue, runId);
        em.clear();

        BlockingQueue<Integer> trashedQueue = trashRun(runId, test.id, true);

        RunDAO run = RunDAO.findById(runId);
        assertNotNull(run);
        assertTrue(run.trashed);
        assertEquals(0, DatasetDAO.count());

        em.clear();

        // reinstate the run
        jsonRequest().post("/api/run/" + runId + "/trash?isTrashed=false").then().statusCode(204);
        assertNull(trashedQueue.poll(50, TimeUnit.MILLISECONDS));
        run = RunDAO.findById(runId);
        assertFalse(run.trashed);
        assertNewDataset(dataSetQueue, runId);
    }

    private String createTransformingSchema(Test t) {
        Schema fooSchema = createSchema("foo", "urn:fooBar");
        Schema postTransformSchema = createSchema("foo-post-function", postFunctionSchemaUri(fooSchema));
        Extractor fooExtractor = new Extractor();
        fooExtractor.name = "foo";
        fooExtractor.jsonpath = "$.foo";
        Extractor barExtractor = new Extractor();
        barExtractor.name = "bar";
        barExtractor.jsonpath = "$.bar";

        addLabel(postTransformSchema, "labelFoo", "", fooExtractor);
        addLabel(postTransformSchema, "labelBar", "", barExtractor);

        Extractor transformExtractor = new Extractor();
        transformExtractor.name = "values";
        transformExtractor.jsonpath = "$.values";

        Transformer transformer = createTransformer("fooBar", fooSchema, "", transformExtractor);
        addTransformer(t, transformer);

        Integer runId = uploadRun("""
                { "values":[
                  { "foo": "uno", "bar": "dox"},
                  { "foo": "dos", "bar": "box"}
                  ]
                }
                """, t.name, fooSchema.uri)
                .get(0);
        recalculateDatasetForRun(runId);
        return runId.toString();
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterMultiSelect() {
        Test t = createTest(createExampleTest("my-test"));
        String id = createTransformingSchema(t);
        JsonNode response = jsonRequest()
                .queryParam("filter", Maps.of("labelBar", Arrays.asList("dox", 30)))
                .queryParam("multiFilter", true)
                .get("/api/run/" + id + "/labelValues")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(1, arrayResponse.size(), "unexpected number of responses " + response);
        JsonNode first = arrayResponse.get(0);
        assertTrue(first.has("values"), first.toString());
        JsonNode values = first.get("values");
        assertTrue(values.has("labelBar"), values.toString());
        assertEquals(JsonNodeType.STRING, values.get("labelBar").getNodeType());
        assertEquals("dox", values.get("labelBar").asText());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesIncludeExcluded() {
        Test t = createTest(createExampleTest("my-test"));
        String id = labelValuesSetup(t, true);

        JsonNode response = jsonRequest()
                .get("/api/run/" + id + "/labelValues?include=labelFoo&exclude=labelFoo")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(1, arrayResponse.size());
        assertInstanceOf(ObjectNode.class, arrayResponse.get(0));
        ObjectNode objectNode = (ObjectNode) arrayResponse.get(0).get("values");
        assertFalse(objectNode.has("labelFoo"), objectNode.toString());
        assertTrue(objectNode.has("labelBar"), objectNode.toString());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesIncludeTwoParams() {
        Test t = createTest(createExampleTest("my-test"));
        String id = labelValuesSetup(t, true);

        JsonNode response = jsonRequest()
                .get("/api/run/" + id + "/labelValues?include=labelFoo&include=labelBar")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(1, arrayResponse.size());
        assertInstanceOf(ObjectNode.class, arrayResponse.get(0));
        ObjectNode objectNode = (ObjectNode) arrayResponse.get(0).get("values");
        assertTrue(objectNode.has("labelFoo"), objectNode.toString());
        assertTrue(objectNode.has("labelBar"), objectNode.toString());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesIncludeTwoSeparated() {
        Test t = createTest(createExampleTest("my-test"));
        String id = labelValuesSetup(t, true);

        JsonNode response = jsonRequest()
                .get("/api/run/" + id + "/labelValues?include=labelFoo,labelBar")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(1, arrayResponse.size());
        assertInstanceOf(ObjectNode.class, arrayResponse.get(0));
        ObjectNode objectNode = (ObjectNode) arrayResponse.get(0).get("values");
        assertTrue(objectNode.has("labelFoo"), objectNode.toString());
        assertTrue(objectNode.has("labelBar"), objectNode.toString());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesInclude() {
        Test t = createTest(createExampleTest("my-test"));
        String id = labelValuesSetup(t, true);

        JsonNode response = jsonRequest()
                .get("/api/run/" + id + "/labelValues?include=labelFoo")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(1, arrayResponse.size());
        assertInstanceOf(ObjectNode.class, arrayResponse.get(0));
        ObjectNode objectNode = (ObjectNode) arrayResponse.get(0).get("values");
        assertTrue(objectNode.has("labelFoo"));
        assertFalse(objectNode.has("labelBar"));
    }

    @org.junit.jupiter.api.Test
    public void labelValuesExclude() {
        Test t = createTest(createExampleTest("my-test"));
        String id = labelValuesSetup(t, true);

        JsonNode response = jsonRequest()
                .get("/api/run/" + id + "/labelValues?exclude=labelFoo")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(1, arrayResponse.size());
        assertInstanceOf(ObjectNode.class, arrayResponse.get(0));
        ObjectNode objectNode = (ObjectNode) arrayResponse.get(0).get("values");
        assertFalse(objectNode.has("labelFoo"), objectNode.toPrettyString());
        assertTrue(objectNode.has("labelBar"), objectNode.toPrettyString());

    }

    @org.junit.jupiter.api.Test
    public void labelValuesPublicTestPublicRun(TestInfo info) throws InterruptedException {
        Test test = createExampleTest(getTestName(info));
        test.access = Access.PUBLIC;
        Test persistedTest = createTest(test);

        BlockingQueue<Dataset.EventNew> dataSetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW,
                persistedTest.id);
        Schema schema = createExampleSchema(info);

        int runId = uploadRun(runWithValue(42, schema).toString(), test.name);
        assertNewDataset(dataSetQueue, runId);
        recalculateDatasetForRun(runId);

        List<ExportedLabelValues> rtrn = jsonRequest()
                .get("/api/run/" + runId + "/labelValues")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath().getList(".", ExportedLabelValues.class);

        assertEquals(1, rtrn.size(), "expected number of label values");
        ExportedLabelValues elv = rtrn.get(0);
        assertEquals(1, elv.values.size(), "expected number of LabelValues");
        assertEquals(runId, elv.runId, "expected runId");
        assertTrue(elv.values.containsKey("value"));
        assertEquals(42, elv.values.get("value").asInt());
    }

    private void assertNewDataset(BlockingQueue<Dataset.EventNew> dataSetQueue, int runId) throws InterruptedException {
        Dataset.EventNew event = dataSetQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(event);
        assertEquals(runId, event.runId);
        DatasetDAO ds = DatasetDAO.findById(event.datasetId);
        assertNotNull(ds);
        assertEquals(runId, ds.run.id);
    }

    @org.junit.jupiter.api.Test
    public void testTransformationWithoutSchemaInUpload(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));
        BlockingQueue<Dataset.EventNew> dataSetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);

        setTestVariables(test, "Value", new Label("value", 1));

        uploadRun("{ \"foo\":\"bar\"}", test.name);

        Dataset.EventNew event = dataSetQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(event);
        DatasetDAO ds = DatasetDAO.findById(event.datasetId);
        TestUtil.assertEmptyArray(ds.data);
    }

    @org.junit.jupiter.api.Test
    public void testTransformationWithoutExtractorsAndBlankFunction(TestInfo info) throws InterruptedException {
        Test exampleTest = createExampleTest(getTestName(info));
        Test test = createTest(exampleTest);

        BlockingQueue<Dataset.EventNew> dataSetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);
        Schema schema = createExampleSchema(info);

        Transformer transformer = createTransformer("acme", schema, "");
        addTransformer(test, transformer);
        uploadRun(runWithValue(42.0d, schema), test.name);

        Dataset.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
        assertNotNull(event);
        DatasetDAO ds = DatasetDAO.findById(event.datasetId);
        JsonNode node = ds.data;
        assertTrue(node.isArray());
        assertEquals(1, node.size());
        assertEquals(1, node.get(0).size());
        assertTrue(node.get(0).hasNonNull("$schema"));
    }

    @org.junit.jupiter.api.Test
    public void testTransformationWithExtractorAndBlankFunction(TestInfo info) throws InterruptedException {
        Test exampleTest = createExampleTest(getTestName(info));
        Test test = createTest(exampleTest);

        BlockingQueue<Dataset.EventNew> dataSetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);
        Schema schema = createExampleSchema("AcneCorp", "AcneInc", "AcneRrUs", false);

        Extractor path = new Extractor("foo", "$.value", false);
        Transformer transformer = createTransformer("acme", schema, "", path); // blank function
        addTransformer(test, transformer);
        uploadRun(runWithValue(42.0d, schema), test.name);

        Dataset.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
        assertNotNull(event);
        DatasetDAO ds = DatasetDAO.findById(event.datasetId);
        assertTrue(ds.data.isArray());
        assertEquals(1, ds.data.size());
        // the result of single extractor is 42, hence this needs to be wrapped into an object (using `value`) before adding schema
        assertEquals(42, ds.data.path(0).path("value").intValue());
    }

    @org.junit.jupiter.api.Test
    public void testTransformationWithNestedSchema(TestInfo info) throws InterruptedException {
        Schema acmeSchema = createExampleSchema("AcmeCorp", "AcmeInc", "AcmeRrUs", false);
        Schema roadRunnerSchema = createExampleSchema("RoadRunnerCorp", "RoadRunnerInc", "RoadRunnerRrUs", false);

        Extractor acmePath = new Extractor("foo", "$.value", false);
        Transformer acmeTransformer = createTransformer("acme", acmeSchema, "value => ({ acme: value })", acmePath);
        Extractor roadRunnerPath = new Extractor("bah", "$.value", false);
        Transformer roadRunnerTransformer = createTransformer("roadrunner", roadRunnerSchema, "value => ({ outcome: value })",
                roadRunnerPath);

        Test exampleTest = createExampleTest(getTestName(info));
        Test test = createTest(exampleTest);
        addTransformer(test, acmeTransformer, roadRunnerTransformer);

        BlockingQueue<Dataset.EventNew> dataSetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);

        String data = runWithValueSchemas(42.0d, acmeSchema, roadRunnerSchema).toString();
        int runId = uploadRun(data, test.name);

        Dataset.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);

        assertNotNull(event);
        DatasetDAO ds = DatasetDAO.findById(event.datasetId);
        JsonNode node = ds.data;
        assertTrue(node.isArray());
        assertEquals(2, node.size());
        validate("42", node.path(0).path("acme"));
        validate("42", node.path(1).path("outcome"));
        RunDAO run = RunDAO.findById(runId);
        assertEquals(1, run.datasets.size());
    }

    @org.junit.jupiter.api.Test
    public void testTransformationSingleSchemaTestWithoutTransformer(TestInfo info) throws InterruptedException {
        Test exampleTest = createExampleTest(getTestName(info));
        Test test = createTest(exampleTest);

        BlockingQueue<Dataset.EventNew> dataSetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);
        Schema acmeSchema = createExampleSchema("AceCorp", "AceInc", "AceRrUs", false);

        uploadRun(runWithValue(42.0d, acmeSchema), test.name);

        Dataset.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);

        assertNotNull(event);
        DatasetDAO ds = DatasetDAO.findById(event.datasetId);
        JsonNode node = ds.data;
        assertTrue(node.isArray());
        ObjectNode object = (ObjectNode) node.path(0);
        JsonNode schema = object.path("$schema");
        assertEquals("urn:AceInc:AceRrUs:1.0", schema.textValue());
        JsonNode value = object.path("value");
        assertEquals(42, value.intValue());
    }

    @org.junit.jupiter.api.Test
    public void testTransformationNestedSchemasWithoutTransformers(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));
        BlockingQueue<Dataset.EventNew> dataSetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);
        Schema schemaA = createExampleSchema("Ada", "Ada", "Ada", false);
        Schema schemaB = createExampleSchema("Bdb", "Bdb", "Bdb", false);
        Schema schemaC = createExampleSchema("Cdc", "Cdc", "Cdc", false);

        ObjectNode data = runWithValue(1, schemaA);
        data.set("nestedB", runWithValue(2, schemaB));
        data.set("nestedC", runWithValue(3, schemaC));
        uploadRun(data, test.name);

        Dataset.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);

        assertNotNull(event);
        Dataset dataset = DatasetMapper.from(DatasetDAO.findById(event.datasetId));
        assertTrue(dataset.data.isArray());
        assertEquals(3, dataset.data.size());
        assertEquals(1, getBySchema(dataset, schemaA).path("value").intValue());
        assertEquals(2, getBySchema(dataset, schemaB).path("value").intValue());
        assertEquals(3, getBySchema(dataset, schemaC).path("value").intValue());

        assertNull(dataSetQueue.poll(50, TimeUnit.MILLISECONDS));
    }

    private JsonNode getBySchema(Dataset dataset, Schema schemaA) {
        return StreamSupport.stream(dataset.data.spliterator(), false)
                .filter(item -> schemaA.uri.equals(item.path("$schema").textValue()))
                .findFirst().orElseThrow(AssertionError::new);
    }

    @org.junit.jupiter.api.Test
    public void testTransformationUsingSameSchemaInBothLevelsTestWithoutTransformer(TestInfo info) throws InterruptedException {
        Test exampleTest = createExampleTest(getTestName(info));
        Test test = createTest(exampleTest);

        BlockingQueue<Dataset.EventNew> dataSetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);

        Schema appleSchema = createExampleSchema("AppleCorp", "AppleInc", "AppleRrUs", false);

        ObjectNode data = runWithValue(42.0d, appleSchema);
        ObjectNode nested = runWithValue(52.0d, appleSchema);
        data.set("field_" + appleSchema.name, nested);

        uploadRun(data, test.name);

        Dataset.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);

        assertNotNull(event);
        DatasetDAO ds = DatasetDAO.findById(event.datasetId);
        JsonNode node = ds.data;
        assertTrue(node.isArray());
        assertEquals(2, node.size());

        JsonNode first = node.path(0);
        assertEquals("urn:AppleInc:AppleRrUs:1.0", first.path("$schema").textValue());
        assertEquals(42, first.path("value").intValue());

        JsonNode second = node.path(1);
        assertEquals("urn:AppleInc:AppleRrUs:1.0", second.path("$schema").textValue());
        assertEquals(52, second.path("value").intValue());
    }

    @org.junit.jupiter.api.Test
    public void testTransformationUsingSingleSchemaTransformersProcessScalarPlusArray(TestInfo info)
            throws InterruptedException {
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

        BlockingQueue<Dataset.EventNew> dataSetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);

        ObjectNode data = runWithValue(42.0d, schema);

        uploadRun(data, test.name);

        Dataset.EventNew first = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
        Dataset.EventNew second = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
        Dataset.EventNew third = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);

        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(third);
        Dataset ds = DatasetMapper.from(DatasetDAO.findById(first.datasetId));
        assertTrue(ds.data.isArray());
        String target = postFunctionSchemaUri(schema);
        validateScalarArray(ds, target);
        ds = DatasetMapper.from(DatasetDAO.findById(second.datasetId));
        validateScalarArray(ds, target);
        ds = DatasetMapper.from(DatasetDAO.findById(third.datasetId));
        validateScalarArray(ds, target);
    }

    @org.junit.jupiter.api.Test
    public void testSelectRunBySchema(TestInfo info) throws InterruptedException {
        Schema schemaA = createExampleSchema("Aba", "Aba", "Aba", false);
        Test test = createTest(createExampleTest(getTestName(info)));

        BlockingQueue<Dataset.EventNew> dataSetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);

        uploadRun(runWithValue(42, schemaA), test.name);
        Dataset.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
        assertNotNull(event);
        assertNull(dataSetQueue.poll(50, TimeUnit.MILLISECONDS));

        RunService.RunsSummary runsSummary = jsonRequest()
                .get("/api/run/bySchema?uri=" + schemaA.uri)
                .then()
                .statusCode(200)
                .extract().body().as(RunService.RunsSummary.class);

        assertNotNull(runsSummary);
        assertEquals(1, runsSummary.total);
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

        BlockingQueue<Dataset.EventNew> dataSetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);

        uploadRun(runWithValue(42, schemaB), test.name);
        Dataset.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
        assertNotNull(event);
        Dataset dataset = DatasetMapper.from(DatasetDAO.findById(event.datasetId));
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
        testTransformationWithoutMatch(info, schema,
                JsonNodeFactory.instance.objectNode().set("nested", runWithValue(42, schema)));
    }

    @org.junit.jupiter.api.Test
    public void testSchemaTransformerWithExtractorProducingNullValue(TestInfo info) throws InterruptedException {
        Schema schema = createExampleSchema("DDDD", "DDDDInc", "DDDDRrUs", true);
        Extractor scalarPath = new Extractor("sheep", "$.duff", false);
        Transformer scalarTransformer = createTransformer("tranProcessNullExtractorValue", schema,
                "sheep => ({ outcome: { sheep }})", scalarPath);

        Test exampleTest = createExampleTest(getTestName(info));
        Test test = createTest(exampleTest);
        addTransformer(test, scalarTransformer);

        BlockingQueue<Dataset.EventNew> dataSetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);

        ObjectNode data = runWithValue(42.0d, schema);

        uploadRun(data, test.name);

        Dataset.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
        assertNotNull(event);
        Dataset dataset = DatasetMapper.from(DatasetDAO.findById(event.datasetId));
        JsonNode eventData = dataset.data;
        assertTrue(eventData.isArray());
        assertEquals(1, eventData.size());
        JsonNode sheep = eventData.path(0).path("outcome").path("sheep");
        assertTrue(sheep.isEmpty());
    }

    private void testTransformationWithoutMatch(TestInfo info, Schema schema, ObjectNode data) throws InterruptedException {
        Extractor firstMatch = new Extractor("foo", "$.foo", false);
        Extractor allMatches = new Extractor("bar", "$.bar[*].x", false);
        allMatches.isArray = true;
        Extractor value = new Extractor("value", "$.value", false);
        Extractor values = new Extractor("values", "$.values[*]", false);
        values.isArray = true;

        Transformer transformerNoFunc = createTransformer("noFunc", schema, null, firstMatch, allMatches);
        Transformer transformerFunc = createTransformer("func", schema, "({foo, bar}) => ({ foo, bar })", firstMatch,
                allMatches);
        Transformer transformerCombined = createTransformer("combined", schema, null, firstMatch, allMatches, value, values);

        Test test = createTest(createExampleTest(getTestName(info)));
        addTransformer(test, transformerNoFunc, transformerFunc, transformerCombined);
        BlockingQueue<Dataset.EventNew> dataSetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);

        uploadRun(data, test.name);
        Dataset.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);

        assertNotNull(event);
        Dataset dataset = DatasetMapper.from(DatasetDAO.findById(event.datasetId));
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
    public void testRecalculateDatasets() throws InterruptedException {
        withExampleDataset(createTest(createExampleTest("dummy")), JsonNodeFactory.instance.objectNode(), ds -> {
            Util.withTx(tm, () -> {
                try (CloseMe ignored = roleManager.withRoles(SYSTEM_ROLES)) {
                    DatasetDAO dbDs = DatasetDAO.findById(ds.id);
                    assertNotNull(dbDs);
                    dbDs.delete();
                    em.flush();
                    em.clear();
                }
                return null;
            });
            List<Integer> dsIds1 = recalculateDatasetForRun(ds.runId);
            assertEquals(1, dsIds1.size());
            try (CloseMe ignored = roleManager.withRoles(SYSTEM_ROLES)) {
                List<DatasetDAO> dataSets = DatasetDAO.find("run.id", ds.runId).list();
                assertEquals(1, dataSets.size());
                assertEquals(dsIds1.get(0), dataSets.get(0).id);
                em.clear();
            }
            List<Integer> dsIds2 = recalculateDatasetForRun(ds.runId);
            try (CloseMe ignored = roleManager.withRoles(SYSTEM_ROLES)) {
                List<DatasetDAO> dataSets = DatasetDAO.find("run.id", ds.runId).list();
                assertEquals(1, dataSets.size());
                assertEquals(dsIds2.get(0), dataSets.get(0).id);
            }
            return null;
        });
    }

    private void validateScalarArray(Dataset ds, String expectedTarget) {
        JsonNode n = ds.data;
        int outcome = n.path(0).findValue("outcome").asInt();
        assertTrue(outcome == 43 || outcome == 44 || outcome == 45);
        int value = n.path(1).path("outcome").path("sheep").asInt();
        assertEquals(42, value);
        String scalarTarget = n.path(0).path("$schema").textValue();
        assertEquals(expectedTarget, scalarTarget);
        String arrayTarget = n.path(1).path("$schema").textValue();
        assertEquals(expectedTarget, arrayTarget);
    }

    @org.junit.jupiter.api.Test
    public void addMicrosecondsInTimestamp() throws JsonProcessingException {
        Test test = createExampleTest("foo");
        test = createTest(test);
        JsonNode payload = new ObjectMapper().readTree(
                "{\"start_time\": \"2024-03-13T21:18:10.878423-04:00\", \"stop_time\": \"2024-03-13T21:18:11.878423-04:00\"}");
        uploadRun("$.start_time", "$.stop_time", test.name, test.owner, Access.PUBLIC,
                null, "test", payload);
    }

    @org.junit.jupiter.api.Test
    public void testUploadToPrivateTest() throws JsonProcessingException {
        Test test = createExampleTest("supersecret");
        test.access = Access.PRIVATE;
        test = createTest(test);

        JsonNode payload = new ObjectMapper().readTree(resourceToString("data/config-quickstart.jvm.json"));
        long now = System.currentTimeMillis();
        int runID = uploadRun(now, now, payload, test.name, test.owner, Access.PRIVATE);

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
        Transformer transformer = createTransformer("ttt", gooSchema, "goo => ({ oog: goo })",
                new Extractor("goo", "$.goo", false));
        addTransformer(test, transformer);
        Schema postSchema = createSchema("Post", "uri:Goo-post-function");

        long now = System.currentTimeMillis();
        ObjectNode data = simpleObject("urn:foo", "foo", "xxx");
        ArrayNode metadata = JsonNodeFactory.instance.arrayNode();
        metadata.add(simpleObject("urn:bar", "bar", "yyy"));
        metadata.add(simpleObject("urn:goo", "goo", "zzz"));

        BlockingQueue<Dataset.EventNew> dsQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);

        int run1 = uploadRun(now, data, metadata, test.name);

        Dataset.EventNew event1 = dsQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(event1);
        assertEquals(run1, event1.runId);
        DatasetDAO dataset = DatasetDAO.findById(event1.datasetId);
        assertEquals(3, dataset.data.size());
        JsonNode foo = getBySchema(dataset.data, "urn:foo");
        assertEquals("xxx", foo.path("foo").asText());
        JsonNode bar = getBySchema(dataset.data, "urn:bar");
        assertEquals("yyy", bar.path("bar").asText());
        JsonNode goo = getBySchema(dataset.data, postSchema.uri);
        assertEquals("zzz", goo.path("oog").asText());

        // test auto-wrapping of object metadata into array
        int run2 = uploadRun(now + 1, data, simpleObject("urn:q", "qqq", "xxx"), test.name);
        Dataset.EventNew event2 = dsQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(event2);
        assertEquals(run2, event2.runId);
        dataset = DatasetDAO.findById(event2.datasetId);
        assertEquals(2, dataset.data.size());
        JsonNode qqq = getBySchema(dataset.data, "urn:q");
        assertEquals("xxx", qqq.path("qqq").asText());
    }

    @org.junit.jupiter.api.Test
    public void testChangeUploadRunSchemas(TestInfo info) throws InterruptedException {
        Test exampleTest = createExampleTest(getTestName(info));
        Test test = createTest(exampleTest);

        BlockingQueue<Dataset.EventNew> dataSetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);
        Schema schemaA = createExampleSchema("AcneCorp", "AcneInc", "RootSchema", false);
        Schema schemaB = createExampleSchema("AcneCorp", "AcneInc", "", false);

        Extractor path = new Extractor("foo", "$.value", false);
        Transformer transformer = createTransformer("acme", schemaA, "", path); // blank function
        addTransformer(test, transformer);

        //      1. Upload a run without a schema, then define a schema after upload
        int runID = uploadRun(runWithValue(42.0d), test.name);

        Dataset.EventNew event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
        assertNotNull(event);

        Map<Object, Object> schemaMap = RestAssured.given().auth().oauth2(getTesterToken())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                .body(schemaA.uri)
                .post("/api/run/" + runID + "/schema")
                .then()
                .statusCode(200)
                .extract().as(Map.class);

        assertNotNull(schemaMap);
        assertNotEquals(0, schemaMap.size());

        //      2. Upload a run WITH a schema, then change the schema after upload

        runID = uploadRun(runWithValue(42.0d, schemaA), test.name);

        List<Object> runSchemas = em.createNativeQuery("SELECT * FROM run_schemas WHERE runid = ?1").setParameter(1, runID)
                .getResultList();

        assertNotEquals(0, runSchemas.size());

        event = dataSetQueue.poll(POLL_DURATION_SECONDS, TimeUnit.SECONDS);
        assertNotNull(event);

        schemaMap = RestAssured.given().auth().oauth2(getTesterToken())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                .body(schemaB.uri)
                .post("/api/run/" + runID + "/schema")
                .then()
                .statusCode(200)
                .extract().as(Map.class);

        assertNotNull(schemaMap);
        assertNotEquals(0, schemaMap.size());
    }

    @org.junit.jupiter.api.Test
    public void testListAllRuns() throws IOException {
        Test test = createTest(createExampleTest("with_meta"));
        createSchema("Foo", "urn:foo");
        createSchema("Bar", "urn:bar");
        createSchema("Q", "urn:q");
        Schema gooSchema = createSchema("Goo", "urn:goo");
        Transformer transformer = createTransformer("ttt", gooSchema, "goo => ({ oog: goo })",
                new Extractor("goo", "$.goo", false));
        addTransformer(test, transformer);
        Schema postSchema = createSchema("Post", "uri:Goo-post-function");

        long now = System.currentTimeMillis();
        ObjectNode data = simpleObject("urn:foo", "foo", "xxx");
        ArrayNode metadata = JsonNodeFactory.instance.arrayNode();
        metadata.add(simpleObject("urn:bar", "bar", "yyy"));
        metadata.add(simpleObject("urn:goo", "goo", "zzz"));

        uploadRun(now, data, metadata, test.name);

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
        populateDataFromFiles();

        RunService.RunsSummary runs = jsonRequest()
                .get("/api/run/list?limit=10&page=1&" +
                        "query=$.buildHash ? (@ == \"defec8eddeadbeafcafebabeb16b00b5\")")
                .then()
                .statusCode(200)
                .extract()
                .as(RunService.RunsSummary.class);

        assertEquals(1, runs.runs.size());
    }

    @org.junit.jupiter.api.Test
    public void testAddRunFromData() throws JsonProcessingException {
        Test test = createExampleTest("supersecret");
        test.access = Access.PRIVATE;
        test = createTest(test);

        JsonNode payload = new ObjectMapper().readTree(resourceToString("data/config-quickstart.jvm.json"));

        Integer runId = uploadRun("$.start", "$.stop", test.name, test.owner, Access.PUBLIC,
                null, "test", payload).get(0);
        assertTrue(runId > 0);
    }

    @org.junit.jupiter.api.Test
    public void testTrashAndReinstateRun() throws JsonProcessingException, InterruptedException {
        Test test = createExampleTest("supersecret");
        test.access = Access.PRIVATE;
        test = createTest(test);
        createSchema("Foo", "urn:foo");

        JsonNode payload = new ObjectMapper().readTree(resourceToString("data/config-quickstart.jvm.json"));

        Integer runId = uploadRun("$.start", "$.stop", test.name, test.owner, Access.PUBLIC,
                "urn:foo", "test", payload).get(0);
        assertTrue(runId > 0);

        // double check run_schemas are properly created
        int nRunSchemas = em.createNativeQuery("SELECT * FROM run_schemas WHERE runid = ?1").setParameter(1, runId)
                .getResultList().size();
        assertEquals(1, nRunSchemas);

        // ensure the run_schemas get deleted when trashing a run
        trashRun(runId, test.id, true);
        nRunSchemas = em.createNativeQuery("SELECT * FROM run_schemas WHERE runid = ?1").setParameter(1, runId)
                .getResultList().size();
        assertEquals(0, nRunSchemas);

        // ensure the run_schemas get re-created when un-trashing a run
        trashRun(runId, test.id, false);
        nRunSchemas = em.createNativeQuery("SELECT * FROM run_schemas WHERE runid = ?1").setParameter(1, runId)
                .getResultList().size();
        assertEquals(1, nRunSchemas);
    }

    @org.junit.jupiter.api.Test
    public void testAddRunWithMetadataData() throws JsonProcessingException {
        Test test = createExampleTest("supersecret");
        test.access = Access.PRIVATE;
        test = createTest(test);

        JsonNode payload = new ObjectMapper().readTree(resourceToString("data/config-quickstart.jvm.json"));
        JsonNode metadata = JsonNodeFactory.instance.objectNode().put("$schema", "urn:foobar").put("foo", "bar");

        int runId = uploadRun("$.start", "$.stop", payload, metadata, test.name, test.owner, Access.PUBLIC);
        assertTrue(runId > 0);
    }

    @org.junit.jupiter.api.Test
    public void testJavascriptExecution() throws InterruptedException {
        Test test = createExampleTest("supersecret");
        test = createTest(test);

        Schema schema = new Schema();
        schema.uri = "urn:dummy:schema";
        schema.name = "Dummy";
        schema.owner = test.owner;
        schema.access = Access.PUBLIC;
        schema = addOrUpdateSchema(schema);

        long now = System.currentTimeMillis();
        String ts = String.valueOf(now);
        JsonNode data = JsonNodeFactory.instance.objectNode()
                .put("$schema", schema.uri)
                .put("value", "foobar");
        uploadRun(ts, ts, test.name, test.owner, Access.PUBLIC, schema.uri, null, data);

        int datasetId = -1;
        while (System.currentTimeMillis() < now + 10000) {
            DatasetService.DatasetList datasets = jsonRequest().get("/api/dataset/list/" + test.id).then().statusCode(200)
                    .extract().body().as(DatasetService.DatasetList.class);
            if (datasets.datasets.isEmpty()) {
                //noinspection BusyWait
                Thread.sleep(50);
            } else {
                Assertions.assertEquals(1, datasets.datasets.size());
                datasetId = datasets.datasets.iterator().next().id;
            }
        }
        Assertions.assertNotEquals(-1, datasetId);

        Label label = new Label();
        label.name = "foo";
        label.schemaId = schema.id;
        label.function = "value => value";
        label.extractors = Collections.singletonList(new Extractor("value", "$.value", false));
        DatasetService.LabelPreview preview = jsonRequest().body(label).post("/api/dataset/" + datasetId + "/previewLabel")
                .then().statusCode(200).extract().body().as(DatasetService.LabelPreview.class);
        Assertions.assertEquals("foobar", preview.value.textValue());
    }

    @org.junit.jupiter.api.Test
    public void runExperiment() throws InterruptedException {
        Test test = createExampleTest("supersecret");
        test = createTest(test);

        try {
            //1. Create new Schema
            Schema schema = new Schema();
            schema.uri = "urn:test-schema:0.1";
            schema.name = "test";
            schema.owner = test.owner;
            schema.access = Access.PUBLIC;
            schema = addOrUpdateSchema(schema);

            //2. Define schema labels
            Label lblCpu = new Label();
            lblCpu.name = "cpu";
            Extractor cpuExtractor = new Extractor("cpu", "$.data.cpu", false);
            lblCpu.extractors = List.of(cpuExtractor);
            lblCpu.access = Access.PUBLIC;
            lblCpu.owner = test.owner;
            lblCpu.metrics = true;
            lblCpu.filtering = false;
            lblCpu.id = addOrUpdateLabel(schema.id, lblCpu);

            Label lblThroughput = new Label();
            lblThroughput.name = "throughput";
            Extractor throughputExtractor = new Extractor("throughput", "$.data.throughput", false);
            lblThroughput.extractors = List.of(throughputExtractor);
            lblThroughput.access = Access.PUBLIC;
            lblThroughput.owner = test.owner;
            lblThroughput.metrics = true;
            lblThroughput.filtering = false;
            lblThroughput.id = addOrUpdateLabel(schema.id, lblThroughput);

            Label lblJob = new Label();
            lblJob.name = "job";
            Extractor jobExtractor = new Extractor("job", "$.job", false);
            lblJob.extractors = List.of(jobExtractor);
            lblJob.access = Access.PUBLIC;
            lblJob.owner = test.owner;
            lblJob.metrics = false;
            lblJob.filtering = true;
            lblJob.id = addOrUpdateLabel(schema.id, lblJob);

            Label lblBuildID = new Label();
            lblBuildID.name = "build-id";
            Extractor buildIDExtractor = new Extractor("build-id", "$.\"build-id\"", false);
            lblBuildID.extractors = List.of(buildIDExtractor);
            lblBuildID.access = Access.PUBLIC;
            lblBuildID.owner = test.owner;
            lblBuildID.metrics = false;
            lblBuildID.filtering = true;
            lblBuildID.id = addOrUpdateLabel(schema.id, lblBuildID);

            //3. Config change detection variables
            Variable variable = new Variable();
            variable.testId = test.id;
            variable.name = "throughput";
            variable.order = 0;
            variable.labels = new ArrayList<>();
            Label l = new Label();
            l.name = "throughput";
            variable.labels.add(l.name);
            ChangeDetection changeDetection = new ChangeDetection();
            changeDetection.model = ChangeDetectionModelType.names.RELATIVE_DIFFERENCE;

            changeDetection.config = (ObjectNode) mapper.readTree("{" +
                    "          \"window\": 1," +
                    "          \"filter\": \"mean\"," +
                    "          \"threshold\": 0.2," +
                    "          \"minPrevious\": 5" +
                    "        }");
            variable.changeDetection = new HashSet<>();
            variable.changeDetection.add(changeDetection);

            updateVariables(test.id, Collections.singletonList(variable));

            //need this for defining experiment
            List<Variable> variableList = variables(test.id);

            AlertingService.ChangeDetectionUpdate update = new AlertingService.ChangeDetectionUpdate();
            update.fingerprintLabels = Collections.emptyList();
            update.timelineLabels = Collections.emptyList();
            updateChangeDetection(test.id, update);

            //4. Define experiments
            ExperimentProfile experimentProfile = new ExperimentProfile();
            experimentProfile.id = -1; //TODO: fix profile add/Update
            experimentProfile.name = "robust-experiment";
            experimentProfile.selectorLabels = mapper.readTree(" [ \"job\" ] ");
            experimentProfile.selectorFilter = "value => !!value";
            experimentProfile.baselineLabels = mapper.readTree(" [ \"build-id\" ] ");
            experimentProfile.baselineFilter = "value => value == 1";

            ExperimentComparison experimentComparison = new ExperimentComparison();
            experimentComparison.model = "relativeDifference";
            experimentComparison.variableName = variableList.get(0).name; //should only contain one variable
            experimentComparison.variableId = variableList.get(0).id; //should only contain one variable
            experimentComparison.config = (ObjectNode) mapper.readTree("{" +
                    "          \"maxBaselineDatasets\": 0," +
                    "          \"threshold\": 0.1," +
                    "          \"greaterBetter\": true" +
                    "        }");

            experimentProfile.comparisons = Collections.singletonList(experimentComparison);

            addOrUpdateProfile(test.id, experimentProfile);

            //5. upload some data
            Test finalTest = test;
            Schema finalSchema = schema;
            Consumer<JsonNode> uploadData = (payload) -> uploadRun("$.start", "$.stop", finalTest.name, finalTest.owner,
                    Access.PUBLIC, finalSchema.uri, null, payload);

            uploadData.accept(mapper.readTree(resourceToString("data/experiment-ds1.json")));
            uploadData.accept(mapper.readTree(resourceToString("data/experiment-ds2.json")));
            uploadData.accept(mapper.readTree(resourceToString("data/experiment-ds3.json")));

            //6. run experiments
            RunService.RunsSummary runsSummary = listTestRuns(test.id, false, null, null, "name", SortDirection.Ascending);

            Integer lastRunID = runsSummary.runs.stream().map(run -> run.id).max((Comparator.comparingInt(anInt -> anInt)))
                    .get();

            RunService.RunExtended extendedRun = getRun(lastRunID);

            assertNotNull(extendedRun.datasets);

            Integer maxDataset = Arrays.stream(extendedRun.datasets).max(Comparator.comparingInt(anInt -> anInt)).get();

            List<ExperimentService.ExperimentResult> experimentResults = runExperiments(maxDataset);

            assertNotNull(experimentResults);
            assertFalse(experimentResults.isEmpty());

            jsonRequest().auth().oauth2(getTesterToken())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                    .body("a new description")
                    .post("/api/run/" + lastRunID + "/description")
                    .then()
                    .statusCode(204);

            Response response = jsonRequest().get("/api/test/" + test.id + "/export").then()
                    .statusCode(200).extract().response();

            String export = response.asString();

            TestExport testExport = response.as(TestExport.class);

            assertNotNull(testExport);

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @org.junit.jupiter.api.Test
    public void testAllRunsOrdering() throws IOException {
        String name = "with_meta";
        Test test = createTest(createExampleTest(name));
        createSchema("Foo", "urn:foo");
        createSchema("Bar", "urn:bar");
        createSchema("Q", "urn:q");
        Schema gooSchema = createSchema("Goo", "urn:goo");
        Schema postSchema = createSchema("Post", "uri:Goo-post-function");

        long now = System.currentTimeMillis();
        ObjectNode data = simpleObject("urn:foo", "foo", "xxx");
        ArrayNode metadata = JsonNodeFactory.instance.arrayNode();
        metadata.add(simpleObject("urn:bar", "bar", "yyy"));

        uploadRun(now, data, metadata, test.name);
        now = System.currentTimeMillis();
        uploadRun(now, data, metadata, test.name);

        RunService.RunsSummary runs = jsonRequest()
                .get("/api/run/list?limit=10&page=1&query=$.*")
                .then()
                .statusCode(200)
                .extract()
                .as(RunService.RunsSummary.class);

        assertEquals(2, runs.runs.size());
        assertEquals(name, runs.runs.get(0).testname);
        assertTrue(runs.runs.get(0).start.isAfter(runs.runs.get(1).start));

        runs = jsonRequest()
                .get("/api/run/list?limit=10&page=1&query=$.*&direction=Ascending")
                .then()
                .statusCode(200)
                .extract()
                .as(RunService.RunsSummary.class);
        assertEquals(2, runs.runs.size());
        assertTrue(runs.runs.get(0).start.isBefore(runs.runs.get(1).start));
    }

    @org.junit.jupiter.api.Test
    public void testDatasetsCreation() {
        String name = "with_meta";
        Test test = createTest(createExampleTest(name));
        createSchema("Foo", "urn:foo");
        createSchema("Bar", "urn:bar");

        long now = System.currentTimeMillis();
        ObjectNode data = simpleObject("urn:foo", "foo", "xxx");
        ArrayNode metadata = JsonNodeFactory.instance.arrayNode();
        metadata.add(simpleObject("urn:bar", "bar", "yyy"));

        assertEquals(0, DatasetDAO.count());

        uploadRun(now, data, metadata, test.name);
        now = System.currentTimeMillis();
        uploadRun(now, data, metadata, test.name);

        assertEquals(2, RunDAO.count());
        // datasets properly created
        assertEquals(2, DatasetDAO.count());
    }

    @org.junit.jupiter.api.Test
    public void testUpdateDescriptionPropagation() {
        String name = "with_meta";
        Test test = createTest(createExampleTest(name));
        createSchema("Foo", "urn:foo");
        createSchema("Bar", "urn:bar");

        long now = System.currentTimeMillis();
        ObjectNode data = simpleObject("urn:foo", "foo", "xxx");
        ArrayNode metadata = JsonNodeFactory.instance.arrayNode();
        metadata.add(simpleObject("urn:bar", "bar", "yyy"));

        assertEquals(0, DatasetDAO.count());

        int firstId = uploadRun(now, data, metadata, test.name);
        now = System.currentTimeMillis();
        uploadRun(now, data, metadata, test.name);

        assertEquals(2, RunDAO.count());
        // datasets properly created
        assertEquals(2, DatasetDAO.count());

        // update the description
        jsonRequest().auth().oauth2(getTesterToken())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                .body("a new description")
                .post("/api/run/" + firstId + "/description")
                .then()
                .statusCode(204);

        RunDAO run = RunDAO.findById(firstId);
        assertNotNull(run);
        assertEquals("a new description", run.description);

        DatasetDAO ds = DatasetDAO.find("run.id = ?1", firstId).firstResult();
        assertNotNull(ds);
        assertEquals("a new description", ds.description);
    }

    @org.junit.jupiter.api.Test
    public void testUpdateAccessPropagation() {
        String name = "with_meta";
        Test test = createTest(createExampleTest(name));
        createSchema("Foo", "urn:foo");
        createSchema("Bar", "urn:bar");

        long now = System.currentTimeMillis();
        ObjectNode data = simpleObject("urn:foo", "foo", "xxx");
        ArrayNode metadata = JsonNodeFactory.instance.arrayNode();
        metadata.add(simpleObject("urn:bar", "bar", "yyy"));

        assertEquals(0, DatasetDAO.count());

        int firstId = uploadRun(now, data, metadata, test.name);
        now = System.currentTimeMillis();
        uploadRun(now, data, metadata, test.name);

        assertEquals(2, RunDAO.count());
        // datasets properly created
        assertEquals(2, DatasetDAO.count());

        jsonRequest().auth().oauth2(getTesterToken())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .post("/api/run/" + firstId + "/updateAccess?owner=" + UPLOADER_ROLES[0] + "&access=" + Access.PROTECTED)
                .then()
                .statusCode(204);

        RunDAO run = RunDAO.findById(firstId);
        assertNotNull(run);
        assertEquals(Access.PROTECTED, run.access);

        DatasetDAO ds = DatasetDAO.find("run.id = ?1", firstId).firstResult();
        assertNotNull(ds);
        assertEquals(Access.PROTECTED, ds.access);
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
