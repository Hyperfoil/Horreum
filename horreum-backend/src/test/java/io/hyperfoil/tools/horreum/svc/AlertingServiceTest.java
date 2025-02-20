package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.inject.Inject;

import org.junit.jupiter.api.TestInfo;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.alerting.*;
import io.hyperfoil.tools.horreum.api.data.Dataset;
import io.hyperfoil.tools.horreum.api.data.Extractor;
import io.hyperfoil.tools.horreum.api.data.Fingerprints;
import io.hyperfoil.tools.horreum.api.data.Label;
import io.hyperfoil.tools.horreum.api.data.Schema;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.api.data.changeDetection.ChangeDetectionModelType;
import io.hyperfoil.tools.horreum.api.internal.services.AlertingService;
import io.hyperfoil.tools.horreum.api.services.TestService;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.entity.FingerprintDAO;
import io.hyperfoil.tools.horreum.entity.alerting.*;
import io.hyperfoil.tools.horreum.entity.data.*;
import io.hyperfoil.tools.horreum.mapper.LabelMapper;
import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.server.RoleManager;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.hyperfoil.tools.horreum.test.TestUtil;
import io.quarkus.arc.impl.ParameterizedTypeImpl;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class AlertingServiceTest extends BaseServiceTest {

    @Inject
    RoleManager roleManager;

    @Inject
    AlertingServiceImpl alertingService;

    @Inject
    ServiceMediator serviceMediator;

    @org.junit.jupiter.api.Test
    public void testNotifications(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);
        setTestVariables(test, "Value", new Label("value", schema.id));

        BlockingQueue<DataPoint.Event> dpe = serviceMediator.getEventQueue(AsyncEventChannels.DATAPOINT_NEW, test.id);
        uploadRun(runWithValue(42, schema).toString(), test.name);

        DataPoint.Event event1 = dpe.poll(10, TimeUnit.SECONDS);
        assertNotNull(event1);
        assertEquals(42d, event1.dataPoint.value);
        assertTrue(event1.notify);

        RestAssured.given().auth().oauth2(getTesterToken())
                .post("/api/test/" + test.id + "/notifications?enabled=false")
                .then().statusCode(204);

        uploadRun(runWithValue(0, schema).toString(), test.name);

        DataPoint.Event event2 = dpe.poll(10, TimeUnit.SECONDS);
        assertNotNull(event2);
        assertEquals(0, event2.dataPoint.value, prettyPrint(event2));
        assertFalse(event2.notify);
    }

    private String prettyPrint(Object obj) {
        return Util.OBJECT_MAPPER.valueToTree(obj).toPrettyString();
    }

    @org.junit.jupiter.api.Test
    public void testLogging(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);
        setTestVariables(test, "Value", new Label("value", schema.id));

        // This run won't contain the 'value'
        ObjectNode runJson = JsonNodeFactory.instance.objectNode();
        runJson.put("$schema", schema.uri);

        BlockingQueue<MissingValuesEvent> missingQueue = serviceMediator
                .getEventQueue(AsyncEventChannels.DATASET_MISSING_VALUES, test.id);
        missingQueue.drainTo(new ArrayList<>());
        int runId = uploadRun(runJson, test.name);
        recalculateDatasetForRun(runId);

        MissingValuesEvent event = missingQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(event);
        assertEquals(runId, event.dataset.runId);

        Util.withTx(tm, () -> {
            try (CloseMe ignored = roleManager.withRoles(Arrays.asList(TESTER_ROLES))) {
                List<DatasetLogDAO> logs = DatasetLogDAO.find("dataset.run.id", runId).list();
                assertFalse(logs.isEmpty());
                return null;
            }
        });

        try (CloseMe ignored = roleManager.withRoles(Arrays.asList(TESTER_ROLES))) {
            deleteTest(test);

            TestUtil.eventually(() -> {
                em.clear();
                List<DatasetLogDAO> currentLogs = DatasetLogDAO.find("dataset.run.id", runId).list();
                assertEquals(0, currentLogs.size());
            });
        }
    }

    @org.junit.jupiter.api.Test
    public void testChangeDetection(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);
        ChangeDetection cd = addChangeDetectionVariable(test, schema.id);

        BlockingQueue<DataPoint.Event> datapointQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATAPOINT_NEW,
                test.id);
        BlockingQueue<Change.Event> changeQueue = serviceMediator.getEventQueue(AsyncEventChannels.CHANGE_NEW, test.id);

        long ts = System.currentTimeMillis();
        uploadRun(ts, ts, runWithValue(1, schema), test.name);
        assertValue(datapointQueue, 1);
        uploadRun(ts + 1, ts + 1, runWithValue(2, schema), test.name);
        assertValue(datapointQueue, 2);
        int run3 = uploadRun(ts + 2, ts + 2, runWithValue(1, schema), test.name);
        assertValue(datapointQueue, 1);
        int run4 = uploadRun(ts + 3, ts + 3, runWithValue(2, schema), test.name);
        assertValue(datapointQueue, 2);

        assertNull(changeQueue.poll(50, TimeUnit.MILLISECONDS));

        uploadRun(ts + 4, ts + 4, runWithValue(3, schema), test.name);
        assertValue(datapointQueue, 3);

        Change.Event changeEvent1 = changeQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(changeEvent1);
        testSerialization(changeEvent1, Change.Event.class);
        // The change is detected already at run 4 because it's > than the previous mean
        assertEquals(run4, changeEvent1.change.dataset.runId);

        cd.config.put("filter", "min");
        setTestVariables(test, "Value", new Label("value", schema.id), cd);
        // After changing the variable the past datapoints and changes are removed; we need to recalculate them again
        String notifyPath = "/api/alerting/recalculate?test="
                .concat(test.id.toString())
                .concat("&notify=false&debug=false&recalc=true&from=")
                .concat(Long.toString(Long.MIN_VALUE)).concat("&to=")
                .concat(Long.toString(Long.MAX_VALUE));

        jsonRequest().post(notifyPath).then().statusCode(204);

        for (int i = 0; i < 5; ++i) {
            DataPoint.Event dpe = datapointQueue.poll(10, TimeUnit.SECONDS);
            assertNotNull(dpe);
        }

        // now we'll find a change already at run3
        Change.Event changeEvent2 = changeQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(changeEvent2);
        assertEquals(run3, changeEvent2.change.dataset.runId);

        int run6 = uploadRun(ts + 5, ts + 5, runWithValue(1.5, schema), test.name);
        assertValue(datapointQueue, 1.5);

        // mean of previous values is 1.5, now the min is 1.5 => no change
        assertNull(changeQueue.poll(50, TimeUnit.MILLISECONDS));

        uploadRun(ts + 6, ts + 6, runWithValue(2, schema), test.name);
        assertValue(datapointQueue, 2);

        // mean of previous is 2, the last value doesn't matter (1.5 is lower than 2 - 10%)
        Change.Event changeEvent3 = changeQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(changeEvent3);
        assertEquals(run6, changeEvent3.change.dataset.runId);
    }

    @org.junit.jupiter.api.Test
    public void testChangeDetectionWithFingerprint(TestInfo info) throws InterruptedException {
        Test test = createExampleTest(getTestName(info));
        test.fingerprintLabels = jsonArray("config");
        test = createTest(test);
        int testId = test.id;
        Schema schema = createExampleSchema(info);
        addLabel(schema, "config", null, new Extractor("config", "$.config", false));

        addChangeDetectionVariable(test, schema.id);

        BlockingQueue<DataPoint.Event> datapointQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATAPOINT_NEW, testId);
        BlockingQueue<Change.Event> changeQueue = serviceMediator.getEventQueue(AsyncEventChannels.CHANGE_NEW, testId);

        long ts = System.currentTimeMillis();
        for (int i = 0; i < 12; i += 3) {
            uploadRun(ts + i, ts + i, runWithValue(1, schema).put("config", "foo"), test.name);
            assertValue(datapointQueue, 1);
            uploadRun(ts + i + 1, ts + i + 1, runWithValue(2, schema).put("config", "bar"), test.name);
            assertValue(datapointQueue, 2);
            uploadRun(ts + i + 2, ts + i + 2, runWithValue(3, schema), test.name);
            assertValue(datapointQueue, 3);
        }
        assertNull(changeQueue.poll(50, TimeUnit.MILLISECONDS));

        int run13 = uploadRun(ts + 12, ts + 12, runWithValue(2, schema).put("config", "foo"), test.name);
        Change.Event changeEvent1 = changeQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(changeEvent1);
        assertEquals(run13, changeEvent1.change.dataset.runId);
        assertEquals(run13, changeEvent1.dataset.runId);

        int run14 = uploadRun(ts + 13, ts + 13, runWithValue(2, schema), test.name);
        Change.Event changeEvent2 = changeQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(changeEvent2);
        assertEquals(run14, changeEvent2.change.dataset.runId);
        assertEquals(run14, changeEvent2.dataset.runId);
    }

    @org.junit.jupiter.api.Test
    public void testFingerprintLabelsChange(TestInfo info) throws Exception {
        Test test = createExampleTest(getTestName(info));
        test.fingerprintLabels = jsonArray("foo");
        test = createTest(test);
        int testId = test.id;
        Schema schema = createExampleSchema(info);
        addChangeDetectionVariable(test, schema.id);
        addLabel(schema, "foo", null, new Extractor("foo", "$.foo", false));
        addLabel(schema, "bar", null, new Extractor("bar", "$.bar", false));

        BlockingQueue<DataPoint.Event> datapointQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATAPOINT_NEW, testId);
        uploadRun(runWithValue(42, schema).put("foo", "aaa").put("bar", "bbb"), test.name);
        assertValue(datapointQueue, 42);

        List<FingerprintDAO> fingerprintsBefore = FingerprintDAO.listAll();
        assertEquals(1, fingerprintsBefore.size());
        assertEquals(JsonNodeFactory.instance.objectNode().put("foo", "aaa"), fingerprintsBefore.get(0).fingerprint);

        test.fingerprintLabels = ((ArrayNode) test.fingerprintLabels).add("bar");
        // We'll change the filter here but we do NOT expect to be applied to existing datapoints
        test.fingerprintFilter = "value => false";
        test = createTest(test); // this is update
        // the fingerprint should be updated within the same transaction as test update
        em.clear();
        List<FingerprintDAO> fingerprintsAfter = FingerprintDAO.listAll();
        assertEquals(1, fingerprintsAfter.size());
        assertEquals(JsonNodeFactory.instance.objectNode().put("foo", "aaa").put("bar", "bbb"),
                fingerprintsAfter.get(0).fingerprint);
        assertEquals(fingerprintsBefore.get(0).datasetId, fingerprintsAfter.get(0).datasetId);

        //lets also test fingerprint endpoint
        List<Fingerprints> values = jsonRequest().get("/api/test/" + test.id + "/fingerprint")
                .then().statusCode(200).extract().body().as(new TypeRef<>() {
                });
        assertEquals(1, values.size());
        assertEquals(2, values.get(0).values.size());
        assertEquals("aaa", values.get(0).values.get(1).value);

        assertEquals(1L, DataPointDAO.findAll().count());

        deleteTest(test);
        assertEquals(0L, DataPointDAO.findAll().count());
    }

    //we need to find a way to determine when the re-calculation is complete
    @org.junit.jupiter.api.Disabled
    public void testFingerprintFilter(TestInfo info) throws Exception {
        Test test = createExampleTest(getTestName(info));
        test.fingerprintLabels = jsonArray("foo");
        test.fingerprintFilter = "value => value === 'aaa'";
        test = createTest(test);
        int testId = test.id;
        Schema schema = createExampleSchema(info);
        addChangeDetectionVariable(test, schema.id);
        addLabel(schema, "foo", null, new Extractor("foo", "$.foo", false));
        addLabel(schema, "bar", null, new Extractor("bar", "$.bar", false));

        BlockingQueue<DataPoint.Event> datapointQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATAPOINT_NEW, testId);

        uploadRun(runWithValue(1, schema).put("foo", "aaa").put("bar", "bbb"), test.name);
        assertValue(datapointQueue, 1);

        // no fingerprint, should not match
        uploadRun(runWithValue(2, schema), test.name);

        uploadRun(runWithValue(3, schema).put("foo", "bbb"), test.name);
        assertNull(datapointQueue.poll(50, TimeUnit.MILLISECONDS));
        assertEquals(3, DatasetDAO.count());
        assertEquals(1, DataPointDAO.count());
        em.clear();

        test.fingerprintLabels = ((ArrayNode) test.fingerprintLabels).add("bar");
        test.fingerprintFilter = "({foo, bar}) => bar !== 'bbb'";
        createTest(test); // update

        uploadRun(runWithValue(4, schema).put("foo", "bbb").put("bar", "aaa"), test.name);
        assertValue(datapointQueue, 4);
        assertEquals(4, DatasetDAO.count());
        assertEquals(2, DataPointDAO.count());

        recalculateDatapoints(test.id);

        List<DataPointDAO> datapoints = DataPointDAO.listAll();
        assertEquals(3, datapoints.size());
        assertTrue(datapoints.stream().anyMatch(dp -> dp.value == 2));
        assertTrue(datapoints.stream().anyMatch(dp -> dp.value == 3));
        assertTrue(datapoints.stream().anyMatch(dp -> dp.value == 4));
    }

    private void recalculateDatapoints(int testId) throws InterruptedException {
        jsonRequest()
                .queryParam("test", testId).queryParam("notify", true).queryParam("debug", true)
                .post("/api/alerting/recalculate")
                .then().statusCode(204);
        for (int i = 0; i < 200; ++i) {
            AlertingService.DatapointRecalculationStatus status = jsonRequest().queryParam("test", testId)
                    .get("/api/alerting/recalculate")
                    .then().statusCode(200).extract().body().as(AlertingService.DatapointRecalculationStatus.class);
            if (status.done) {
                break;
            }
            Thread.sleep(20);
        }
    }

    @org.junit.jupiter.api.Test
    public void testMissingRules(TestInfo info) throws InterruptedException {
        NotificationServiceImpl notificationService = Mockito.mock(NotificationServiceImpl.class);
        List<String> notifications = Collections.synchronizedList(new ArrayList<>());
        Mockito.doAnswer(invocation -> {
            notifications.add(invocation.getArgument(1, String.class));
            return null;
        }).when(notificationService).notifyMissingDataset(Mockito.anyInt(), Mockito.anyString(), Mockito.anyLong(),
                Mockito.any(Instant.class));
        QuarkusMock.installMockForType(notificationService, NotificationServiceImpl.class);

        Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);
        int firstRuleId = addMissingDataRule(test, "my rule", jsonArray("value"), "value => value > 2", 10000);
        assertTrue(firstRuleId > 0);

        BlockingQueue<Dataset.EventNew> newDatasetQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_NEW,
                test.id);
        long now = System.currentTimeMillis();
        uploadRun(now - 20000, runWithValue(3, schema), test.name);
        Dataset.EventNew firstEvent = newDatasetQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(firstEvent);
        uploadRun(now - 5000, runWithValue(1, schema), test.name);
        Dataset.EventNew secondEvent = newDatasetQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(secondEvent);
        // only the matching dataset will be present
        pollMissingDataRuleResultsByRule(firstRuleId, firstEvent.datasetId);

        alertingService.checkMissingDataset();
        assertEquals(1, notifications.size());
        assertEquals("my rule", notifications.get(0));

        // The notification should not fire again because the last notification is now
        alertingService.checkMissingDataset();
        assertEquals(1, notifications.size());

        Util.withTx(tm, () -> {
            try (@SuppressWarnings("unused")
            CloseMe h = roleManager.withRoles(SYSTEM_ROLES)) {
                MissingDataRuleDAO currentRule = MissingDataRuleDAO.findById(firstRuleId);
                assertNotNull(currentRule.lastNotification);
                assertTrue(currentRule.lastNotification.isAfter(Instant.ofEpochMilli(now - 1)));
                // remove the last notification
                currentRule.lastNotification = null;
                currentRule.persistAndFlush();
                return null;
            }
        });
        em.clear();

        int thirdRunId = uploadRun(now - 5000, runWithValue(3, schema), test.name);
        Dataset.EventNew thirdEvent = newDatasetQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(thirdEvent);
        pollMissingDataRuleResultsByRule(firstRuleId, firstEvent.datasetId, thirdEvent.datasetId);
        alertingService.checkMissingDataset();
        assertEquals(1, notifications.size());

        em.clear();

        pollMissingDataRuleResultsByDataset(thirdEvent.datasetId, 1);
        trashRun(thirdRunId, test.id, true);
        pollMissingDataRuleResultsByDataset(thirdEvent.datasetId, 0);

        alertingService.checkMissingDataset();
        assertEquals(2, notifications.size());
        assertEquals("my rule", notifications.get(1));

        int otherRuleId = addMissingDataRule(test, null, null, null, 10000);
        pollMissingDataRuleResultsByRule(otherRuleId, firstEvent.datasetId, secondEvent.datasetId);
        alertingService.checkMissingDataset();
        assertEquals(2, notifications.size());

        Util.withTx(tm, () -> {
            try (@SuppressWarnings("unused")
            CloseMe h = roleManager.withRoles(SYSTEM_ROLES)) {
                MissingDataRuleDAO otherRule = MissingDataRuleDAO.findById(otherRuleId);
                otherRule.maxStaleness = 1000;
                otherRule.persistAndFlush();
                return null;
            }
        });
        alertingService.checkMissingDataset();
        assertEquals(3, notifications.size());
        assertTrue(notifications.get(2).startsWith("rule #"));
        em.clear();

        Util.withTx(tm, () -> {
            try (@SuppressWarnings("unused")
            CloseMe h = roleManager.withRoles(SYSTEM_ROLES)) {
                MissingDataRuleDAO otherRule = MissingDataRuleDAO.findById(otherRuleId);
                assertNotNull(otherRule.lastNotification);
                otherRule.lastNotification = Instant.ofEpochMilli(now - 2000);
                otherRule.persistAndFlush();
                return null;
            }
        });
        alertingService.checkMissingDataset();
        assertEquals(4, notifications.size());
        assertTrue(notifications.get(3).startsWith("rule #"));

        jsonRequest().delete("/api/alerting/missingdatarule/" + otherRuleId).then().statusCode(204);
        em.clear();
        assertEquals(0, MissingDataRuleResultDAO.find("pk.ruleId", otherRuleId).count());
    }

    private void pollMissingDataRuleResultsByRule(int ruleId, int... datasetIds) throws InterruptedException {
        try (CloseMe h = roleManager.withRoles(SYSTEM_ROLES)) {
            for (int i = 0; i < 1000; ++i) {
                em.clear();
                List<MissingDataRuleResultDAO> results = MissingDataRuleResultDAO.list("pk.ruleId", ruleId);
                if (results.size() == datasetIds.length && results.stream().mapToInt(MissingDataRuleResultDAO::datasetId)
                        .allMatch(res -> IntStream.of(datasetIds).anyMatch(id -> id == res))) {
                    return;
                } else {
                    Thread.sleep(10);
                }
            }
            fail();
        }
    }

    private void pollMissingDataRuleResultsByDataset(int datasetId, long expectedResults) throws InterruptedException {
        try (CloseMe h = roleManager.withRoles(SYSTEM_ROLES)) {
            // there's no event when the results are updated, we need to poll
            for (int i = 0; i < 1000; ++i) {
                em.clear();
                List<MissingDataRuleResultDAO> results = MissingDataRuleResultDAO.list("pk.datasetId", datasetId);
                if (expectedResults == results.size()) {
                    return;
                } else {
                    Thread.sleep(10);
                }
            }
            fail();
        }
    }

    private AtomicLong mockInstantNow() {
        TimeService timeService = Mockito.mock(TimeService.class);
        AtomicLong current = new AtomicLong(System.currentTimeMillis());
        Mockito.doAnswer(invocation -> Instant.ofEpochMilli(current.get()))
                .when(timeService).now();
        QuarkusMock.installMockForType(timeService, TimeService.class);
        return current;
    }

    private List<String> mockNotifyExpectedRun() {
        NotificationServiceImpl notificationService = Mockito.mock(NotificationServiceImpl.class);
        List<String> notifications = Collections.synchronizedList(new ArrayList<>());
        Mockito.doAnswer(invocation -> {
            notifications.add(invocation.getArgument(2, String.class));
            return null;
        }).when(notificationService).notifyExpectedRun(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyString(),
                Mockito.anyString());
        QuarkusMock.installMockForType(notificationService, NotificationServiceImpl.class);
        return notifications;
    }

    @org.junit.jupiter.api.Test
    public void testExpectRunTimeout() {
        Test test = createTest(createExampleTest("timeout"));
        AtomicLong current = mockInstantNow();
        List<String> notifications = mockNotifyExpectedRun();

        jsonUploaderRequest().post("/api/alerting/expectRun?test=" + test.name + "&timeout=10&expectedby=foo&backlink=bar")
                .then().statusCode(204);
        List<RunExpectation> expectations = jsonRequest().get("/api/alerting/expectations")
                .then().statusCode(200).extract().body().as(new ParameterizedTypeImpl(List.class, RunExpectation.class));
        assertEquals(1, expectations.size());
        alertingService.checkExpectedRuns();
        assertEquals(0, notifications.size());

        current.addAndGet(20000);
        alertingService.checkExpectedRuns();
        assertEquals(1, notifications.size());
        assertEquals("foo", notifications.get(0));
    }

    @org.junit.jupiter.api.Test
    public void testExpectRunUploaded() {
        Test test = createTest(createExampleTest("uploaded"));
        AtomicLong current = mockInstantNow();
        List<String> notifications = mockNotifyExpectedRun();

        jsonUploaderRequest().post("/api/alerting/expectRun?test=" + test.name + "&timeout=10").then().statusCode(204);
        List<RunExpectation> expectations = jsonRequest().get("/api/alerting/expectations")
                .then().statusCode(200).extract().body().as(new ParameterizedTypeImpl(List.class, RunExpectation.class));
        assertEquals(1, expectations.size());
        alertingService.checkExpectedRuns();
        assertEquals(0, notifications.size());

        uploadRun(JsonNodeFactory.instance.objectNode(), test.name);

        current.addAndGet(20000);
        alertingService.checkExpectedRuns();
        assertEquals(0, notifications.size());
    }

    // This tests recalculation of run -> dataset, not dataset -> datapoint
    @org.junit.jupiter.api.Test
    public void testRecalculateDatasets(TestInfo info) throws InterruptedException {
        assertEquals(0, DataPointDAO.count());

        Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);
        addChangeDetectionVariable(test, schema.id);

        BlockingQueue<DataPoint.Event> datapointQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATAPOINT_NEW,
                test.id);

        uploadRun(runWithValue(42, schema), test.name);
        DataPoint first = assertValue(datapointQueue, 42);

        assertNotNull(DataPointDAO.findById(first.id));
        assertEquals(1, DataPointDAO.count());

        recalculateDatasets(test.id, false);
        DataPoint second = assertValue(datapointQueue, 42);
        assertNotEquals(first.id, second.id);

        assertEquals(0, DataPointDAO.count("id", first.id));

        em.clear();
        // We need to use system role in the test because as the policy fetches ownership from dataset
        // and this is missing we wouldn't find the old datapoint anyway
        try (CloseMe ignored = roleManager.withRoles(SYSTEM_ROLES)) {
            assertNotNull(DataPointDAO.findById(second.id));
            assertNull(DataPointDAO.findById(first.id));
            assertEquals(1, DataPointDAO.count());
        }
    }

    private void recalculateDatasets(int testId, boolean waitForComplete) throws InterruptedException {
        jsonRequest()
                .post("/api/test/" + testId + "/recalculate")
                .then().statusCode(204);
        if (waitForComplete) {
            for (int i = 0; i < 200; ++i) {
                TestService.RecalculationStatus status = jsonRequest().get("/api/test/" + testId + "/recalculate")
                        .then().statusCode(200).extract().body().as(TestService.RecalculationStatus.class);
                if (status.finished == status.totalRuns) {
                    break;
                }
                Thread.sleep(20);
            }
        }
    }

    @org.junit.jupiter.api.Test
    public void testFixedThresholds(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);
        ChangeDetection rd = new ChangeDetection();
        rd.model = ChangeDetectionModelType.names.FIXED_THRESHOLD;
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        config.putObject("min").put("value", 3).put("enabled", true).put("inclusive", true);
        config.putObject("max").put("value", 6).put("enabled", true).put("inclusive", false);
        rd.config = config;
        setTestVariables(test, "Value", new Label("value", schema.id), rd);

        BlockingQueue<DataPoint.Event> datapointQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATAPOINT_NEW,
                test.id);
        BlockingQueue<Change.Event> changeQueue = serviceMediator.getEventQueue(AsyncEventChannels.CHANGE_NEW, test.id);

        long ts = System.currentTimeMillis();
        uploadRun(ts, ts, runWithValue(4, schema), test.name);
        assertValue(datapointQueue, 4);
        uploadRun(ts + 1, ts + 1, runWithValue(3, schema), test.name);
        assertValue(datapointQueue, 3);
        // lower bound is inclusive, no change
        assertNull(changeQueue.poll(50, TimeUnit.MILLISECONDS));

        int run3 = uploadRun(ts + 2, ts + 2, runWithValue(2, schema), test.name);
        assertValue(datapointQueue, 2);
        Change.Event changeEvent1 = changeQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(changeEvent1);
        assertEquals(run3, changeEvent1.change.dataset.runId);

        int run4 = uploadRun(ts + 3, ts + 3, runWithValue(6, schema), test.name);
        assertValue(datapointQueue, 6);
        Change.Event changeEvent2 = changeQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(changeEvent2);
        assertEquals(run4, changeEvent2.change.dataset.runId);
    }

    @org.junit.jupiter.api.Test
    public void testCustomTimeline(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);
        addLabel(schema, "timestamp", null, new Extractor("ts", "$.timestamp", false));
        addChangeDetectionVariable(test, schema.id);
        setChangeDetectionTimeline(test, Collections.singletonList("timestamp"), null);

        BlockingQueue<DataPoint.Event> datapointQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATAPOINT_NEW,
                test.id);

        long ts = System.currentTimeMillis();
        uploadRun(ts, ts, runWithValue(1, schema).put("timestamp", 1662023776000L), test.name);
        DataPoint.Event dp11 = datapointQueue.poll(10, TimeUnit.SECONDS);
        assertEquals(Instant.ofEpochSecond(1662023776), dp11.dataPoint.timestamp);

        uploadRun(ts - 1, ts - 1, runWithValue(2, schema).put("timestamp", "1662023777000"), test.name);
        DataPoint.Event dp12 = datapointQueue.poll(10, TimeUnit.SECONDS);
        assertEquals(Instant.ofEpochSecond(1662023777), dp12.dataPoint.timestamp);

        uploadRun(ts + 1, ts + 1, runWithValue(3, schema).put("timestamp", "2022-09-01T11:16:18+02:00"), test.name);
        DataPoint.Event dp13 = datapointQueue.poll(10, TimeUnit.SECONDS);
        assertEquals(Instant.ofEpochSecond(1662023778), dp13.dataPoint.timestamp);

        setChangeDetectionTimeline(test, Collections.singletonList("timestamp"), "timestamp => timestamp");
        // The DataSets will be recalculated based on DataSet.start, not DataPoint.timestamp
        recalculateDatapoints(test.id);
        DataPoint.Event dp22 = datapointQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(dp22);
        assertEquals(Instant.ofEpochSecond(1662023777), dp22.dataPoint.timestamp);
        DataPoint.Event dp21 = datapointQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(dp21);
        assertEquals(Instant.ofEpochSecond(1662023776), dp21.dataPoint.timestamp);
        DataPoint.Event dp23 = datapointQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(dp23);
        assertEquals(Instant.ofEpochSecond(1662023778), dp23.dataPoint.timestamp);

        setChangeDetectionTimeline(test, Arrays.asList("timestamp", "value"), "({ timestamp, value }) => timestamp");
        recalculateDatapoints(test.id);
        DataPoint.Event dp32 = datapointQueue.poll(10, TimeUnit.SECONDS);
        assertEquals(Instant.ofEpochSecond(1662023777), dp32.dataPoint.timestamp);
        DataPoint.Event dp31 = datapointQueue.poll(10, TimeUnit.SECONDS);
        assertEquals(Instant.ofEpochSecond(1662023776), dp31.dataPoint.timestamp);
        DataPoint.Event dp33 = datapointQueue.poll(10, TimeUnit.SECONDS);
        assertEquals(Instant.ofEpochSecond(1662023778), dp33.dataPoint.timestamp);
    }

    private void setChangeDetectionTimeline(Test test, List<String> labels, String function) {
        ObjectNode update = JsonNodeFactory.instance.objectNode();
        update.putArray("timelineLabels")
                .addAll(labels.stream().map(JsonNodeFactory.instance::textNode).collect(Collectors.toList()));
        update.put("timelineFunction", function);
        jsonRequest().queryParam("testId", test.id).body(update).post("/api/alerting/changeDetection").then().statusCode(204);
    }

    @org.junit.jupiter.api.Test
    public void testLabelsChange(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);
        addChangeDetectionVariable(test, schema.id);
        LabelDAO l = LabelDAO.find("name", "value").firstResult();
        Label label = LabelMapper.from(l);

        BlockingQueue<DataPoint.Event> datapointQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATAPOINT_NEW,
                test.id);

        long ts = System.currentTimeMillis();
        uploadRun(ts, ts, runWithValue(1, schema), test.name);
        DataPoint.Event dpe1 = datapointQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(dpe1);

        // The datapoint will have to be recalculated due to label function update
        updateLabel(schema, label.id, label.name, "val => val", label.extractors.toArray(Extractor[]::new));
        DataPoint.Event dpe2 = datapointQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(dpe2);

        em.clear();
        // the old datapoint should be deleted
        assertEquals(1, DataPointDAO.count());
    }

    //we need to find a way to determine when the re-calculation is complete
    @org.junit.jupiter.api.Disabled
    public void testRandomOrder(TestInfo info) throws InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);
        addChangeDetectionVariable(test, 0.1, 1, schema.id);
        addLabel(schema, "timestamp", null, new Extractor("ts", "$.timestamp", false));
        setChangeDetectionTimeline(test, Collections.singletonList("timestamp"), null);

        BlockingQueue<DataPoint.Event> datapointQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATAPOINT_NEW,
                test.id);
        BlockingQueue<Change.Event> changeQueue = serviceMediator.getEventQueue(AsyncEventChannels.CHANGE_NEW, test.id);

        int[] order = new int[] { 5, 0, 1, 7, 4, 8, 2, 3, 9, 6 };
        double[] values = new double[] { 1, 2, 2, 2, 1, 1, 2, 1, 1, 2 };
        assertEquals(order.length, values.length);

        long now = System.currentTimeMillis();
        for (int i = 0; i < order.length; ++i) {
            uploadRun(now + i, runWithValue(values[order[i]], schema).put("timestamp", order[i]), test.name);
            Thread.sleep(100); //add sleep, might make the test fail less often
        }
        drainQueue(datapointQueue, order.length);
        drainQueue(changeQueue);
        checkChanges(test);

        em.clear();
        recalculateDatapoints(test.id);
        drainQueue(datapointQueue, order.length);
        drainQueue(changeQueue);
        //      Thread.sleep(2000);
        checkChanges(test);

        em.clear();
        recalculateDatasets(test.id, true);
        drainQueue(datapointQueue, order.length);
        drainQueue(changeQueue);
        //      Thread.sleep(2000);
        checkChanges(test);
    }

    @org.junit.jupiter.api.Test
    public void testFindLastDatapoints(TestInfo info) throws IOException {
        populateDataFromFiles();
        AlertingService.LastDatapointsParams params = new AlertingService.LastDatapointsParams();
        params.fingerprint = mapper.valueToTree("{ \"fingerprint\":\"benchmark_test\"}").asText();
        params.variables = new int[] { 1, 2, 3 };

        List<AlertingService.DatapointLastTimestamp> timestamps = jsonRequest().body(params)
                .post("/api/alerting/datapoint/last")
                .then().statusCode(200).extract().body()
                .as(new ParameterizedTypeImpl(List.class, AlertingService.DatapointLastTimestamp.class));
    }

    @org.junit.jupiter.api.Test
    public void testUpdateVariablesHandlesNegativeId(TestInfo info) throws Exception {
        Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);
        ChangeDetection cd = addChangeDetectionVariable(test, schema.id);

        cd.config.put("filter", "min");
        setTestVariables(test, "Value", new Label("value", schema.id), cd);//create

        List<Variable> variables = variables(test.id);
        assertEquals(1, variables.size());
        assertNotNull(variables.get(0).id);

        ChangeDetection problematicChangeDetection = new ChangeDetection();
        problematicChangeDetection.id = -1; //UI typically sets this value
        problematicChangeDetection.model = ChangeDetectionModelType.names.RELATIVE_DIFFERENCE;
        problematicChangeDetection.config = JsonNodeFactory.instance.objectNode().put("threshold", 0.2).put("minPrevious", 2)
                .put("window", 2).put("filter", "mean");
        List<String> labels = Collections.singletonList("foobar");
        Set<ChangeDetection> cdSet = Collections.singleton(problematicChangeDetection);

        Variable throughput = new Variable();
        throughput.id = -1; //UI typically sets this value
        throughput.testId = test.id;
        throughput.name = "throughput";
        throughput.labels = labels;
        throughput.changeDetection = cdSet;
        variables.add(throughput);

        updateVariables(test.id, variables);//update
        List<Variable> updated = variables(test.id);
        Variable updatedThroughput = updated.stream().filter(v -> v.name.equals(throughput.name)).findFirst().get();
        assertNotEquals(-1, updatedThroughput.id);
        assertEquals(throughput.changeDetection.size(), updatedThroughput.changeDetection.size());
        ChangeDetection updatedChangeDetection = updatedThroughput.changeDetection.stream().findFirst().get();
        assertNotEquals(-1, updatedChangeDetection.id);
    }

    private void checkChanges(Test test) {
        List<ChangeDAO> list = ChangeDAO.list("variable.testId", test.id);
        assertEquals(Arrays.asList(1L, 4L, 6L, 7L, 9L),
                list.stream().map(c -> c.timestamp.toEpochMilli()).sorted().collect(Collectors.toList()));
    }

    private void drainQueue(BlockingQueue<DataPoint.Event> datapointQueue, int expectedItems) throws InterruptedException {
        for (int i = 0; i < expectedItems; ++i) {
            DataPoint.Event event = datapointQueue.poll(10, TimeUnit.SECONDS);
            assertNotNull(event);
        }
    }

    private void drainQueue(BlockingQueue<Change.Event> changeQueue) throws InterruptedException {
        // we don't know exactly how many changes are going to be created and deleted
        for (;;) {
            Change.Event changeEvent = changeQueue.poll(100, TimeUnit.MILLISECONDS);
            if (changeEvent == null) {
                return;
            }
        }
    }
}
