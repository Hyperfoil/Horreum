package io.hyperfoil.tools.horreum.svc;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionManager;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import org.hibernate.query.NativeQuery;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.action.GitHubIssueCommentAction;
import io.hyperfoil.tools.horreum.action.HttpAction;
import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.alerting.ChangeDetection;
import io.hyperfoil.tools.horreum.api.alerting.DataPoint;
import io.hyperfoil.tools.horreum.api.alerting.MissingDataRule;
import io.hyperfoil.tools.horreum.api.alerting.Variable;
import io.hyperfoil.tools.horreum.api.data.*;
import io.hyperfoil.tools.horreum.api.data.Extractor;
import io.hyperfoil.tools.horreum.api.data.changeDetection.ChangeDetectionModelType;
import io.hyperfoil.tools.horreum.api.internal.services.AlertingService;
import io.hyperfoil.tools.horreum.api.report.ReportComponent;
import io.hyperfoil.tools.horreum.api.report.TableReportConfig;
import io.hyperfoil.tools.horreum.api.services.DatasetService;
import io.hyperfoil.tools.horreum.api.services.ExperimentService;
import io.hyperfoil.tools.horreum.api.services.RunService;
import io.hyperfoil.tools.horreum.api.services.TestService;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.bus.BlockingTaskDispatcher;
import io.hyperfoil.tools.horreum.entity.ExperimentProfileDAO;
import io.hyperfoil.tools.horreum.entity.FingerprintDAO;
import io.hyperfoil.tools.horreum.entity.alerting.*;
import io.hyperfoil.tools.horreum.entity.data.*;
import io.hyperfoil.tools.horreum.experiment.RelativeDifferenceExperimentModel;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.hyperfoil.tools.horreum.mapper.DatasetMapper;
import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.server.RoleManager;
import io.quarkus.arc.impl.ParameterizedTypeImpl;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.smallrye.jwt.build.Jwt;

public class BaseServiceTest {
    protected static final String SCHEMA = "urn:comparison";
    static final String[] UPLOADER_ROLES = { "foo-team", "foo-uploader", "uploader" };
    public static final String[] TESTER_ROLES = { "foo-team", "foo-tester", "tester", "viewer" };
    static final List<String> SYSTEM_ROLES = Collections.singletonList(Roles.HORREUM_SYSTEM);
    private static String UPLOADER_TOKEN;
    private static String TESTER_TOKEN;
    private static String ADMIN_TOKEN;
    protected static JsonNodeFactory JSON_NODE_FACTORY = JsonNodeFactory.instance;

    int lastAddedLabelId;

    protected final Logger log = Logger.getLogger(getClass());

    @Inject
    protected EntityManager em;

    @Inject
    TransactionManager tm;

    @Inject
    protected RoleManager roleManager;

    @Inject
    BlockingTaskDispatcher messageBus;

    @Inject
    ObjectMapper mapper;

    @Inject
    ServiceMediator serviceMediator;

    List<Runnable> afterMethodCleanup = new ArrayList<>();

    protected String getUploaderToken() {
        synchronized (BaseServiceTest.class) {
            if (UPLOADER_TOKEN == null) {
                UPLOADER_TOKEN = BaseServiceTest.getAccessToken("alice", UPLOADER_ROLES);
            }
            return UPLOADER_TOKEN;
        }
    }

    protected String getTesterToken() {
        synchronized (BaseServiceTest.class) {
            if (TESTER_TOKEN == null) {
                TESTER_TOKEN = BaseServiceTest.getAccessToken("alice", TESTER_ROLES);
            }
            return TESTER_TOKEN;
        }
    }

    protected String getAdminToken() {
        synchronized (BaseServiceTest.class) {
            if (ADMIN_TOKEN == null) {
                ADMIN_TOKEN = BaseServiceTest.getAccessToken("admin", "admin");
            }
            return ADMIN_TOKEN;
        }
    }

    protected static ObjectNode runWithValue(double value) {
        return runWithValue(value, null);
    }

    protected static ObjectNode runWithValue(double value, Schema schema) {
        ObjectNode runJson = JsonNodeFactory.instance.objectNode();
        if (schema != null)
            runJson.put("$schema", schema.uri);
        runJson.put("value", value);
        ArrayNode values = JsonNodeFactory.instance.arrayNode();
        values.add(++value);
        values.add(++value);
        values.add(++value);
        runJson.set("values", values);
        return runJson;
    }

    protected static ObjectNode runWithValueSchemas(double value, Schema... schemas) {
        ObjectNode root = null;
        for (Schema s : schemas) {
            ObjectNode n = runWithValue(value, s);
            if (root == null) {
                root = n;
            } else {
                root.set("field_" + s.name, n);
            }
        }
        return root;
    }

    @BeforeEach
    public void beforeMethod(TestInfo info) {
        log.debugf("Starting test %s.%s", info.getTestClass().map(Class::getSimpleName).orElse("<unknown>"),
                info.getDisplayName());
    }

    @AfterEach
    public void afterMethod(TestInfo info) {
        log.debugf("Completed test %s.%s", info.getTestClass().map(Class::getSimpleName).orElse("<unknown>"),
                info.getDisplayName());
        dropAllViewsAndTests();
        afterMethodCleanup.forEach(Runnable::run);
        afterMethodCleanup.clear();
        log.debugf("Finished cleanup of test %s.%s", info.getTestClass().map(Class::getSimpleName).orElse("<unknown>"),
                info.getDisplayName());
    }

