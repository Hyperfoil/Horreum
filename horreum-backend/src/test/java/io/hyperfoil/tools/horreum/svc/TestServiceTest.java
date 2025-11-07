package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.hyperfoil.tools.horreum.action.ExperimentResultToMarkdown;
import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.alerting.MissingDataRule;
import io.hyperfoil.tools.horreum.api.alerting.Variable;
import io.hyperfoil.tools.horreum.api.alerting.Watch;
import io.hyperfoil.tools.horreum.api.data.*;
import io.hyperfoil.tools.horreum.api.services.SchemaService;
import io.hyperfoil.tools.horreum.api.services.TestService;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.entity.ExperimentProfileDAO;
import io.hyperfoil.tools.horreum.entity.PersistentLogDAO;
import io.hyperfoil.tools.horreum.entity.alerting.*;
import io.hyperfoil.tools.horreum.entity.data.*;
import io.hyperfoil.tools.horreum.mapper.VariableMapper;
import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.test.*;
import io.quarkus.logging.Log;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.common.mapper.TypeRef;
import io.restassured.response.Response;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
class TestServiceTest extends BaseServiceTest {

    @org.junit.jupiter.api.Test
    void testListTests() {
        int count = 10;
        // all with owner TESTER_ROLES[0];
        createTests(count, "test-");

        TestService.TestQueryResult testsResult = listTests(null, null, null, null, null, null);
        assertEquals(count, testsResult.count);
        assertEquals(count, testsResult.tests.size());

        testsResult = listTests(null, null, 5, 0, null, null);
        assertEquals(count, testsResult.count);
        assertEquals(5, testsResult.tests.size());

        // get all my tests
        testsResult = listTests(null, Roles.MY_ROLES, null, null, null, null);
        assertEquals(count, testsResult.count);
        assertEquals(10, testsResult.tests.size());

        // get my tests for admin user
        testsResult = listTests(getAdminToken(), Roles.MY_ROLES, null, null, null, null);
        assertEquals(count, testsResult.count);
        assertEquals(0, testsResult.tests.size());

        // get all tests for admin user
        testsResult = listTests(getAdminToken(), Roles.ALL_ROLES, null, null, null, null);
        assertEquals(count, testsResult.count);
        assertEquals(10, testsResult.tests.size());
    }

    @org.junit.jupiter.api.Test
    public void testCreateDelete(TestInfo info) throws InterruptedException {

        Test test = createTest(createExampleTest(getTestName(info)));
        try (CloseMe ignored = roleManager.withRoles(Arrays.asList(TESTER_ROLES))) {
            assertNotNull(TestDAO.findById(test.id));
        }

        BlockingQueue<Dataset.EventNew> dsQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);
        int runId = uploadRun("{ \"foo\" : \"bar\" }", test.name);
        assertNotNull(dsQueue.poll(10, TimeUnit.SECONDS));

        jsonRequest().get("/api/test/summary?roles=__my").then().statusCode(200);

        BlockingQueue<Integer> events = serviceMediator.getEventQueue(AsyncEventChannels.RUN_TRASHED, test.id);
        deleteTest(test);
        assertNotNull(events.poll(10, TimeUnit.SECONDS));

