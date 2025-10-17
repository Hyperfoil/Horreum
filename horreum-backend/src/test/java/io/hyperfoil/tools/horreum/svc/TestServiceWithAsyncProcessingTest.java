package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.groovy.util.Maps;
import org.hibernate.query.NativeQuery;
import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.data.Dataset;
import io.hyperfoil.tools.horreum.api.data.ExportedLabelValues;
import io.hyperfoil.tools.horreum.api.data.Extractor;
import io.hyperfoil.tools.horreum.api.data.Schema;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.api.data.View;
import io.hyperfoil.tools.horreum.api.data.ViewComponent;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.hyperfoil.tools.horreum.test.TestUtil;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.common.mapper.TypeRef;

/**
 * Note that for most of the labelValues tests we are explicitly
 * calling {@link #recalculateDatasetForRun} so that the label values
 * computation is also performed sync, and we are sure the data will be
 * populated before the checks are performed
 */
@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
class TestServiceWithAsyncProcessingTest extends BaseServiceTest {

    @org.junit.jupiter.api.Test
    public void testUpdateView(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);

        BlockingQueue<Dataset.EventNew> newDatasetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW,
                test.id);
        uploadRun(runWithValue(42, schema), test.name);
        Dataset.EventNew event = newDatasetQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(event);

        ViewComponent vc = new ViewComponent();
        vc.headerName = "Foobar";
        vc.labels = JsonNodeFactory.instance.arrayNode().add("value");
        List<View> views = getViews(test.id);
        View defaultView = views.stream().filter(v -> "Default".equals(v.name)).findFirst().orElseThrow();
        defaultView.components.add(vc);
        defaultView.testId = test.id;
        updateView(defaultView);

        TestUtil.eventually(() -> {
            em.clear();
            @SuppressWarnings("unchecked")
            List<JsonNode> list = em.createNativeQuery(
                    "SELECT value FROM dataset_view WHERE dataset_id = ?1 AND view_id = ?2")
                    .setParameter(1, event.datasetId).setParameter(2, defaultView.id)
                    .unwrap(NativeQuery.class).addScalar("value", JsonBinaryType.INSTANCE)
                    .getResultList();
            return !list.isEmpty() && !list.get(0).isEmpty();
        });
    }

    @org.junit.jupiter.api.Test
    public void testLabelValues(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);

        BlockingQueue<Dataset.LabelsUpdatedEvent> newDatasetQueue = serviceMediator
                .getEventQueue(AsyncEventChannels.DATASET_UPDATED_LABELS, test.id);
        uploadRun(runWithValue(42, schema), test.name);
        uploadRun(JsonNodeFactory.instance.objectNode(), test.name);
        assertNotNull(newDatasetQueue.poll(10, TimeUnit.SECONDS));
        assertNotNull(newDatasetQueue.poll(10, TimeUnit.SECONDS));

        List<ExportedLabelValues> values = jsonRequest().get("/api/test/" + test.id + "/labelValues").then().statusCode(200)
                .extract().body().as(new TypeRef<>() {
                });
        assertNotNull(values);
        assertFalse(values.isEmpty());
        assertEquals(2, values.size());
        assertTrue(values.get(1).values.containsKey("value"));
    }

    @org.junit.jupiter.api.Test
    public void testFilterLabelValues(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));

        String name = info.getTestClass().map(Class::getName).orElse("<unknown>") + "." + info.getDisplayName();
        Schema schema = createSchema(name, uriForTest(info, "1.0"));

        addLabel(schema, "filter-1", null, new Extractor("filter", "$.filter1", false));
        addLabel(schema, "filter-2", null, new Extractor("filter", "$.filter2", false));

        BlockingQueue<Dataset.LabelsUpdatedEvent> newDatasetQueue = serviceMediator
                .getEventQueue(AsyncEventChannels.DATASET_UPDATED_LABELS, test.id);
        ObjectNode run;

        run = runWithValue(42, schema);
        run.put("filter1", "foo");
        run.put("filter2", "bar");
        uploadRun(run, test.name);

        run = runWithValue(43, schema);
        run.put("filter1", "foo");
        run.put("filter2", "bar");
        uploadRun(run, test.name);

        run = runWithValue(44, schema);
        run.put("filter1", "biz");
        run.put("filter2", "bar");
        uploadRun(run, test.name);

        run = runWithValue(45, schema);
        run.put("filter1", "foo");
        run.put("filter2", "baz");
        uploadRun(run, test.name);

        for (int i = 0; i < 4; i++) {
            assertNotNull(newDatasetQueue.poll(10, TimeUnit.SECONDS));
        }

        List<ObjectNode> values = jsonRequest().get("/api/test/" + test.id + "/filteringLabelValues").then().statusCode(200)
                .extract().body().as(new TypeRef<>() {
                });
        assertNotNull(values);
        assertFalse(values.isEmpty());
        assertEquals(3, values.size());
        assertNotNull(values.stream()
                .filter(node -> node.get("filter-1").asText().equals("foo") && node.get("filter-2").asText().equals("bar"))
                .findAny().orElse(null));
        assertNotNull(values.stream()
                .filter(node -> node.get("filter-1").asText().equals("biz") && node.get("filter-2").asText().equals("bar"))
                .findAny().orElse(null));
        assertNotNull(values.stream()
                .filter(node -> node.get("filter-1").asText().equals("foo") && node.get("filter-2").asText().equals("baz"))
                .findAny().orElse(null));

    }

    @org.junit.jupiter.api.Test
    public void testFilteringLabelValues(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));

        String name = info.getTestClass().map(Class::getName).orElse("<unknown>") + "." + info.getDisplayName();
        Schema schema = createSchema(name, uriForTest(info, "1.0"));

        addLabel(schema, "filter-1", null, new Extractor("filter", "$.filter1", false));
        addLabel(schema, "filter-2", null, new Extractor("filter", "$.filter2", false));

        BlockingQueue<Dataset.LabelsUpdatedEvent> newDatasetQueue = serviceMediator
                .getEventQueue(AsyncEventChannels.DATASET_UPDATED_LABELS, test.id);
        ObjectNode run;

        run = runWithValue(42, schema);
        run.put("filter1", "foo");
        run.put("filter2", "bar");
        uploadRun(run, test.name);

        run = runWithValue(43, schema);
        run.put("filter1", "foo");
        run.put("filter2", "bar");
        uploadRun(run, test.name);

        run = runWithValue(44, schema);
        run.put("filter1", "biz");
        run.put("filter2", "bar");
        uploadRun(run, test.name);

        run = runWithValue(45, schema);
        run.put("filter1", "foo");
        run.put("filter2", "baz");
        uploadRun(run, test.name);

        for (int i = 0; i < 4; i++) {
            assertNotNull(newDatasetQueue.poll(10, TimeUnit.SECONDS));
        }

        Map<String, String[]> values = jsonRequest().get("/api/test/" + test.id + "/filteringLabels").then().statusCode(200)
                .extract().body().as(new TypeRef<>() {
                });

        assertNotNull(values);
        assertFalse(values.isEmpty());
        // two filtering labels
        assertEquals(2, values.size());
        assertTrue(Arrays.asList(values.get("filter-1")).contains("foo"));
        assertTrue(Arrays.asList(values.get("filter-1")).contains("biz"));
        assertEquals(2, values.get("filter-1").length);
        assertTrue(Arrays.asList(values.get("filter-2")).contains("bar"));
        assertTrue(Arrays.asList(values.get("filter-2")).contains("baz"));
        assertEquals(2, values.get("filter-2").length);
    }

    @org.junit.jupiter.api.Test
    public void labelValuesIncludeExcluded() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, true);

        JsonNode response = jsonRequest()
                .get("/api/test/" + t.id + "/labelValues?include=labelFoo&exclude=labelFoo")
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
    public void labelValuesWithTimestampAfterFilter() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        long stop = System.currentTimeMillis();
        long start = System.currentTimeMillis();
        long delta = 5000; // 5 seconds
        Integer runId = uploadRun(start, stop, "{ \"foo\": 1, \"bar\": \"uno\"}", t.name, "urn:foo",
                jakarta.ws.rs.core.Response.Status.ACCEPTED.getStatusCode()).get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun(start + delta, stop, "{ \"foo\": 2, \"bar\": \"dos\"}", t.name, "urn:foo",
                jakarta.ws.rs.core.Response.Status.ACCEPTED.getStatusCode()).get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun(start + delta, stop, "{ \"foo\": 3, \"bar\": \"tres\"}", t.name, "urn:foo",
                jakarta.ws.rs.core.Response.Status.ACCEPTED.getStatusCode()).get(0);
        recalculateDatasetForRun(runId);

        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                // keep only those runs that started after (start+delta-1)
                .queryParam("after", Long.toString(start + delta - 1))
                .get("/api/test/" + t.id + "/labelValues")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(2, arrayResponse.size(), "unexpected number of responses " + response);
        JsonNode first = arrayResponse.get(0);
        assertTrue(first.has("values"), first.toString());
        JsonNode values = first.get("values");
        assertTrue(values.has("labelBar"), values.toString());
        assertEquals(JsonNodeType.STRING, values.get("labelBar").getNodeType());
        JsonNode second = arrayResponse.get(1);
        assertTrue(first.has("values"), second.toString());
        JsonNode secondValues = second.get("values");
        assertTrue(secondValues.has("labelBar"), secondValues.toString());
        assertEquals(JsonNodeType.STRING, secondValues.get("labelBar").getNodeType());

        List<String> labelBarValues = List.of(values.get("labelBar").asText(), secondValues.get("labelBar").asText());
        assertTrue(labelBarValues.contains("dos"), labelBarValues.toString());
        assertTrue(labelBarValues.contains("tres"), labelBarValues.toString());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesWithISOAfterFilter() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        long stop = System.currentTimeMillis();
        Integer runId = uploadRun(Util.toInstant("2024-10-06T20:20:32.183Z").toEpochMilli(), stop,
                "{ \"foo\": 1, \"bar\": \"uno\"}", t.name,
                "urn:foo", jakarta.ws.rs.core.Response.Status.ACCEPTED.getStatusCode()).get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun(Util.toInstant("2024-10-06T20:20:32.183Z").toEpochMilli(), stop, "{ \"foo\": 2, \"bar\": \"dos\"}",
                t.name,
                "urn:foo", jakarta.ws.rs.core.Response.Status.ACCEPTED.getStatusCode()).get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun(Util.toInstant("2024-10-09T20:20:32.183Z").toEpochMilli(), stop, "{ \"foo\": 3, \"bar\": \"tres\"}",
                t.name,
                "urn:foo", jakarta.ws.rs.core.Response.Status.ACCEPTED.getStatusCode()).get(0);
        recalculateDatasetForRun(runId);

        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                // keep only those runs that started after (start+delta-1)
                .queryParam("after", "2024-10-07T20:20:32.183Z")
                .queryParam("filter", Maps.of("labelBar", Arrays.asList("none", "tres")))
                .queryParam("multiFilter", true)
                .get("/api/test/" + t.id + "/labelValues")
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
        assertEquals("tres", values.get("labelBar").asText());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterWithJsonpath() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        int runId = uploadRun("{ \"foo\": 1, \"bar\": \"uno\"}", t.name, "urn:foo").get(0);
        runId = uploadRun("{ \"foo\": 2, \"bar\": \"dos\"}", t.name, "urn:foo").get(0);
        runId = uploadRun("{ \"foo\": 3, \"bar\": \"tres\"}", t.name, "urn:foo").get(0);
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                .queryParam("filter", "$.labelFoo ? (@ < 2)")
                .get("/api/test/" + t.id + "/labelValues")
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
        assertEquals("uno", values.get("labelBar").asText());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterWithInvalidJsonpath() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        int runId = uploadRun("{ \"foo\": 1, \"bar\": \"uno\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun("{ \"foo\": 2, \"bar\": \"dos\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun("{ \"foo\": 3, \"bar\": \"tres\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        jsonRequest()
                .urlEncodingEnabled(true)
                .queryParam("filter", "$..name")
                .get("/api/test/" + t.id + "/labelValues")
                .then()
                .statusCode(400);
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterWithObject() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        int runId = uploadRun("{ \"foo\": 1, \"bar\": \"uno\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun("{ \"foo\": 2, \"bar\": \"dos\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun("{ \"foo\": 3, \"bar\": \"tres\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                .queryParam("filter", Maps.of("labelBar", "uno", "labelFoo", 1))
                .queryParam("multiFilter", false)
                .get("/api/test/" + t.id + "/labelValues")
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
        assertEquals("uno", values.get("labelBar").asText());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterWithObject2() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        int runId = uploadRun("{ \"foo\": 1, \"bar\": \"uno\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun("{ \"foo\": 2, \"bar\": \"dos\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun("{ \"foo\": 3, \"bar\": \"tres\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);

        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                .queryParam("filter", Maps.of("labelBar", "tres", "labelFoo", 3))
                .queryParam("multiFilter", false)
                .get("/api/test/" + t.id + "/labelValues")
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
        assertEquals("tres", values.get("labelBar").asText());
        assertTrue(values.has("labelFoo"), values.toString());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterWithObjectNoMatch() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        int runId = uploadRun("{ \"foo\": 1, \"bar\": \"uno\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun("{ \"foo\": 2, \"bar\": \"dos\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun("{ \"foo\": 3, \"bar\": \"tres\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                // no runs match both conditions
                .queryParam("filter", Maps.of("labelBar", "uno", "labelFoo", "3"))
                .queryParam("multiFilter", false)
                .get("/api/test/" + t.id + "/labelValues")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(0, arrayResponse.size(), "unexpected number of responses " + response);
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterMultiSelectMultipleValues() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        Integer runId = uploadRun("{ \"foo\": 1, \"bar\": \"uno\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun("{ \"foo\": 2, \"bar\": \"dos\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun("{ \"foo\": 3, \"bar\": \"tres\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                .queryParam("filter", Maps.of("labelBar", Arrays.asList("uno", "tres")))
                .queryParam("multiFilter", true)
                .get("/api/test/" + t.id + "/labelValues")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(2, arrayResponse.size(), "unexpected number of responses " + response);
        JsonNode first = arrayResponse.get(0);
        assertTrue(first.has("values"), first.toString());
        JsonNode values = first.get("values");
        assertTrue(values.has("labelBar"), values.toString());
        assertEquals(JsonNodeType.STRING, values.get("labelBar").getNodeType());
        JsonNode second = arrayResponse.get(1);
        assertTrue(first.has("values"), second.toString());
        JsonNode secondValues = second.get("values");
        assertTrue(secondValues.has("labelBar"), secondValues.toString());
        assertEquals(JsonNodeType.STRING, secondValues.get("labelBar").getNodeType());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterMultiSelectStrings() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        uploadRun("{ \"foo\": 1, \"bar\": \"uno\"}", t.name, "urn:foo");
        uploadRun("{ \"foo\": 2, \"bar\": \"dos\"}", t.name, "urn:foo");
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                .queryParam("filter", Maps.of("labelBar", Arrays.asList("uno", 30)))
                .queryParam("multiFilter", true)
                .get("/api/test/" + t.id + "/labelValues")
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
        assertEquals("uno", values.get("labelBar").asText());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterMultiSelectNumber() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        uploadRun("{ \"foo\": 1, \"bar\": 10}", t.name, "urn:foo");
        uploadRun("{ \"foo\": 2, \"bar\": 20}", t.name, "urn:foo");
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                .queryParam("filter", Maps.of("labelBar", Arrays.asList(10, 30)))
                .queryParam("multiFilter", true)
                .get("/api/test/" + t.id + "/labelValues")
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
        assertEquals(JsonNodeType.NUMBER, values.get("labelBar").getNodeType());
        assertEquals("10", values.get("labelBar").toString());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterMultiSelectBoolean() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        uploadRun("{ \"foo\": 1, \"bar\": true}", t.name, "urn:foo");
        uploadRun("{ \"foo\": 2, \"bar\": 20}", t.name, "urn:foo");
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                .queryParam("filter", Maps.of("labelBar", Arrays.asList(true, 30)))
                .queryParam("multiFilter", true)
                .get("/api/test/" + t.id + "/labelValues")
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
        assertEquals(JsonNodeType.BOOLEAN, values.get("labelBar").getNodeType());
        assertEquals("true", values.get("labelBar").toString());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesIncludeTwoParams() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, true);

        JsonNode response = jsonRequest()
                .get("/api/test/" + t.id + "/labelValues?include=labelFoo&include=labelBar")
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
        labelValuesSetup(t, true);

        JsonNode response = jsonRequest()
                .get("/api/test/" + t.id + "/labelValues?include=labelFoo,labelBar")
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
        labelValuesSetup(t, true);

        JsonNode response = jsonRequest()
                .get("/api/test/" + t.id + "/labelValues?include=labelFoo")
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
        labelValuesSetup(t, true);

        JsonNode response = jsonRequest()
                .get("/api/test/" + t.id + "/labelValues?exclude=labelFoo")
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
    public void labelValuesFilterWithLimitSingle() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        int runId = uploadRun("{ \"foo\": 1, \"bar\": \"uno\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun("{ \"foo\": 2, \"bar\": \"dos\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun("{ \"foo\": 3, \"bar\": \"tres\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                .queryParam("limit", 1)
                .get("/api/test/" + t.id + "/labelValues")
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
        assertTrue(values.has("labelFoo"), values.toString());
        assertTrue(values.has("labelBar"), values.toString());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterWithLimitSingleSecondPage() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        int runId = uploadRun("{ \"foo\": 1, \"bar\": \"uno\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun("{ \"foo\": 2, \"bar\": \"dos\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun("{ \"foo\": 3, \"bar\": \"tres\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                .queryParam("limit", 1)
                .queryParam("page", 1)
                .get("/api/test/" + t.id + "/labelValues")
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
        assertTrue(values.has("labelFoo"), values.toString());
        assertTrue(values.has("labelBar"), values.toString());
        assertEquals(2, values.get("labelFoo").asInt());
    }

    @org.junit.jupiter.api.Test
    public void labelValuesFilterWithLimitMultiple() {
        Test t = createTest(createExampleTest("my-test"));
        labelValuesSetup(t, false);
        int runId = uploadRun("{ \"foo\": 1, \"bar\": \"uno\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun("{ \"foo\": 2, \"bar\": \"dos\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        runId = uploadRun("{ \"foo\": 3, \"bar\": \"tres\"}", t.name, "urn:foo").get(0);
        recalculateDatasetForRun(runId);
        JsonNode response = jsonRequest()
                .urlEncodingEnabled(true)
                .queryParam("limit", 2)
                .queryParam("page", 0)
                .get("/api/test/" + t.id + "/labelValues")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(JsonNode.class);
        assertInstanceOf(ArrayNode.class, response);
        ArrayNode arrayResponse = (ArrayNode) response;
        assertEquals(2, arrayResponse.size(), "unexpected number of responses " + response);
        JsonNode first = arrayResponse.get(0);
        assertTrue(first.has("values"), first.toString());
        JsonNode values = first.get("values");
        assertTrue(values.has("labelFoo"), values.toString());
        assertTrue(values.has("labelBar"), values.toString());
        values = arrayResponse.get(1).get("values");
        assertTrue(values.has("labelFoo"), values.toString());
        assertTrue(values.has("labelBar"), values.toString());
    }
}