    protected void dropAllViewsAndTests() {
        Util.withTx(tm, () -> {
            try (CloseMe ignored = roleManager
                    .withRoles(Stream.concat(Stream.of(TESTER_ROLES), Stream.of(Roles.HORREUM_SYSTEM, Roles.ADMIN))
                            .collect(Collectors.toList()))) {
                ViewComponentDAO.deleteAll();
                ViewDAO.deleteAll();

                em.createNativeQuery("DELETE FROM test_transformers").executeUpdate();
                em.createNativeQuery("DELETE FROM transformer_extractors").executeUpdate();
                em.createNativeQuery("DELETE FROM experiment_comparisons").executeUpdate();
                TransformerDAO.deleteAll();
                TestDAO.deleteAll();
                ChangeDAO.deleteAll();
                DataPointDAO.deleteAll();
                ChangeDetectionDAO.deleteAll();
                ExperimentProfileDAO.deleteAll();
                VariableDAO.deleteAll();
                FingerprintDAO.deleteAll();

                DatasetDAO.deleteAll();
                RunDAO.deleteAll();

                em.createNativeQuery("DELETE FROM label_extractors").executeUpdate();
                LabelDAO.deleteAll();
                SchemaDAO.deleteAll();
                em.createNativeQuery("DELETE FROM dataset_schemas").executeUpdate();

                ActionDAO.deleteAll();
                AllowedSiteDAO.deleteAll();

                for (var subscription : WatchDAO.listAll()) {
                    subscription.delete();
                }
            }
            return null;
        });
    }

    public static Test createExampleTest(String testName) {
        return createExampleTest(testName, null);
    }

    public static Test createExampleTest(String testName, Integer datastoreID) {
        Test test = new Test();
        test.name = testName;
        test.description = "Bar";
        test.owner = TESTER_ROLES[0];
        test.transformers = new ArrayList<>();
        test.datastoreId = datastoreID;
        test.folder = "";
        return test;
    }

    public static String getAccessToken(String userName, String... groups) {
        return Jwt.preferredUserName(userName)
                .groups(new HashSet<>(Arrays.asList(groups)))
                .issuer("https://server.example.com")
                .audience("https://service.example.com")
                .jws()
                .keyId("1")
                .sign();
    }

    protected int uploadRun(Object runJson, String test) {
        long timestamp = System.currentTimeMillis();
        int runId = uploadRun(timestamp, timestamp, runJson, test);
        assertNotEquals(-1, runId);
        return runId;
    }

    protected List<Integer> uploadRun(Object runJson, String test, String schemaUri) {
        return uploadRun(runJson, test, schemaUri, jakarta.ws.rs.core.Response.Status.ACCEPTED.getStatusCode());
    }

    protected List<Integer> uploadRun(Object runJson, String test, String schemaUri, Integer statusCode) {
        long timestamp = System.currentTimeMillis();
        return uploadRun(timestamp, timestamp, runJson, test, schemaUri, statusCode);
    }

    protected List<Integer> uploadRun(long start, long stop, Object runJson, String test, String schemaUri,
            Integer statusCode) {
        return uploadRun(Long.toString(start), Long.toString(stop), test, UPLOADER_ROLES[0], Access.PUBLIC,
                schemaUri, null, statusCode, runJson);
    }

    protected int uploadRun(long timestamp, Object runJson, String test) {
        return uploadRun(timestamp, timestamp, runJson, test);
    }

    protected int uploadRun(long start, long stop, Object runJson, String test) {
        return uploadRun(start, stop, runJson, test, UPLOADER_ROLES[0], Access.PUBLIC);
    }

    protected int uploadRun(long start, long stop, Object runJson, String test, String owner, Access access) {
        String runIdsAsString = given().auth().oauth2(getUploaderToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(runJson)
                .post("/api/run/data?start=" + start + "&stop=" + stop + "&test=" + test + "&owner=" + owner + "&access="
                        + access)
                .then()
                .statusCode(202)
                .extract().asString();

        List<Integer> runIds = parseCommaSeparatedIds(runIdsAsString);
        assertEquals(1, runIds.size());
        return runIds.get(0);
    }

    protected List<Integer> uploadRun(String start, String stop, String test, String owner, Access access,
            String schemaUri, String description, Object runJson) {
        return uploadRun(start, stop, test, owner, access,
                schemaUri, description, jakarta.ws.rs.core.Response.Status.ACCEPTED.getStatusCode(), runJson);
    }

    protected List<Integer> uploadRun(String start, String stop, String test, String owner, Access access,
            String schemaUri, String description, Integer statusCode, Object runJson) {
        String runIdsAsString = RestAssured.given().auth().oauth2(getUploaderToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(runJson)
                .post("/api/run/data?start=" + start + "&stop=" + stop + "&test=" + test + "&owner=" + owner
                        + "&access=" + access + "&schema=" + schemaUri + "&description=" + description)
                .then()
                .statusCode(statusCode)
                .extract().asString();

        return parseCommaSeparatedIds(runIdsAsString);
    }

    protected int uploadRun(long timestamp, JsonNode data, JsonNode metadata, String testName) {
        return uploadRun(timestamp, timestamp, data, metadata, testName, UPLOADER_ROLES[0], Access.PUBLIC);
    }

    protected int uploadRun(long start, long stop, JsonNode data, JsonNode metadata, String testName, String owner,
            Access access) {
        String runIdsAsString = given().auth().oauth2(getUploaderToken())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA)
                // the .toString().getBytes(...) is required because RestAssured otherwise won't send the filename
                // and Quarkus in turn will use null FileUpload: https://github.com/quarkusio/quarkus/issues/20938
                .multiPart("data", "data.json", data.toString().getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON)
                .multiPart("metadata", "metadata.json", metadata.toString().getBytes(StandardCharsets.UTF_8),
                        MediaType.APPLICATION_JSON)
                .post("/api/run/data?start=" + start + "&stop=" + stop + "&test=" + testName + "&owner=" + owner + "&access="
                        + access)
                .then()
                .statusCode(202)
                .extract().asString();
        List<Integer> runIds = parseCommaSeparatedIds(runIdsAsString);
        assertEquals(1, runIds.size());
        return runIds.get(0);
    }

    protected int uploadRun(String start, String stop, JsonNode data, JsonNode metadata, String testName, String owner,
            Access access) {
        String runIdsAsString = given().auth().oauth2(getUploaderToken())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA)
                // the .toString().getBytes(...) is required because RestAssured otherwise won't send the filename
                // and Quarkus in turn will use null FileUpload: https://github.com/quarkusio/quarkus/issues/20938
                .multiPart("data", "data.json", data.toString().getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON)
                .multiPart("metadata", "metadata.json", metadata.toString().getBytes(StandardCharsets.UTF_8),
                        MediaType.APPLICATION_JSON)
                .post("/api/run/data?start=" + start + "&stop=" + stop + "&test=" + testName + "&owner=" + owner + "&access="
                        + access)
                .then()
                .statusCode(202)
                .extract().asString();
        List<Integer> runIds = parseCommaSeparatedIds(runIdsAsString);
        assertEquals(1, runIds.size());
        return runIds.get(0);
    }