        em.clear();
        try (CloseMe ignored = roleManager.withRoles(Arrays.asList(TESTER_ROLES))) {
            assertNull(TestDAO.findById(test.id));
            // There's no constraint between runs and tests; therefore the run is not deleted
            RunDAO run = RunDAO.findById(runId);
            assertNotNull(run);
            assertTrue(run.trashed);

            assertEquals(0, DatasetDAO.count("testid", test.id));
        }
    }

    @org.junit.jupiter.api.Test
    public void testRecalculate(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);

        BlockingQueue<Dataset.EventNew> newDatasetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW,
                test.id);
        final int NUM_DATASETS = 5;
        for (int i = 0; i < NUM_DATASETS; ++i) {
            uploadRun(runWithValue(i, schema), test.name);
            Dataset.EventNew event = newDatasetQueue.poll(10, TimeUnit.SECONDS);
            assertNotNull(event);
            assertFalse(event.isRecalculation);
        }
        List<DatasetDAO> datasets = DatasetDAO.list("testid", test.id);
        assertEquals(NUM_DATASETS, datasets.size());
        int maxId = datasets.stream().mapToInt(ds -> ds.id).max().orElse(0);

        jsonRequest().post("/api/test/" + test.id + "/recalculate").then().statusCode(204);
        TestUtil.eventually(() -> {
            TestService.RecalculationStatus status = jsonRequest().get("/api/test/" + test.id + "/recalculate")
                    .then().statusCode(200).extract().body().as(TestService.RecalculationStatus.class);
            assertEquals(NUM_DATASETS, status.totalRuns);
            return status.finished == status.totalRuns;
        });
        for (int i = 0; i < NUM_DATASETS; ++i) {
            Dataset.EventNew event = newDatasetQueue.poll(10, TimeUnit.SECONDS);
            assertNotNull(event);
            assertTrue(event.datasetId > maxId);
            assertTrue(event.isRecalculation);
        }
        datasets = DatasetDAO.list("testid", test.id);
        assertEquals(NUM_DATASETS, datasets.size());
        datasets.forEach(ds -> {
            assertTrue(ds.id > maxId);
            assertEquals(0, ds.ordinal);
        });
        assertEquals(NUM_DATASETS, datasets.stream().map(ds -> ds.runId).collect(Collectors.toSet()).size());
    }

    @org.junit.jupiter.api.Test
    public void testAddTestAction(TestInfo info) {
        Test test = createTest(createExampleTest(getTestName(info)));

        // look for the TEST_NEW action log for test just created
        List<ActionLog> actionLog = jsonRequest().auth().oauth2(getTesterToken()).queryParam("level", PersistentLogDAO.DEBUG)
                .get("/api/log/action/" + test.id).then().statusCode(200).extract().body().jsonPath().getList(".");
        assertFalse(actionLog.isEmpty());

        addTestHttpAction(test, ActionEvent.RUN_NEW, "https://attacker.com").then().statusCode(400);

        addAllowedSite("https://example.com");

        Action action = addTestHttpAction(test, ActionEvent.RUN_NEW, "https://example.com/foo/bar").then()
                .statusCode(200).extract().body().as(Action.class);
        assertNotNull(action.id);
        assertTrue(action.active);
        action.active = false;
        action.testId = test.id;
        jsonRequest().body(action).put("/api/action").then().statusCode(204);

        deleteTest(test);
    }

    @org.junit.jupiter.api.Test
    public void testImportFromFile() throws JsonProcessingException {
        Path p = new File(getClass().getClassLoader().getResource(".").getPath()).toPath();
        p = p.getParent().getParent().getParent().resolve("infra-legacy/example-data/");

        Test t = new ObjectMapper().readValue(
                readFile(p.resolve("quarkus_quickstart_test_empty.json").toFile()), Test.class);
        assertEquals("foo-team", t.owner);
        t.owner = "foo-team";
        Test t2 = createTest(t);
        assertEquals(t.description, t2.description);
        assertNotEquals(t.id, t2.id);
    }

    @org.junit.jupiter.api.Test
    public void testImportExportWithWipe() throws InterruptedException {
        testImportExport(true);
    }

    @org.junit.jupiter.api.Test
    public void testImportExportWithoutWipe() throws InterruptedException {
        testImportExport(false);
    }

    private void testImportExport(boolean wipe) throws InterruptedException {
        Schema schema = createSchema("Example", "urn:example:1.0");
        Extractor barExtractor = new Extractor();
        barExtractor.name = "bar";
        barExtractor.jsonpath = "$.bar";
        addLabel(schema, "value", "", barExtractor);
        Transformer transformer = createTransformer("Foobar", schema, null, new Extractor("foo", "$.foo", false));

        Test test = createTest(createExampleTest("to-be-exported"));
        addTransformer(test, transformer);
        View view = new View();
        view.name = "Another";
        ViewComponent vc = new ViewComponent();
        vc.labels = JsonNodeFactory.instance.arrayNode().add("foo");
        vc.headerName = "Some foo";
        view.components = Collections.singletonList(vc);
        view.testId = test.id;
        updateView(view);

        addTestHttpAction(test, ActionEvent.RUN_NEW, "http://example.com");
        addTestGithubIssueCommentAction(test, ActionEvent.EXPERIMENT_RESULT_NEW,
                ExperimentResultToMarkdown.NAME, "hyperfoil", "horreum", "123", "super-secret-github-token");

        addChangeDetectionVariable(test, schema.id);
        addMissingDataRule(test, "Let me know", JsonNodeFactory.instance.arrayNode().add("foo"), null,
                (int) TimeUnit.DAYS.toMillis(1));

        addExperimentProfile(test, "Some profile", VariableDAO.<VariableDAO> listAll().get(0));
        addSubscription(test);

        HashMap<String, List<JsonNode>> db = dumpDatabaseContents();

        Response response = jsonRequest().get("/api/test/" + test.id + "/export").then()
                .statusCode(200).extract().response();

        TestExport testExport = response.as(TestExport.class);
        assertEquals(testExport.id, test.id);
        assertEquals(1, testExport.variables.size());

        if (wipe) {
            BlockingQueue<Test> events = serviceMediator.getEventQueue(AsyncEventChannels.TEST_DELETED, test.id);
            deleteTest(test);
            Test deleted = events.poll(10, TimeUnit.SECONDS);
            assertNotNull(deleted);
            assertEquals(test.id, deleted.id);

            TestUtil.eventually(() -> {
                em.clear();
                try (var h = roleManager.withRoles(Collections.singleton(Roles.HORREUM_SYSTEM))) {
                    assertEquals(0, TestDAO.count("id = ?1", test.id));
                    assertEquals(0, ActionDAO.count("testId = ?1", test.id));
                    assertEquals(0, VariableDAO.count("testId = ?1", test.id));
                    assertEquals(0, ChangeDetectionDAO.count());
                    assertEquals(0, MissingDataRuleDAO.count("test.id = ?1", test.id));
                    assertEquals(0, ExperimentProfileDAO.count("test.id = ?1", test.id));
                    assertEquals(0, WatchDAO.count("test.id = ?1", test.id));
                }
            });
        }

        //wiping and inserting with the same ids just results in too much foobar
        if (!wipe) {
            jsonRequest().body(testExport).put("/api/test/import").then().statusCode(200);
            //if we wipe, we actually import a new test and there is no use validating the db
            validateDatabaseContents(db);
            //clean up after us
            deleteTest(test);
        }
    }

    @org.junit.jupiter.api.Test
    public void testImportWithTransformers() {
        Path p = new File(getClass().getClassLoader().getResource(".").getPath()).toPath();
        p = p.getParent().getParent().getParent().resolve("infra-legacy/example-data/");

        String s = readFile(p.resolve("quarkus_sb_schema.json").toFile());
        jsonRequest().body(s).post("/api/schema/import").then().statusCode(201);

        String t = readFile(p.resolve("quarkus_sb_test.json").toFile());
        jsonRequest().body(t).post("/api/test/import").then().statusCode(201);
        TestDAO test = TestDAO.<TestDAO> find("name", "quarkus-spring-boot-comparison").firstResult();
        assertEquals(1, test.transformers.size());

        List<SchemaService.SchemaDescriptor> descriptors = jsonRequest().get("/api/schema/descriptors")
                .then().statusCode(200).extract().body().as(new TypeRef<>() {
                });
        assertEquals("quarkus-sb-compare", descriptors.get(0).name);

        List<ExperimentProfileDAO> experiments = ExperimentProfileDAO.list("test.id", test.id);
        assertEquals(1, experiments.size());
        assertNotNull(experiments.get(0).comparisons.get(0).variable);
    }

    @org.junit.jupiter.api.Test
    public void testExportImportWithExistingDatastore() {
        String datastoreName = "My Datastore";
        int datastoreId = createDatastore(datastoreName);
        Test test = createTest(createExampleTest("to-be-exported", datastoreId));

        Response response = jsonRequest().get("/api/test/" + test.id + "/export").then()
                .statusCode(200).extract().response();

        TestExport export = response.as(TestExport.class);
        assertEquals(test.id, export.id);
        assertNotNull(export.datastore);
        assertEquals(test.datastoreId, export.datastore.id);
        assertEquals(datastoreName, export.datastore.name);

        // re-import the test as new one
        export.id = null;
        export.name = "imported-test";
        jsonRequest().body(export).post("/api/test/import").then().statusCode(201);

        assertEquals(2, TestDAO.count());

        TestDAO imported = TestDAO.<TestDAO> find("name", "imported-test").firstResult();
        assertNotNull(imported);
        assertNotEquals(test.id, imported.id);

        TestExport importedTestExported = jsonRequest().get("/api/test/" + imported.id + "/export").then()
                .statusCode(200).extract().response().as(TestExport.class);
        assertEquals(imported.id, importedTestExported.id);
        assertNotNull(importedTestExported.datastore);
        assertEquals(test.datastoreId, importedTestExported.datastore.id);
        assertEquals(datastoreId, importedTestExported.datastore.id);
        assertEquals(datastoreName, importedTestExported.datastore.name);
    }

    @org.junit.jupiter.api.Test
    public void testExportImportWithNotExistingDatastore() {
        String datastoreName = "My Datastore";
        int datastoreId = createDatastore(datastoreName);
        Test test = createTest(createExampleTest("to-be-exported", datastoreId));

        Response response = jsonRequest().get("/api/test/" + test.id + "/export").then()
                .statusCode(200).extract().response();

        TestExport export = response.as(TestExport.class);
        assertEquals(test.id, export.id);
        assertNotNull(export.datastore);
        assertEquals(test.datastoreId, export.datastore.id);
        assertEquals(datastoreName, export.datastore.name);

        // force to create a new test during the import
        export.id = null;
        export.name = "imported-test";
        // force to create a new datastore
        export.datastore.id = null;
        String datastoreName2 = "My Datastore 2";
        export.datastore.name = datastoreName2;
        jsonRequest().body(export).post("/api/test/import").then().statusCode(201);

        assertEquals(2, TestDAO.count());

        TestDAO imported = TestDAO.<TestDAO> find("name", "imported-test").firstResult();
        assertNotNull(imported);
        assertNotEquals(test.id, imported.id);

        TestExport importedTestExported = jsonRequest().get("/api/test/" + imported.id + "/export").then()
                .statusCode(200).extract().response().as(TestExport.class);
        assertEquals(imported.id, importedTestExported.id);
        assertNotNull(importedTestExported.datastore);
        assertNotNull(importedTestExported.datastore.id);
        assertNotEquals(test.datastoreId, importedTestExported.datastore.id);
        assertEquals(datastoreName2, importedTestExported.datastore.name);
    }

    @org.junit.jupiter.api.Test
    public void testListFingerprints() throws JsonProcessingException {
        List<JsonNode> fps = new ArrayList<>();
        fps.add(mapper.readTree("""
                {
                   "Mode" : "library",
                   "TestName" : "reads10",
                   "ConfigName" : "dist"
                }
                """));

        List<Fingerprints> values = Fingerprints.parse(fps);
        assertEquals(1, values.size());
        assertEquals(3, values.get(0).values.size());
        assertEquals("dist", values.get(0).values.get(2).value);

        fps.add(mapper.readTree("""
                {
                   "tag": "main",
                   "params":  {
                       "storeFirst": false,
                       "numberOfRules": 200,
                       "rulesProviderId": "RulesWithJoinsProvides",
                       "useCanonicalMode": true
                    },
                    "testName": "BaseFromContainer"
                }
                """));
        values = Fingerprints.parse(fps);
        assertEquals(2, values.size());
        assertEquals(3, values.get(0).values.size());
        assertEquals(3, values.get(1).values.size());
        assertEquals(4, values.get(1).values.get(1).children.size());

        //We need the cast on children due to Type Erasure on recursive elements
        assertEquals("storeFirst", ((FingerprintValue<Boolean>) values.get(1).values.get(1).children.get(0)).name);
        assertEquals(false, ((FingerprintValue<Boolean>) values.get(1).values.get(1).children.get(0)).value);
        assertEquals("numberOfRules", ((FingerprintValue<Double>) values.get(1).values.get(1).children.get(1)).name);
        assertEquals(200d, ((FingerprintValue<Double>) values.get(1).values.get(1).children.get(1)).value);
        assertEquals("rulesProviderId", ((FingerprintValue<String>) values.get(1).values.get(1).children.get(2)).name);
        assertEquals("RulesWithJoinsProvides", ((FingerprintValue<String>) values.get(1).values.get(1).children.get(2)).value);
    }

    @org.junit.jupiter.api.Test
    public void testPagination() {
        int count = 50;
        createTests(count, "acme");
        try (CloseMe ignored = roleManager.withRoles(Arrays.asList(TESTER_ROLES))) {
            assertEquals(count, TestDAO.count());
        }
        int limit = 20;
        TestService.TestListing listing = listTestSummary("__my", "", limit, 1, SortDirection.Ascending);
        assertEquals(count, listing.count);
        assertEquals(limit, listing.tests.size());
        assertEquals("acme_00", listing.tests.get(0).name);
        assertEquals("acme_19", listing.tests.get(19).name);
        listing = listTestSummary(null, "*", limit, 1, SortDirection.Ascending);
        assertEquals(count, listing.count);
        assertEquals(limit, listing.tests.size());
        assertEquals("acme_00", listing.tests.get(0).name);
        assertEquals("acme_19", listing.tests.get(19).name);
        listing = listTestSummary(null, "*", limit, 1, SortDirection.Ascending);
        assertEquals(count, listing.count);
        assertEquals(limit, listing.tests.size());
        assertEquals("acme_00", listing.tests.get(0).name);
        assertEquals("acme_19", listing.tests.get(19).name);
        listing = listTestSummary("__all", "*", limit, 1, SortDirection.Ascending);
        assertEquals(count, listing.count);
        assertEquals(limit, listing.tests.size());
        assertEquals("acme_00", listing.tests.get(0).name);
        assertEquals("acme_19", listing.tests.get(19).name);

        listing = listTestSummary("__my", "*", limit, 2, SortDirection.Ascending);
        assertEquals(count, listing.count);
        assertEquals(limit, listing.tests.size());
        assertEquals("acme_20", listing.tests.get(0).name);
        assertEquals("acme_39", listing.tests.get(19).name);

        listing = listTestSummary("__my", "*", limit, 3, SortDirection.Ascending);
        assertEquals(count, listing.count);
        assertEquals(10, listing.tests.size());
        assertEquals("acme_40", listing.tests.get(0).name);
        assertEquals("acme_49", listing.tests.get(9).name);

        listing = listTestSummary("__my", "foo", limit, 1, SortDirection.Ascending);
        assertEquals(0, listing.count);
        assertEquals(0, listing.tests.size());
    }

    @org.junit.jupiter.api.Test
    public void testImportTestWithChangeDetectionVariable() {
        String schema = resourceToString("data/acme_sb_schema.json");
        jsonRequest().body(schema).post("/api/schema/import").then().statusCode(201);
        String test = resourceToString("data/acme_new_variable_test.json");
        Integer importedId = jsonRequest().body(test).post("/api/test/import").then().statusCode(201).extract()
                .as(Integer.class);
        TestExport testExport = jsonRequest().get("/api/test/" + importedId + "/export").then()
                .statusCode(200).extract().as(TestExport.class);
        assertEquals(importedId, testExport.id);
        assertNull(VariableDAO.<VariableDAO> find("name", "Max RSS (v2)").firstResult());

        VariableDAO maxRSS = VariableDAO.<VariableDAO> find("name", "Max RSS").firstResult();
        assertNotNull(maxRSS);
        assertEquals("Max RSS", maxRSS.name);
        Variable var = VariableMapper.from(maxRSS);
        var.id = null;
        var.testId = testExport.id;
        var.name = "Max RSS (v2)";
        var.labels = List.of("Quarkus - JVM - maxRss");
        var.changeDetection = new HashSet<>();

        testExport.variables = List.of(var);

        Integer updatedId = jsonRequest().body(testExport).put("/api/test/import").then().statusCode(200).extract()
                .as(Integer.class);
        assertEquals(importedId, updatedId);

        VariableDAO varDAO = VariableDAO.<VariableDAO> find("name", "Max RSS (v2)").firstResult();
        assertNotNull(varDAO);
    }

    @org.junit.jupiter.api.Test
    public void testImportTestWithExperimentProfile() {
        String schema = resourceToString("data/acme_sb_schema.json");
        jsonRequest().body(schema).post("/api/schema/import").then().statusCode(201);
        String test = resourceToString("data/acme_new_variable_test.json");
        Integer importedId = jsonRequest().body(test).post("/api/test/import").then().statusCode(201).extract()
                .as(Integer.class);
        TestExport testExport = jsonRequest().get("/api/test/" + importedId + "/export").then()
                .statusCode(200).extract().as(TestExport.class);
        assertEquals(importedId, testExport.id);

        VariableDAO maxRSS = VariableDAO.<VariableDAO> find("name", "Max RSS").firstResult();
        assertNotNull(maxRSS);
        assertEquals("Max RSS", maxRSS.name);
        Variable var = VariableMapper.from(maxRSS);
        var.id = null;
        var.testId = testExport.id;
        var.name = "Max RSS (v2)";
        var.labels = List.of("Quarkus - JVM - maxRss");
        var.changeDetection = new HashSet<>();
        testExport.variables = List.of(var);

        ExperimentProfile ep = new ExperimentProfile();
        ep.testId = testExport.id;
        ep.name = "acme Quarkus experiment";
        ArrayNode labelsJSON = JSON_NODE_FACTORY.arrayNode();
        ep.selectorLabels = labelsJSON;
        labelsJSON.add("Quarkus - JVM - maxRss");
        ep.selectorFilter = "value => {return true;}";
        ep.baselineLabels = JSON_NODE_FACTORY.arrayNode();
        ep.baselineFilter = "value => {return true;}";

        ExperimentComparison ec = new ExperimentComparison();
        ec.variableName = var.name;
        ec.model = "relativeDifference";
        ec.config = JSON_NODE_FACTORY.objectNode()
                .put("threshold", 0.1)
                .put("greaterBetter", false)
                .put("maxBaselineDatasets", 0);
        ep.comparisons = List.of(ec);

        ep.extraLabels = JSON_NODE_FACTORY.arrayNode();
        testExport.experiments.add(ep);

        Integer updatedId = jsonRequest().body(testExport).put("/api/test/import").then().statusCode(200).extract()
                .as(Integer.class);
        assertEquals(importedId, updatedId);

        ExperimentProfileDAO epDAO = ExperimentProfileDAO.<ExperimentProfileDAO> find("name", "acme Quarkus experiment")
                .firstResult();
        assertNotNull(epDAO);
        assertEquals(1, epDAO.comparisons.size());
        assertEquals(var.name, epDAO.comparisons.get(0).variable.name);
    }

    @org.junit.jupiter.api.Test
    public void testImportTestWithMissingDataRules() {
        String schema = resourceToString("data/acme_sb_schema.json");
        jsonRequest().body(schema).post("/api/schema/import").then().statusCode(201);
        String test = resourceToString("data/acme_new_variable_test.json");
        Integer importedId = jsonRequest().body(test).post("/api/test/import").then().statusCode(201).extract()
                .as(Integer.class);
        TestExport testExport = jsonRequest().get("/api/test/" + importedId + "/export").then()
                .statusCode(200).extract().as(TestExport.class);
        assertEquals(importedId, testExport.id);

        var msdr = new MissingDataRule();
        msdr.testId = testExport.id;
        msdr.name = "imported missing rule";
        msdr.maxStaleness = 42;
        testExport.missingDataRules = Collections.singletonList(msdr);

        Integer updatedId = jsonRequest().body(testExport).put("/api/test/import").then().statusCode(200).extract()
                .as(Integer.class);
        assertEquals(importedId, updatedId);

        MissingDataRuleDAO mdr = MissingDataRuleDAO.find("name", "imported missing rule").firstResult();
        assertEquals(42, mdr.maxStaleness);
    }

    private void addSubscription(Test test) {
        Watch watch = new Watch();
        watch.testId = test.id;
        watch.users = Arrays.asList("john", "bill");
        watch.teams = Collections.singletonList("dev-team");
        watch.optout = Collections.singletonList("ignore-me");

        jsonRequest().body(watch).post("/api/subscriptions/" + test.id);
    }

    // utility to get list of schemas
    private TestService.TestQueryResult listTests(String token, String roles, Integer limit, Integer page, String sort,
            SortDirection direction) {
        StringBuilder query = new StringBuilder("/api/test/");
        if (roles != null || limit != null || page != null || sort != null || direction != null) {
            query.append("?");

            if (roles != null) {
                query.append("roles=").append(roles).append("&");
            }

            if (limit != null) {
                query.append("limit=").append(limit).append("&");
            }

            if (page != null) {
                query.append("page=").append(page).append("&");
            }

            if (sort != null) {
                query.append("sort=").append(sort).append("&");
            }

            if (direction != null) {
                query.append("direction=").append(direction);
            }
        }
        return jsonRequest()
                .auth()
                .oauth2(token == null ? getTesterToken() : token)
                .get(query.toString())
                .then()
                .statusCode(200)
                .extract()
                .as(TestService.TestQueryResult.class);
    }

    @org.junit.jupiter.api.Test
    void testUpdateLabelsOnRuns() throws IOException, InterruptedException {
        Path p = new File(getClass().getClassLoader().getResource(".").getPath()).toPath();
        p = p.getParent().getParent().getParent().resolve("infra-legacy/example-data/");
        ObjectMapper mapper = new ObjectMapper();
        String s = readFile(p.resolve("quarkus_sb_schema.json").toFile());
        Integer schemaId = jsonRequest().body(s).post("/api/schema/import").then().statusCode(201).extract().as(Integer.class);

        long currentNumberOfLabelValues = LabelValueDAO.count();

        s = readFile(p.resolve("quarkus_sb_compare_ds.json").toFile());
        Integer schemaId2 = jsonRequest().body(s).post("/api/schema/import").then().statusCode(201).extract().as(Integer.class);
        assertTrue(schemaId2 > schemaId);

        String t = readFile(p.resolve("quarkus_sb_test.json").toFile());
        Integer testId = jsonRequest().body(t).post("/api/test/import").then().statusCode(201).extract().as(Integer.class);

        TestDAO test = TestDAO.<TestDAO> find("name", "quarkus-spring-boot-comparison").firstResult();
        assertEquals(1, test.transformers.size());

        //let's get all the labels to make sure they're all there
        List<Label> labels = jsonRequest().get("/api/schema/" + schemaId + "/labels")
                .then().statusCode(200).extract().body().jsonPath().getList(".", Label.class);
        assertEquals(30, labels.size());

        List<Label> labels2 = jsonRequest().get("/api/schema/" + schemaId2 + "/labels")
                .then().statusCode(200).extract().body().jsonPath().getList(".", Label.class);
        assertEquals(11, labels2.size());

        BlockingQueue<Test> newDataPoints = serviceMediator.getEventQueue(AsyncEventChannels.DATAPOINT_NEW, testId);
        BlockingQueue<Test> updates = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_UPDATED_LABELS, testId);
        List<Integer> runIds = new ArrayList<>();
        for (int i = 1; i < 5; i++) {
            for (int j = 1; j < 11; j++) {
                BlockingQueue<Test> events = serviceMediator.getEventQueue(AsyncEventChannels.RUN_NEW, testId);
                Run run = new Run();
                run.testid = testId;
                run.data = mapper.readTree(p.resolve("quarkus_sb_run" + i + ".json").toFile());
                run.start = Instant.parse(run.data.get("timing").get("start").asText()).plusSeconds(j);
                run.stop = Instant.parse(run.data.get("timing").get("stop").asText()).plusSeconds(j);
                run.owner = "foo-team";

                Response response = jsonRequest()
                        .auth()
                        .oauth2(getUploaderToken())
                        .body(run)
                        .post("/api/run/test");
                assertEquals(202, response.statusCode());
                assertEquals(1, response.getBody().as(List.class).size());

                runIds.addAll(response.getBody().as(List.class));
                assertFalse(runIds.isEmpty());
                assertNotNull(events.poll(10, TimeUnit.SECONDS));
            }
        }
        //make sure we've generating datapoints
        assertNotNull(newDataPoints.poll(10, TimeUnit.SECONDS));

        //we should have 200 datasets now in total
        assertEquals(200, DatasetDAO.findAll().count());

        //give Horreum some time to calculate LabelValues
        for (int i = 0; i < 200; i++) {
            assertNotNull(updates.poll(20, TimeUnit.SECONDS));
        }

        Log.info("Number of LabelValues: " + LabelValueDAO.count());
        assertEquals(currentNumberOfLabelValues + 2200, LabelValueDAO.count());

        OptionalInt maxId;
        try (Stream<DataPointDAO> datapoints = DataPointDAO.findAll().stream()) {
            maxId = datapoints.mapToInt(n -> n.id).max();
        }

        //let's update one label
        List<Integer> updatedLabels = jsonRequest().body(List.of(labels2.get(0))).put("/api/schema/" + schemaId2 + "/labels")
                .then().statusCode(200).extract().body().jsonPath().getList(".", Integer.class);
        assertEquals(1, updatedLabels.size());

        //make sure we get all the updates
        for (int i = 0; i < 200; i++) {
            assertNotNull(updates.poll(20, TimeUnit.SECONDS));
        }

        Log.info("Number of LabelValues after 1 label update: " + LabelValueDAO.count());
        maxId = DataPointDAO.<DataPointDAO> streamAll().mapToInt(dp -> dp.id).max();

        //lets update 11 labels
        updatedLabels = jsonRequest().body(labels2).put("/api/schema/" + schemaId2 + "/labels")
                .then().statusCode(200).extract().body().jsonPath().getList(".", Integer.class);
        assertEquals(11, updatedLabels.size());

        Log.info("Waiting after labels update");
        Thread.sleep(20 * 1000);
        assertEquals(maxId.getAsInt() + 800, DataPointDAO.<DataPointDAO> streamAll().mapToInt(dp -> dp.id).max().getAsInt());
    }
}
