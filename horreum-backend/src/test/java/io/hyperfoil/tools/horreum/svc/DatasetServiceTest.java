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
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.*;
import io.hyperfoil.tools.horreum.api.data.Extractor;
import io.hyperfoil.tools.horreum.api.data.QueryResult;
import io.hyperfoil.tools.horreum.api.internal.services.SqlService;
import io.hyperfoil.tools.horreum.api.services.DatasetService;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.entity.data.*;
import io.hyperfoil.tools.horreum.entity.data.ViewComponentDAO;
import io.hyperfoil.tools.horreum.mapper.LabelMapper;
import io.hyperfoil.tools.horreum.mapper.ViewMapper;
import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.response.Response;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class DatasetServiceTest extends BaseServiceTest {
    @Inject
    SqlService sqlService;

    @org.junit.jupiter.api.Test
    public void testDatasetQueryNoSchema() {
        String value = testDatasetQuery("$.value", false, null);
        assertEquals("24", value);
    }

    @org.junit.jupiter.api.Test
    public void testDatasetQueryNoSchemaStrict() {
        String value = testDatasetQuery("$[1].value", false, null);
        assertEquals("42", value);
    }

    @org.junit.jupiter.api.Test
    public void testDatasetQueryNoSchemaArray() {
        String value = testDatasetQuery("$.value", true, null);
        assertEquals("[24, 42]", value);
    }

    @org.junit.jupiter.api.Test
    public void testDatasetQuerySchema() {
        String value = testDatasetQuery("$.value", false, "urn:B");
        assertEquals("42", value);
    }

    @org.junit.jupiter.api.Test
    public void testDatasetQuerySchemaArray() {
        String value = testDatasetQuery("$.value", true, "urn:B");
        assertEquals("[42]", value);
    }

    private String testDatasetQuery(String jsonPath, boolean array, String schemaUri) {
        AtomicReference<String> result = new AtomicReference<>();
        withExampleSchemas(schemas -> result.set(withExampleDataset(createTest(createExampleTest("dummy")),
                createABData(), ds -> {
                    QueryResult queryResult = sqlService.queryDatasetData(ds.id, jsonPath, array, schemaUri);
                    assertTrue(queryResult.valid);
                    return queryResult.value;
                })), "urn:A", "urn:B");
        return result.get();
    }

    @org.junit.jupiter.api.Test
    public void testDatasetLabelsSingle() {
        withExampleSchemas((schemas) -> {
            int labelA = addLabel(schemas[0], "value", null, new Extractor("value", "$.value", false));
            int labelB = addLabel(schemas[1], "value", "v => v + 1", new Extractor("value", "$.value", false));
            List<Label.Value> values = withLabelValues(createABData());
            assertEquals(2, values.size());
            assertEquals(24,
                    values.stream().filter(v -> v.labelId == labelA).map(v -> v.value.numberValue()).findFirst().orElse(null));
            assertEquals(43,
                    values.stream().filter(v -> v.labelId == labelB).map(v -> v.value.numberValue()).findFirst().orElse(null));
        }, "urn:A", "urn:B");
    }

    @org.junit.jupiter.api.Test
    public void testDatasetLabelSingleWithReduceFunctionArray() {
        withExampleSchemas((schemas) -> {
            int labelReduce = addLabel(schemas[0], "sum", "value => { return value.reduce((a,b) => a+b); }",
                    new Extractor("value", "$.samplesArray", true));
            List<Label.Value> values = withLabelValues(createSampleArray());
            assertEquals(30, values.stream().filter(v -> v.labelId == labelReduce).map(v -> v.value.numberValue()).findFirst()
                    .orElse(null));
        }, "urn:A");
    }

    @org.junit.jupiter.api.Test
    public void testDatasetLabelAsyncSingleWithReduceFunctionArray() {
        withExampleSchemas((schemas) -> {
            int labelReduce = addLabel(schemas[0], "sum", "async (value) => { return value.reduce((a,b) => a+b); }",
                    new Extractor("value", "$.samplesArray", true));
            List<Label.Value> values = withLabelValues(createSampleArray());
            assertEquals(30, values.stream().filter(v -> v.labelId == labelReduce).map(v -> v.value.numberValue()).findFirst()
                    .orElse(null));
        }, "urn:A");
    }

    private ArrayNode createXYData() {
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        ObjectNode a = JsonNodeFactory.instance.objectNode();
        ObjectNode b = JsonNodeFactory.instance.objectNode();
        a.put("$schema", "urn:X");
        a.put("a", 1);
        a.put("b", 2);
        b.put("$schema", "urn:Y");
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
            assertEquals(3, values.stream().filter(v -> v.labelId == labelSum).map(v -> v.value.numberValue()).findFirst()
                    .orElse(null));
            assertEquals(1, values.stream().filter(v -> v.labelId == labelSingle).map(v -> v.value.numberValue()).findFirst()
                    .orElse(null));
            assertEquals(JsonNodeFactory.instance.objectNode().put("x", 1).put("y", 2),
                    values.stream().filter(v -> v.labelId == labelObject).map(v -> v.value).findFirst().orElse(null));
            assertEquals(JsonNodeFactory.instance.arrayNode().add(3).add(4),
                    values.stream().filter(v -> v.labelId == labelArray).map(v -> v.value).findFirst().orElse(null));
            assertEquals(7, values.stream().filter(v -> v.labelId == labelReduce).map(v -> v.value.numberValue()).findFirst()
                    .orElse(null));
            assertEquals(42, values.stream().filter(v -> v.labelId == labelNoExtractor).map(v -> v.value.numberValue())
                    .findFirst().orElse(null));

        }, "urn:X", "urn:Y");
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
            int labelMultiArray = addLabel(schemas[0], "MultiArray",
                    "({a, value}) => a === 1 && Array.isArray(value) && value.length === 0",
                    new Extractor("a", "$.a", false), new Extractor("value", "$.thisPathDoesNotExist", true));

            List<Label.Value> values = withLabelValues(createXYData());
            assertEquals(6, values.size());
            Label.Value singleValue = values.stream().filter(v -> v.labelId == labelSingle).findFirst().orElseThrow();
            assertEquals(JsonNodeFactory.instance.nullNode(), singleValue.value);
            BooleanNode trueNode = JsonNodeFactory.instance.booleanNode(true);
            assertEquals(trueNode,
                    values.stream().filter(v -> v.labelId == labelSingleFunc).map(v -> v.value).findFirst().orElse(null));
            assertEquals(trueNode,
                    values.stream().filter(v -> v.labelId == labelSingleArray).map(v -> v.value).findFirst().orElse(null));
            assertEquals(JsonNodeFactory.instance.objectNode().put("a", 1).putNull("value"),
                    values.stream().filter(v -> v.labelId == labelMulti).map(v -> v.value).findFirst().orElse(null));
            assertEquals(trueNode,
                    values.stream().filter(v -> v.labelId == labelMultiFunc).map(v -> v.value).findFirst().orElse(null));
            assertEquals(trueNode,
                    values.stream().filter(v -> v.labelId == labelMultiArray).map(v -> v.value).findFirst().orElse(null));
        }, "urn:X");
    }

    @org.junit.jupiter.api.Test
    public void testDatasetLabelChanged() {
        withExampleSchemas((schemas) -> {
            int labelA = addLabel(schemas[0], "A", null, new Extractor("value", "$.value", false));
            int labelB = addLabel(schemas[1], "B", "v => v + 1", new Extractor("value", "$.value", false));
            int labelC = addLabel(schemas[1], "C", null, new Extractor("value", "$.value", false));
            Test test = createTest(createExampleTest("dummy"));
            BlockingQueue<Dataset.LabelsUpdatedEvent> updateQueue = serviceMediator
                    .getEventQueue(AsyncEventChannels.DATASET_UPDATED_LABELS, test.id);
            withExampleDataset(test, createABData(), ds -> {
                waitForUpdate(updateQueue, ds);
                List<LabelValueDAO> values = LabelValueDAO.<LabelValueDAO> find("datasetId", ds.id).list();
                assertEquals(3, values.size());
                assertEquals(24, values.stream().filter(v -> v.labelId == labelA).map(v -> v.value.numberValue()).findFirst()
                        .orElse(null));
                assertEquals(43, values.stream().filter(v -> v.labelId == labelB).map(v -> v.value.numberValue()).findFirst()
                        .orElse(null));
                assertEquals(42, values.stream().filter(v -> v.labelId == labelC).map(v -> v.value.numberValue()).findFirst()
                        .orElse(null));
                em.clear();

                updateLabel(schemas[0], labelA, "value", null, new Extractor("value", "$.value", true));
                updateLabel(schemas[1], labelB, "value", "({ x, y }) => x + y", new Extractor("x", "$.value", false),
                        new Extractor("y", "$.value", false));
                waitForUpdate(updateQueue, ds);
                waitForUpdate(updateQueue, ds);
                //delete label will cause a recalculation of the labels&datapoints
                deleteLabel(schemas[1], labelC);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                values = LabelValueDAO.<LabelValueDAO> find("datasetId", ds.id).list();
                assertEquals(2, values.size());
                assertEquals(JsonNodeFactory.instance.arrayNode().add(24),
                        values.stream().filter(v -> v.labelId == labelA).map(v -> v.value).findFirst().orElse(null));
                assertEquals(84, values.stream().filter(v -> v.labelId == labelB).map(v -> v.value.numberValue()).findFirst()
                        .orElse(null));
                return null;
            });

        }, "urn:A", "urn:B");
    }

    private List<Label.Value> withLabelValues(ArrayNode data) {
        Test test = createTest(createExampleTest("dummy"));
        BlockingQueue<Dataset.LabelsUpdatedEvent> updateQueue = serviceMediator
                .getEventQueue(AsyncEventChannels.DATASET_UPDATED_LABELS, test.id);
        return withExampleDataset(test, data, ds -> {
            waitForUpdate(updateQueue, ds);
            return LabelValueDAO.<LabelValueDAO> find("datasetId", ds.id).list().stream().map(LabelMapper::fromValue)
                    .collect(Collectors.toList());
        });
    }

    @org.junit.jupiter.api.Test
    public void testSchemaAfterData() throws InterruptedException {
        Test test = createTest(createExampleTest("xxx"));
        BlockingQueue<Dataset.EventNew> dsQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);
        BlockingQueue<Dataset.LabelsUpdatedEvent> labelQueue = serviceMediator
                .getEventQueue(AsyncEventChannels.DATASET_UPDATED_LABELS, test.id);
        JsonNode data = JsonNodeFactory.instance.arrayNode()
                .add(JsonNodeFactory.instance.objectNode().put("$schema", "urn:another"))
                .add(JsonNodeFactory.instance.objectNode().put("$schema", "urn:foobar").put("value", 42));
        int runId = uploadRun(data, test.name);
        Dataset.EventNew firstEvent = dsQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(firstEvent);
        assertEquals(runId, firstEvent.runId);
        //      TestUtil.assertEmptyArray(firstEvent.dataset.data);
        // this update is for no label values - there's no schema
        Dataset.LabelsUpdatedEvent firstUpdate = labelQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(firstUpdate);
        assertEquals(firstEvent.datasetId, firstUpdate.datasetId);

        assertEquals(0, ((Number) em.createNativeQuery("SELECT count(*) FROM dataset_schemas where dataset_id = ?1")
                .setParameter(1, firstEvent.datasetId).getSingleResult()).intValue());
        Schema schema = createSchema("Foobar", "urn:foobar");

        Dataset.EventNew secondEvent = dsQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(secondEvent);
        assertEquals(runId, secondEvent.runId);
        // empty again - we have schema but no labels defined
        Dataset.LabelsUpdatedEvent secondUpdate = labelQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(secondUpdate);
        assertEquals(secondEvent.datasetId, secondUpdate.datasetId);

        @SuppressWarnings("unchecked")
        List<Object[]> ds = em.createNativeQuery("SELECT dataset_id, index FROM dataset_schemas where dataset_id = ?1")
                .setParameter(1, secondEvent.datasetId).getResultList();
        assertEquals(1, ds.size());
        assertEquals(secondEvent.datasetId, ds.get(0)[0]);
        assertEquals(0, ds.get(0)[1]);
        assertEquals(0, ((Number) em.createNativeQuery("SELECT count(*) FROM label_values where dataset_id = ?1")
                .setParameter(1, secondEvent.datasetId).getSingleResult()).intValue());

        addLabel(schema, "value", null, new Extractor("value", "$.value", false));
        // not empty anymore
        Dataset.LabelsUpdatedEvent thirdUpdate = labelQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(thirdUpdate);
        assertEquals(secondEvent.datasetId, thirdUpdate.datasetId);

        List<LabelValueDAO> values = LabelValueDAO.list("datasetId", thirdUpdate.datasetId);
        assertEquals(1, values.size());
        assertEquals(42, values.get(0).value.asInt());
    }

    @org.junit.jupiter.api.Test
    public void testDatasetView() {
        String testname = "dummy";
        log.debugf("Creating new test: %s", testname);
        Test test = createTest(createExampleTest(testname));
        log.debugf("New test created: %s", test.toString());
        Util.withTx(tm, () -> {
            try (CloseMe ignored = roleManager.withRoles(Arrays.asList(TESTER_ROLES))) {
                ViewDAO view = ViewDAO.find("test.id", test.id).firstResult();
                log.debugf("view is null: %b", view == null);
                view.components.clear();
                ViewComponentDAO vc1 = new ViewComponentDAO();
                vc1.view = view;
                vc1.headerName = "X";
                vc1.labels = jsonArray("a");
                view.components.add(vc1);
                ViewComponentDAO vc2 = new ViewComponentDAO();
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
            log.debugf("Waiting for  MessageBusChannels.DATASET_UPDATED_LABELS");
            BlockingQueue<Dataset.LabelsUpdatedEvent> updateQueue = serviceMediator
                    .getEventQueue(AsyncEventChannels.DATASET_UPDATED_LABELS, test.id);
            withExampleDataset(test, createABData(), ds -> {
                waitForUpdate(updateQueue, ds);
                JsonNode datasets = fetchDatasetsByTest(test.id);
                assertEquals(1, datasets.get("total").asInt());
                assertEquals(1, datasets.get("datasets").size());
                JsonNode dsJson = datasets.get("datasets").get(0);
                assertEquals(2, dsJson.get("schemas").size());
                JsonNode view = dsJson.get("view");
                assertEquals(2, view.size());
                JsonNode vc1 = StreamSupport.stream(view.spliterator(), false).filter(vc -> vc.size() == 1).findFirst()
                        .orElseThrow();
                assertEquals(24, vc1.get("a").asInt());
                JsonNode vc2 = StreamSupport.stream(view.spliterator(), false).filter(vc -> vc.size() == 2).findFirst()
                        .orElseThrow();
                assertEquals(24, vc2.get("a").asInt());
                assertEquals(42, vc2.get("b").asInt());

                String labelIds = (String) em
                        .createNativeQuery("SELECT to_json(label_ids)::text FROM dataset_view WHERE dataset_id = ?1")
                        .setParameter(1, ds.id).getSingleResult();
                Set<Integer> ids = new HashSet<>();
                StreamSupport.stream(Util.toJsonNode(labelIds).spliterator(), false).mapToInt(JsonNode::asInt)
                        .forEach(ids::add);
                assertEquals(2, ids.size());
                assertTrue(ids.contains(labelA));
                assertTrue(ids.contains(labelB));

                // there must be only one result here!
                ViewDAO dbView = (ViewDAO) ViewDAO.findAll().list().get(0);
                assertEquals(2, dbView.components.size());

                View updateView = ViewMapper.from(dbView);
                ArrayNode vcLabels = (ArrayNode) updateView.components.stream().filter(vc -> vc.labels.size() == 1).findFirst()
                        .orElseThrow().labels;
                vcLabels.add("b");

                Response response = jsonRequest()
                        .auth()
                        .oauth2(getTesterToken())
                        .body(updateView)
                        .post("/api/ui/view");
                assertEquals(200, response.getStatusCode());

                JsonNode updated = fetchDatasetsByTest(test.id);
                JsonNode updatedView = updated.get("datasets").get(0).get("view");
                assertEquals(2, updatedView.size());
                assertTrue(StreamSupport.stream(updatedView.spliterator(), false).allMatch(vc -> vc.size() == 2),
                        updated.toPrettyString());

                return null;
            });
        }, "urn:A", "urn:B");
    }

    private JsonNode fetchDatasetsByTest(int testId) {
        JsonNode datasets = Util.toJsonNode(
                jsonRequest().get("/api/dataset/list/" + testId).then().statusCode(200).extract().body().asString());
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
        a.put("$schema", "urn:A");
        a.put("value", 24);
        b.put("$schema", "urn:B");
        b.put("value", 42);
        data.add(a).add(b);
        return data;
    }

    private ArrayNode createSampleArray() {
        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        ObjectNode a = JsonNodeFactory.instance.objectNode();
        a.put("$schema", "urn:A");
        ArrayNode array = a.arrayNode();
        array.add(2).add(4).add(6).add(8).add(10);
        a.putIfAbsent("samplesArray", array);
        data.add(a);
        return data;
    }

    private void waitForUpdate(BlockingQueue<Dataset.LabelsUpdatedEvent> updateQueue, Dataset ds) {
        try {
            Dataset.LabelsUpdatedEvent event = updateQueue.poll(10, TimeUnit.SECONDS);
            assertNotNull(event);
            assertEquals(ds.id, event.datasetId);
        } catch (InterruptedException e) {
            fail(e);
        }
    }

    @org.junit.jupiter.api.Test
    public void testEverythingPrivate() throws InterruptedException {
        Schema schema = new Schema();
        schema.owner = TESTER_ROLES[0];
        schema.access = Access.PRIVATE;
        schema.name = "private-schema";
        schema.uri = "urn:private";
        addOrUpdateSchema(schema);
        postLabel(schema, "value", null, l -> l.access = Access.PRIVATE, new Extractor("value", "$.value", false));

        Test test = createExampleTest("private-test");
        test.access = Access.PRIVATE;
        test = createTest(test);
        int testId = test.id;

        BlockingQueue<Dataset.LabelsUpdatedEvent> updateQueue = serviceMediator
                .getEventQueue(AsyncEventChannels.DATASET_UPDATED_LABELS, testId);
        long timestamp = System.currentTimeMillis();
        uploadRun(timestamp, timestamp,
                JsonNodeFactory.instance.objectNode().put("$schema", schema.uri).put("value", 42),
                test.name, TESTER_ROLES[0], Access.PRIVATE);

        Dataset.LabelsUpdatedEvent event = updateQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(event);

        try (CloseMe ignored = roleManager.withRoles(Arrays.asList(TESTER_ROLES))) {
            List<LabelValueDAO> labels = LabelValueDAO.<LabelValueDAO> find("datasetId", event.datasetId).list();
            assertEquals(1, labels.size());
        }
    }

    @org.junit.jupiter.api.Test
    public void testDatasetListOrdering() {
        String testname = "road-runner";
        Test test = createTest(createExampleTest(testname));
        BlockingQueue<Dataset.LabelsUpdatedEvent> updateQueue = serviceMediator
                .getEventQueue(AsyncEventChannels.DATASET_UPDATED_LABELS, test.id);
        long timestamp = System.currentTimeMillis();
        int initialRunID = uploadRun(timestamp, timestamp,
                JsonNodeFactory.instance.objectNode(),
                test.name);
        timestamp = System.currentTimeMillis();
        int latterRunID = uploadRun(timestamp, timestamp,
                JsonNodeFactory.instance.objectNode(),
                test.name);
        DatasetService.DatasetList datasetsList = listTestDatasets(test.id, null);
        assertEquals(2, datasetsList.datasets.size());
        assertEquals(0, datasetsList.datasets.get(0).ordinal);
        assertEquals(initialRunID, datasetsList.datasets.get(1).runId);
        assertEquals(latterRunID, datasetsList.datasets.get(0).runId);
        datasetsList = null;
        datasetsList = listTestDatasets(test.id, SortDirection.Ascending);
        assertEquals(2, datasetsList.datasets.size());
        assertEquals(initialRunID, datasetsList.datasets.get(0).runId);
        assertEquals(latterRunID, datasetsList.datasets.get(1).runId);
    }
}