    protected Integer addOrUpdateLabel(Integer schemaId, Label label) {
        String labelId = jsonRequest()
                .body(label)
                .post("/api/schema/" + schemaId + "/labels")
                .then()
                .statusCode(200)
                .extract().asString();
        return Integer.parseInt(labelId);
    }

    protected RunService.RunsSummary listTestRuns(int testId, boolean trashed,
            Integer limit, Integer page, String sort, SortDirection direction) {
        StringBuilder url = new StringBuilder("/api/run/list/" + testId + "?trashed=" + trashed);
        if (limit != null)
            url.append("&limit=" + limit);
        if (page != null)
            url.append("&page=" + page);
        if (sort != null)
            url.append("&sort=" + sort);
        if (direction != null)
            url.append("&direction=" + direction);
        return jsonRequest()
                .get(url.toString())
                .then()
                .statusCode(200)
                .extract()
                .as(RunService.RunsSummary.class);
    }

    protected RunService.RunExtended getRun(int id) {
        return jsonRequest().auth().oauth2(getTesterToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                //.body(org.testcontainers.shaded.com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
                .get("/api/run/" + id)
                .then()
                .statusCode(200)
                .extract().as(RunService.RunExtended.class);
    }

    protected List<ExperimentService.ExperimentResult> runExperiments(int datasetId) {
        return jsonRequest().get("/api/experiment/run?datasetId=" + datasetId)
                .then().statusCode(200).extract().body()
                .as(new ParameterizedTypeImpl(List.class, ExperimentService.ExperimentResult.class));

    }

    protected Test createTest(Test test) {
        log.debugf("Creating new test via /api/test: %s", test.toString());

        test = jsonRequest()
                .body(test)
                .post("/api/test")
                .then()
                .statusCode(200)
                .extract().body().as(Test.class);

        log.debugf("New test created via /api/test: %s", test.toString());

        return test;
    }

    protected void createViews(List<View> views) {
        jsonRequest()
                .body(views)
                .post("/api/ui/views")
                .then()
                .statusCode(204);
    }

    protected View createView(View view) {
        return jsonRequest()
                .body(view)
                .post("/api/ui/view")
                .then()
                .statusCode(200).extract().body().as(View.class);
    }

    protected List<View> getViews(int testId) {
        return jsonRequest().get("/api/ui/" + testId + "/views")
                .then().statusCode(200).extract().body().as(new ParameterizedTypeImpl(List.class, View.class));
    }

    protected void deleteTest(Test test) {
        RestAssured.given().auth().oauth2(getTesterToken())
                .delete("/api/test/" + test.id)
                .then()
                .statusCode(204);
    }

    protected RequestSpecification unauthenticatedJsonRequest() {
        return RestAssured.given()
                .header(HttpHeaders.CONTENT_TYPE, "application/json");
    }

    protected RequestSpecification jsonRequest() {
        return RestAssured.given().auth().oauth2(getTesterToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json");
    }

    protected RequestSpecification jsonUploaderRequest() {
        return RestAssured.given().auth().oauth2(getUploaderToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json");
    }

    protected String getTestName(TestInfo info) {
        return info.getTestClass().map(Class::getName).orElse("<unknown>") + "." + info.getDisplayName();
    }

    protected Schema createExampleSchema(TestInfo info) {
        String name = info.getTestClass().map(Class::getName).orElse("<unknown>") + "." + info.getDisplayName();
        Schema schema = createSchema(name, uriForTest(info, "1.0"));
        addLabel(schema, "value", null, new Extractor("value", "$.value", false));
        return schema;
    }

    protected Schema createExampleSchema(String name, String className, String displayName, boolean label) {
        Schema schema = new Schema();
        schema.owner = TESTER_ROLES[0];
        schema.access = Access.PUBLIC;
        schema.name = name + "." + displayName;
        schema.uri = "urn:" + className + ":" + displayName + ":1.0";
        Integer id = jsonRequest().body(schema).post("/api/schema").then()
                .statusCode(200).extract().as(Integer.class);
        schema.id = id;

        if (label) {
            addLabel(schema, "value", null, new Extractor("value", "$.value", false));
        }
        assertNotNull(schema.id);
        return schema;
    }

    protected Schema createSchema(String name, String uri) {
        return createSchema(name, uri, null);
    }

    protected Schema createSchema(String name, String uri, JsonNode jsonSchema) {
        Schema schema = new Schema();
        schema.owner = TESTER_ROLES[0];
        schema.name = name;
        schema.uri = uri;
        schema.schema = jsonSchema;
        return addOrUpdateSchema(schema);
    }

    protected Schema getSchema(int id, String token) {
        return jsonRequest().auth().oauth2(getTesterToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .get("/api/schema/" + id + "?token=" + token)
                .then()
                .statusCode(200)
                .extract().as(Schema.class);
    }

    protected Schema addOrUpdateSchema(Schema schema) {
        Response response = jsonRequest().body(schema).post("/api/schema");
        response.then().statusCode(200);
        schema.id = Integer.parseInt(response.body().asString());
        return schema;
    }

    protected void deleteSchema(Schema schema) {
        jsonRequest().delete("/api/schema/" + schema.id).then().statusCode(204);
    }

    protected String uriForTest(TestInfo info, String suffix) {
        return "urn:" + info.getTestClass().map(Class::getName).orElse("<unknown>") + ":" + info.getDisplayName() + ":"
                + suffix;
    }

    protected int addLabel(Schema schema, String name, String function, Extractor... extractors) {
        lastAddedLabelId = postLabel(schema, name, function, null, extractors);
        return lastAddedLabelId;
    }

    protected int addLabel(Schema schema, String name, String function, boolean filtering, boolean metric,
            Extractor... extractors) {
        lastAddedLabelId = postLabel(schema, name, function, null, filtering, metric, extractors);
        return lastAddedLabelId;
    }

    protected int updateLabel(Schema schema, int labelId, String name, String function, Extractor... extractors) {
        return postLabel(schema, name, function, l -> l.id = labelId, extractors);
    }

    protected int postLabel(Schema schema, String name, String function, Consumer<Label> mutate, Extractor... extractors) {
        return postLabel(schema, name, function, mutate, true, true, extractors);
    }

    protected int postLabel(Schema schema, String name, String function, Consumer<Label> mutate, boolean filtering,
            boolean metric, Extractor... extractors) {
        Label l = new Label();
        l.name = name;
        l.function = function;
        l.schemaId = schema.id;
        l.owner = TESTER_ROLES[0];
        l.access = Access.PUBLIC;
        l.extractors = Arrays.asList(extractors);
        if (mutate != null) {
            mutate.accept(l);
        }
        Response response = jsonRequest().body(l).post("/api/schema/" + schema.id + "/labels");
        response.then().statusCode(200);
        return Integer.parseInt(response.body().asString());
    }

    protected void deleteLabel(Schema schema, int labelId) {
        jsonRequest().delete("/api/schema/" + schema.id + "/labels/" + labelId).then().statusCode(204);
    }

    protected void setTestVariables(Test test, String name, Label label, ChangeDetection... rds) {
        label.id = lastAddedLabelId;
        setTestVariables(test, name, Collections.singletonList(label.name), rds);
    }

    protected void setTestVariables(Test test, String name, List<String> labels, ChangeDetection... rds) {
        List<Variable> variables = new ArrayList<>();
        Variable variable = new Variable();
        variable.testId = test.id;
        variable.name = name;
        variable.labels = labels;
        variable.changeDetection = Arrays.stream(rds).collect(Collectors.toSet());

        variables.add(variable);
        jsonRequest().body(variables).post("/api/alerting/variables?test=" + test.id).then().statusCode(204);
    }

    protected void updateVariables(Integer testId, List<Variable> variables) {
        jsonRequest().body(variables).post("/api/alerting/variables?test=" + testId).then().statusCode(204);
    }

    protected void addOrUpdateProfile(Integer testId, ExperimentProfile profile) {
        jsonRequest().body(profile).post("/api/experiment/" + testId + "/profiles").then().statusCode(200);
    }

    protected void updateChangeDetection(Integer testId, AlertingService.ChangeDetectionUpdate update) {
        jsonRequest().body(update).post("/api/alerting/variables?test=" + testId); //.then().statusCode(204);
    }

    protected List<Variable> variables(Integer testId) {
        return jsonRequest().get("/api/alerting/variables?test=" + testId)
                .then().statusCode(200).extract().body().as(new ParameterizedTypeImpl(List.class, Variable.class));
    }

    protected ArrayNode jsonArray(String... items) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode(items.length);
        for (String item : items) {
            array.add(item);
        }
        return array;
    }

    protected BlockingQueue<Integer> trashRun(int runId, Integer testId, boolean trashed) throws InterruptedException {
        BlockingQueue<Integer> trashedQueue = serviceMediator.getEventQueue(AsyncEventChannels.RUN_TRASHED, testId);
        jsonRequest().post("/api/run/" + runId + "/trash?isTrashed=" + trashed).then().statusCode(204);
        if (trashed) {
            assertEquals(runId, trashedQueue.poll(10, TimeUnit.SECONDS));
        }
        return trashedQueue;
    }

    protected <T> T withExampleDataset(Test test, JsonNode data, Function<Dataset, T> testLogic) {
        BlockingQueue<Dataset.EventNew> dataSetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW, test.id);
        try {
            RunDAO run = new RunDAO();
            tm.begin();
            try (CloseMe ignored = roleManager.withRoles(Arrays.asList(UPLOADER_ROLES))) {
                run.data = data;
                run.testid = test.id;
                run.start = run.stop = Instant.now();
                run.owner = UPLOADER_ROLES[0];
                log.debugf("Creating new Run via API: %s", run.toString());

                Response response = jsonRequest()
                        .auth()
                        .oauth2(getUploaderToken())
                        .body(run)
                        .post("/api/run/test");
                run.id = response.body().as(Integer.class);
                log.debugf("Run ID: %d, for test ID: %d", run.id, run.testid);
            } finally {
                if (tm.getTransaction().getStatus() == Status.STATUS_ACTIVE) {
                    tm.commit();
                } else {
                    tm.rollback();
                    fail();
                }
            }
            Dataset.EventNew event = dataSetQueue.poll(10, TimeUnit.SECONDS);
            assertNotNull(event);
            assertTrue(event.datasetId > 0);
            // only to cover the summary call in API
            jsonRequest().get("/api/dataset/" + event.datasetId + "/summary").then().statusCode(200);
            T value = testLogic.apply(DatasetMapper.from(
                    DatasetDAO.<DatasetDAO> findById(event.datasetId)));
            tm.begin();
            Throwable error = null;
            try (CloseMe ignored = roleManager.withRoles(SYSTEM_ROLES)) {
                DatasetDAO oldDs = DatasetDAO.findById(event.datasetId);
                if (oldDs != null) {
                    oldDs.delete();
                }
                DatasetDAO.delete("run.id", run.id);
                RunDAO.findById(run.id).delete();
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

    protected void addToken(Test test, int permissions, String value) {
        ObjectNode token = JsonNodeFactory.instance.objectNode();
        token.put("value", value);
        token.put("permissions", permissions);
        token.put("description", "blablabla");

        jsonRequest().header(HttpHeaders.CONTENT_TYPE, "application/json").body(token.toString())
                .post("/api/test/" + test.id + "/addToken").then().statusCode(200);
    }

    protected RequestSpecification bareRequest() {
        return RestAssured.given().auth().oauth2(getTesterToken());
    }

    protected void addTransformer(Test test, Transformer... transformers) {
        List<Integer> ids = new ArrayList<>();
        assertNotNull(test.id);
        for (Transformer t : transformers) {
            ids.add(t.id);
        }
        jsonRequest().body(ids).post("/api/test/" + test.id + "/transformers").then().assertThat().statusCode(204);
    }

    protected Transformer createTransformer(String name, Schema schema, String function, Extractor... paths) {
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
        transformer.schemaId = schema.id;
        transformer.schemaUri = schema.uri;
        transformer.schemaName = schema.name;
        transformer.function = function;
        transformer.targetSchemaUri = postFunctionSchemaUri(schema);
        Integer id = jsonRequest().body(transformer).post("/api/schema/" + schema.id + "/transformers")
                .then().statusCode(200).extract().as(Integer.class);
        transformer.id = id;
        return transformer;
    }

    protected String postFunctionSchemaUri(Schema s) {
        return "uri:" + s.name + "-post-function";
    }

    protected boolean checkTestId(int datasetId, int testId) {
        return Util.withTx(tm, () -> {
            try (CloseMe ignored = roleManager.withRoles(Collections.singleton(Roles.HORREUM_SYSTEM))) {
                List<?> list = em.createNativeQuery("SELECT testid FROM dataset WHERE id = ?1").setParameter(1, datasetId)
                        .getResultList();
                if (1 != list.size()) {
                    throw new RuntimeException("Retry TX");
                }
                return testId == (int) list.get(0);
            }
        });
    }

    protected boolean checkRunTestId(int runId, int testId) {
        return Util.withTx(tm, () -> {
            try (CloseMe ignored = roleManager.withRoles(Collections.singleton(Roles.HORREUM_SYSTEM))) {
                List<?> list = em.createNativeQuery("SELECT testid FROM run WHERE id = ?1").setParameter(1, runId)
                        .getResultList();
                assertEquals(1, list.size());
                return testId == (int) list.get(0);
            }
        });
    }

    protected void addAllowedSite(String prefix) {
        given().auth().oauth2(getAdminToken()).header(HttpHeaders.CONTENT_TYPE, "text/plain")
                .body(prefix).post("/api/action/allowedSites").then().statusCode(200);
    }

    protected Response addTestHttpAction(Test test, AsyncEventChannels event, String url) {
        Action action = new Action();
        action.event = event.name();
        action.type = HttpAction.TYPE_HTTP;
        action.active = true;
        action.testId = test.id;
        action.config = JsonNodeFactory.instance.objectNode().put("url", url);
        return jsonRequest().auth().oauth2(getAdminToken()).body(action).post("/api/action");
    }

    protected Response addTestGithubIssueCommentAction(Test test, AsyncEventChannels event, String formatter, String owner,
            String repo, String issue, String secretToken) {
        Action action = new Action();
        action.event = event.name();
        action.type = GitHubIssueCommentAction.TYPE_GITHUB_ISSUE_COMMENT;
        action.active = true;
        action.config = JsonNodeFactory.instance.objectNode()
                .put("formatter", formatter)
                .put("owner", owner)
                .put("repo", repo)
                .put("issue", issue);
        action.secrets = JsonNodeFactory.instance.objectNode().put("token", secretToken);
        return jsonRequest().body(action).post("/api/test/" + test.id + "/action");
    }

    protected Response addGlobalAction(AsyncEventChannels event, String url) {
        Action action = new Action();
        action.event = event.name();
        action.type = "http";
        action.active = true;
        action.config = JsonNodeFactory.instance.objectNode().put("url", url);
        return given().auth().oauth2(getAdminToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json").body(action).post("/api/action");
    }

    protected ChangeDetection addChangeDetectionVariable(Test test, int schemaId) {
        return addChangeDetectionVariable(test, 0.1, 2, schemaId);
    }

    protected ChangeDetection addChangeDetectionVariable(Test test, double threshold, int window, int schemaId) {
        ChangeDetection cd = new ChangeDetection();
        cd.model = ChangeDetectionModelType.names.RELATIVE_DIFFERENCE;
        cd.config = JsonNodeFactory.instance.objectNode().put("threshold", threshold).put("minPrevious", window)
                .put("window", window).put("filter", "mean");
        setTestVariables(test, "Value", new Label("value", schemaId), cd);
        return cd;
    }

    protected int addMissingDataRule(Test test, String ruleName, ArrayNode labels, String condition, int maxStaleness) {
        MissingDataRule rule = new MissingDataRule();
        rule.testId = test.id;
        rule.name = ruleName;
        rule.condition = condition;
        rule.labels = labels;
        rule.maxStaleness = maxStaleness;
        String ruleIdString = jsonRequest().body(rule).post("/api/alerting/missingdatarule?testId=" + test.id).then()
                .statusCode(200).extract().body().asString();
        return Integer.parseInt(ruleIdString);
    }

    protected void addExperimentProfile(Test test, String name, VariableDAO... variables) {
        ExperimentProfile profile = new ExperimentProfile();
        profile.name = name;
        profile.testId = test.id;
        profile.selectorLabels = JsonNodeFactory.instance.arrayNode().add("isSnapshot");
        profile.baselineLabels = JsonNodeFactory.instance.arrayNode().add("isSnapshot");
        profile.baselineFilter = "snapshot => !snapshot";
        profile.comparisons = Stream.of(variables).map(v -> {
            ExperimentComparison comp = new ExperimentComparison();
            comp.variableName = v.name;
            comp.variableId = v.id;
            comp.model = RelativeDifferenceExperimentModel.NAME;
            comp.config = JsonNodeFactory.instance.objectNode()
                    .setAll(new RelativeDifferenceExperimentModel().config().defaults);
            return comp;
        }).collect(Collectors.toList());

        // add new experimentProfile
        int profileId = jsonRequest().body(profile)
                .post("/api/experiment/" + test.id + "/profiles")
                .then().statusCode(200).extract().as(Integer.class);
        assertTrue(profileId > 0);

        //make sure the profile has been correctly stored
        Collection<ExperimentProfile> profiles = jsonRequest().get("/api/experiment/" + test.id + "/profiles")
                .then().statusCode(200).extract().body()
                .as(new ParameterizedTypeImpl(Collection.class, ExperimentProfile.class));

        assertEquals(1, profiles.size());
        assertEquals(profileId, profiles.stream().findFirst().get().id);
        assertEquals(test.id, profiles.stream().findFirst().get().testId);
    }

    protected void validateDatabaseContents(HashMap<String, List<JsonNode>> tableContents) {
        Util.withTx(tm, () -> {
            em.clear();
            try (var h = roleManager.withRoles(Collections.singleton(Roles.HORREUM_SYSTEM))) {
                for (String table : tableContents.keySet()) {
                    //noinspection unchecked
                    List<JsonNode> rows = em.createNativeQuery("SELECT to_jsonb(t) AS json FROM \"" + table + "\" t;")
                            .unwrap(NativeQuery.class).addScalar("json", JsonBinaryType.INSTANCE).getResultList();
                    List<JsonNode> expected = tableContents.get(table);

                    assertEquals(expected.size(), rows.size());
                    // If the table does not have ID column we won't compare values
                    if (!rows.isEmpty() && rows.get(0).hasNonNull("id")) {
                        Map<Integer, JsonNode> byId = rows.stream()
                                .collect(Collectors.toMap(row -> row.path("id").asInt(), Function.identity()));
                        assertEquals(rows.size(), byId.size());
                        for (var expectedRow : expected) {
                            JsonNode row = byId.get(expectedRow.path("id").asInt());
                            assertEquals(expectedRow, row, "Comparison failed in table " + table);
                        }
                    }
                }
            }
            return null;
        });
    }

    protected HashMap<String, List<JsonNode>> dumpDatabaseContents() {
        @SuppressWarnings("unchecked")
        List<String> tables = em.createNativeQuery(
                "SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname = 'public';").getResultList();
        tables.remove("databasechangelog");
        tables.remove("databasechangeloglock");
        tables.remove("view_recalc_queue");
        tables.remove("label_recalc_queue");
        tables.remove("fingerprint_recalc_queue");

        HashMap<String, List<JsonNode>> tableContents = new HashMap<>();
        Util.withTx(tm, () -> {
            try (var h = roleManager.withRoles(Collections.singleton(Roles.HORREUM_SYSTEM))) {
                for (String table : tables) {
                    //noinspection unchecked
                    tableContents.put(table, em.createNativeQuery("SELECT to_jsonb(t) AS json FROM \"" + table + "\" t;")
                            .unwrap(NativeQuery.class).addScalar("json", JsonBinaryType.INSTANCE).getResultList());
                }
            }
            return null;
        });
        return tableContents;
    }

    protected void populateDataFromFiles() throws IOException {
        if (getClass().getClassLoader().getResource(".") == null) {
            fail("Could not find resource directory, aborting test");
        }
        Path p = new File(getClass().getClassLoader().getResource(".").getPath()).toPath();
        p = p.getParent().getParent().getParent().resolve("infra-legacy/example-data/");

        Test t = new ObjectMapper().readValue(
                readFile(p.resolve("roadrunner_test.json").toFile()), Test.class);
        assertEquals("dev-team", t.owner);
        t.owner = "foo-team";
        t = createTest(t);

        View view = new ObjectMapper().readValue(
                readFile(p.resolve("roadrunner_view.json").toFile()), View.class);
        assertEquals("Default", view.name);
        view.testId = t.id;
        view = createView(view);

        Schema s = new ObjectMapper().readValue(
                readFile(p.resolve("acme_benchmark_schema.json").toFile()), Schema.class);
        assertEquals("dev-team", s.owner);
        s.owner = "foo-team";
        s = addOrUpdateSchema(s);

        Label l = new ObjectMapper().readValue(
                readFile(p.resolve("throughput_label.json").toFile()), Label.class);
        assertEquals("dev-team", l.owner);
        l.owner = "foo-team";
        Response response = jsonRequest().body(l)
                .post("/api/schema/" + s.id + "/labels");
        assertEquals(200, response.statusCode());
        l.id = Integer.parseInt(response.body().asString());

        Transformer transformer = new ObjectMapper().readValue(
                readFile(p.resolve("acme_transformer.json").toFile()), Transformer.class);
        assertEquals("dev-team", transformer.owner);
        transformer.owner = "foo-team";
        addTransformer(t, transformer);

        List<Variable> variables = new ObjectMapper().readValue(
                readFile(p.resolve("roadrunner_variables.json").toFile()), new TypeReference<>() {
                });
        assertEquals(1, variables.size());
        jsonRequest().body(variables).post("/api/alerting/variables?test=" + t.id).then().statusCode(204);

        Action a = new ObjectMapper().readValue(
                readFile(p.resolve("new_run_action.json").toFile()), Action.class);
        assertEquals("run/new", a.event);
        //This request should return a bad request as the url is not set
        jsonRequest().auth().oauth2(getAdminToken()).body(a).post("/api/action").then().statusCode(400);

        Run r = mapper.readValue(
                readFile(p.resolve("roadrunner_run.json").toFile()), Run.class);
        assertEquals("dev-team", r.owner);
        r.owner = "foo-team";
        r.testid = t.id;
        response = jsonRequest()
                .auth()
                .oauth2(getUploaderToken())
                .body(r)
                .post("/api/run/test");
        assertEquals(202, response.statusCode());

    }

    String readFile(File file) {
        if (file.isFile()) {
            try {
                return new String(Files.readAllBytes(file.toPath()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    protected static String resourceToString(String resourcePath) {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            return new BufferedReader(new InputStreamReader(inputStream))
                    .lines().collect(Collectors.joining(" "));
        } catch (IOException e) {
            fail("Failed to read `" + resourcePath + "`", e);
            return null;
        }
    }

    protected void createComparisonSchema() {
        Schema schema = createSchema("comparison", ReportServiceTest.SCHEMA);
        addLabel(schema, "variant", null, new Extractor("variant", "$.variant", false));
        addLabel(schema, "os", null, new Extractor("os", "$.os", false));
        addLabel(schema, "category", null, new Extractor("category", "$.category", false));
        addLabel(schema, "clusterSize", null, new Extractor("clusterSize", "$.clusterSize", false));
        addLabel(schema, "cpuUsage", null, new Extractor("cpuUsage", "$.cpuUsage", false));
        addLabel(schema, "memoryUsage", null, new Extractor("memoryUsage", "$.memoryUsage", false));
        addLabel(schema, "throughput", null, new Extractor("throughput", "$.throughput", false));
    }

    protected void uploadExampleRuns(Test test) throws InterruptedException {
        BlockingQueue<Dataset.LabelsUpdatedEvent> queue = serviceMediator
                .getEventQueue(AsyncEventChannels.DATASET_UPDATED_LABELS, test.id);

        long ts = System.currentTimeMillis();
        uploadRun(ts - 1, createRunData("production", "windows", "jvm", 1, 0.5, 150_000_000, 123), test.name);
        uploadRun(ts - 2, createRunData("debug", "windows", "jvm", 2, 0.4, 120_000_000, 256), test.name);
        uploadRun(ts - 3, createRunData("production", "linux", "jvm", 1, 0.4, 100_000_000, 135), test.name);
        uploadRun(ts - 4, createRunData("debug", "linux", "jvm", 2, 0.3, 80_000_000, 260), test.name);
        uploadRun(ts - 5, createRunData("production", "windows", "native", 1, 0.4, 50_000_000, 100), test.name);
        uploadRun(ts - 6, createRunData("production", "windows", "native", 2, 0.3, 40_000_000, 200), test.name);
        uploadRun(ts - 7, createRunData("production", "linux", "native", 1, 0.3, 30_000_000, 110), test.name);
        uploadRun(ts - 8, createRunData("debug", "linux", "native", 2, 0.28, 20_000_000, 210), test.name);
        // some older run that should be ignored
        uploadRun(ts - 9, createRunData("production", "windows", "jvm", 2, 0.8, 150_000_000, 200), test.name);

        for (int i = 0; i < 9; ++i) {
            assertNotNull(queue.poll(1, TimeUnit.SECONDS));
        }
    }

    private JsonNode createRunData(String variant, String os, String category, int clusterSize, double cpuUsage,
            long memoryUsage, long throughput) {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        return data.put("$schema", SCHEMA)
                .put("variant", variant)
                .put("os", os)
                .put("category", category)
                .put("clusterSize", clusterSize)
                .put("cpuUsage", cpuUsage)
                .put("memoryUsage", memoryUsage)
                .put("throughput", throughput);
    }

    protected TableReportConfig newExampleTableReportConfig(Test test) {
        TableReportConfig config = new TableReportConfig();
        config.test = test;
        config.title = "Test no filter";
        config.categoryLabels = arrayOf("category");
        // category is used just for the sake of testing two labels
        config.seriesLabels = arrayOf("os", "category");
        config.seriesFunction = "({ os, category }) => os";
        config.scaleLabels = arrayOf("clusterSize");
        config.components = new ArrayList<>();
        config.components.add(newComponent(null, "cpuUsage"));
        config.components.add(newComponent(null, "memoryUsage"));
        config.components.add(newComponent(null, "throughput"));
        return config;
    }

    private ReportComponent newComponent(String function, String... labels) {
        ReportComponent component = new ReportComponent();
        component.name = Stream.of(labels).map(l -> Character.toUpperCase(l.charAt(0)) + l.substring(1))
                .collect(Collectors.joining("+"));
        component.labels = arrayOf(labels);
        component.function = function;
        return component;
    }

    protected ArrayNode arrayOf(String... labels) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (String label : labels) {
            array.add(label);
        }
        return array;
    }

    protected void createTests(int count, String prefix) {
        for (int i = 0; i < count; i += 1) {
            createTest(new Test(createExampleTest(String.format("%1$s_%2$02d", prefix, i))));
        }
    }

    protected String labelValuesSetup(Test t, boolean load) {
        Schema fooSchema = createSchema("foo", "urn:foo");
        Extractor fooExtractor = new Extractor();
        fooExtractor.name = "foo";
        fooExtractor.jsonpath = "$.foo";
        Extractor barExtractor = new Extractor();
        barExtractor.name = "bar";
        barExtractor.jsonpath = "$.bar";

        addLabel(fooSchema, "labelFoo", "", fooExtractor);
        addLabel(fooSchema, "labelBar", "", barExtractor);

        if (load) {
            List<Integer> ids = uploadRun("{ \"foo\": \"uno\", \"bar\": \"dox\"}", t.name, fooSchema.uri);
            assertEquals(1, ids.size());
            // force to recalculate datasets and label values sync
            recalculateDatasetForRun(ids.get(0));
            return ids.get(0).toString();
        } else {
            return "-1";
        }
    }

    protected List<Integer> recalculateDatasetForRun(int runId) {
        ArrayNode json = jsonRequest().post("/api/run/" + runId + "/recalculate").then().statusCode(200).extract().body()
                .as(ArrayNode.class);
        ArrayList<Integer> list = new ArrayList<>(json.size());
        json.forEach(item -> list.add(item.asInt()));
        return list;
    }

    protected DatasetService.DatasetList listTestDatasets(long id, SortDirection direction) {
        StringBuilder url = new StringBuilder("/api/dataset/list/" + id);
        if (direction != null) {
            url.append("?direction=").append(direction);
        }
        return jsonRequest()
                .get(url.toString())
                .then()
                .statusCode(200)
                .extract()
                .as(DatasetService.DatasetList.class);
    }

    protected TestService.TestListing listTestSummary(String roles, String folder, int limit, int page,
            SortDirection direction) {
        StringBuilder url = new StringBuilder("/api/test/summary");
        url.append("?limit=").append(limit).append("&page=").append(page).append("&direction=").append(SortDirection.Ascending);
        if (roles != null && !"".equals(roles))
            url.append("&roles=").append(roles);
        if (folder != null && !"".equals(folder))
            url.append("&folder=").append(folder);

        return jsonRequest()
                .get(url.toString())
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(TestService.TestListing.class);
    }

    protected void updateView(View view) {
        View newView = jsonRequest().body(view).post("/api/ui/view")
                .then().statusCode(200).extract().body().as(View.class);
        if (view.id != null) {
            assertEquals(view.id, newView.id);
        }
    }

    protected DataPoint assertValue(BlockingQueue<DataPoint.Event> datapointQueue, double value) throws InterruptedException {
        DataPoint.Event dpe = datapointQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(dpe);
        assertEquals(value, dpe.dataPoint.value);
        testSerialization(dpe, DataPoint.Event.class);
        return dpe.dataPoint;
    }

    protected <T> void testSerialization(T event, Class<T> eventClass) {
        // test serialization and deserialization
        JsonNode changeJson = Util.OBJECT_MAPPER.valueToTree(event);
        try {
            Util.OBJECT_MAPPER.treeToValue(changeJson, eventClass);
        } catch (JsonProcessingException e) {
            throw new AssertionError("Cannot deserialize " + event + " from " + changeJson.toPrettyString(), e);
        }
    }

    private List<Integer> parseCommaSeparatedIds(String idsAsString) {
        return idsAsString.isBlank() ? List.of()
                : Stream.of(idsAsString.trim().split(",")).map(Integer::parseInt).collect(Collectors.toList());
    }
}
