package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import javax.inject.Inject;
import javax.persistence.EntityManager;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.TestInfo;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.AlertingService;
import io.hyperfoil.tools.horreum.entity.Fingerprint;
import io.hyperfoil.tools.horreum.entity.alerting.DatasetLog;
import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;
import io.hyperfoil.tools.horreum.entity.alerting.ChangeDetection;
import io.hyperfoil.tools.horreum.entity.alerting.MissingDataRule;
import io.hyperfoil.tools.horreum.entity.alerting.MissingDataRuleResult;
import io.hyperfoil.tools.horreum.entity.alerting.RunExpectation;
import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.entity.json.Extractor;
import io.hyperfoil.tools.horreum.entity.json.Schema;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.changedetection.RelativeDifferenceChangeDetectionModel;
import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.server.RoleManager;
import io.hyperfoil.tools.horreum.test.NoGrafanaProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.arc.impl.ParameterizedTypeImpl;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.RestAssured;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(NoGrafanaProfile.class)
public class AlertingServiceTest extends BaseServiceTest {
   private static final Logger log = Logger.getLogger(AlertingServiceTest.class);

   @Inject
   EventBus eventBus;

   @Inject
   EntityManager em;

   @Inject
   RoleManager roleManager;

   @Inject
   Vertx vertx;

   @Inject
   AlertingServiceImpl alertingService;

   @org.junit.jupiter.api.Test
   public void testNotifications(TestInfo info) throws InterruptedException {
      Test test = createTest(createExampleTest(getTestName(info)));
      Schema schema = createExampleSchema(info);
      setTestVariables(test, "Value", "value");

      BlockingQueue<DataPoint.Event> dpe = eventConsumerQueue(DataPoint.Event.class, DataPoint.EVENT_NEW);
      uploadRun(runWithValue(schema, 42).toString(), test.name);

      DataPoint.Event event1 = dpe.poll(10, TimeUnit.SECONDS);
      assertNotNull(event1);
      assertEquals(42d, event1.dataPoint.value);
      assertTrue(event1.notify);

      RestAssured.given().auth().oauth2(TESTER_TOKEN)
            .post("/api/test/" + test.id + "/notifications?enabled=false")
            .then().statusCode(204);

      uploadRun(runWithValue(schema, 0).toString(), test.name);

      DataPoint.Event event2 = dpe.poll(10, TimeUnit.SECONDS);
      assertNotNull(event2);
      assertEquals(0, event2.dataPoint.value);
      assertFalse(event2.notify);
   }

   private ObjectNode runWithValue(Schema schema, double value) {
      ObjectNode runJson = JsonNodeFactory.instance.objectNode();
      runJson.put("$schema", schema.uri);
      runJson.put("value", value);
      return runJson;
   }

   @org.junit.jupiter.api.Test
   public void testLogging(TestInfo info) throws InterruptedException, ExecutionException, TimeoutException {
      Test test = createTest(createExampleTest(getTestName(info)));
      Schema schema = createExampleSchema(info);
      setTestVariables(test, "Value", "value");

      // This run won't contain the 'value'
      ObjectNode runJson = JsonNodeFactory.instance.objectNode();
      runJson.put("$schema", schema.uri);

      BlockingQueue<MissingValuesEvent> missingQueue = eventConsumerQueue(MissingValuesEvent.class, DataSet.EVENT_MISSING_VALUES);
      missingQueue.drainTo(new ArrayList<>());
      int runId = uploadRun(runJson, test.name);

      assertNotNull(missingQueue.poll(10, TimeUnit.SECONDS));

      try (CloseMe ignored = roleManager.withRoles(em, Arrays.asList(TESTER_ROLES))) {
         List<DatasetLog> logs = DatasetLog.find("dataset.run.id", runId).list();
         // If this fails this might be a race - I thought it's fixed with quarkus.datasource.jdbc.transaction-isolation-level=serializable
         assertTrue(logs.size() > 0);

         deleteTest(test);

         eventually(() -> {
            em.clear();
            List<DatasetLog> currentLogs = DatasetLog.find("dataset.run.id", runId).list();
            assertEquals(0, currentLogs.size());
         });
      }
   }

   @org.junit.jupiter.api.Test
   public void testChangeDetection(TestInfo info) throws InterruptedException {
      Test test = createTest(createExampleTest(getTestName(info)));
      Schema schema = createExampleSchema(info);
      ChangeDetection cd = addChangeDetectionVariable(test);

      BlockingQueue<DataPoint.Event> datapointQueue = eventConsumerQueue(DataPoint.Event.class, DataPoint.EVENT_NEW);
      BlockingQueue<Change.Event> changeQueue = eventConsumerQueue(Change.Event.class, Change.EVENT_NEW);

      long ts = System.currentTimeMillis();
      uploadRun(ts, ts, runWithValue(schema, 1), test.name);
      uploadRun(ts + 1, ts + 1, runWithValue(schema, 2), test.name);
      int run3 = uploadRun(ts + 2, ts + 2, runWithValue(schema, 1), test.name);
      int run4 = uploadRun(ts + 3, ts + 3, runWithValue(schema, 2), test.name);

      assertValue(datapointQueue, 1);
      assertValue(datapointQueue, 2);
      assertValue(datapointQueue, 1);
      assertValue(datapointQueue, 2);

      assertNull(changeQueue.poll(50, TimeUnit.MILLISECONDS));

      uploadRun(ts + 4, ts + 4, runWithValue(schema, 3), test.name);
      assertValue(datapointQueue, 3);

      Change.Event changeEvent1 = changeQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(changeEvent1);
      // The change is detected already at run 4 because it's > than the previous mean
      assertEquals(run4, changeEvent1.change.dataset.id);

      ((ObjectNode) cd.config).put("filter", "min");
      setTestVariables(test, "Value", "value", cd);
      // After changing the variable the past datapoints and changes are removed; we need to recalculate them again
      jsonRequest().post("/api/alerting/recalculate?test=" + test.id).then().statusCode(204);

      assertValue(datapointQueue, 1);
      assertValue(datapointQueue, 2);
      assertValue(datapointQueue, 1);
      assertValue(datapointQueue, 2);
      assertValue(datapointQueue, 3);

      // now we'll find a change already at run3
      Change.Event changeEvent2 = changeQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(changeEvent2);
      assertEquals(run3, changeEvent2.change.dataset.id);

      int run6 = uploadRun(ts + 5, ts + 5, runWithValue(schema, 1.5), test.name);
      assertValue(datapointQueue, 1.5);

      // mean of previous values is 1.5, now the min is 1.5 => no change
      assertNull(changeQueue.poll(50, TimeUnit.MILLISECONDS));

      uploadRun(ts + 6, ts + 6, runWithValue(schema, 2), test.name);
      assertValue(datapointQueue, 2);

      // mean of previous is 2, the last value doesn't matter (1.5 is lower than 2 - 10%)
      Change.Event changeEvent3 = changeQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(changeEvent3);
      assertEquals(run6, changeEvent3.change.dataset.id);
   }

   @org.junit.jupiter.api.Test
   public void testChangeDetectionWithFingerprint(TestInfo info) throws InterruptedException {
      Test test = createExampleTest(getTestName(info));
      test.fingerprintLabels = jsonArray("config");
      test = createTest(test);
      Schema schema = createExampleSchema(info);
      addLabel(schema, "config", null, new Extractor("config", "$.config", false));

      addChangeDetectionVariable(test);

      BlockingQueue<DataPoint.Event> datapointQueue = eventConsumerQueue(DataPoint.Event.class, DataPoint.EVENT_NEW);
      BlockingQueue<Change.Event> changeQueue = eventConsumerQueue(Change.Event.class, Change.EVENT_NEW);

      long ts = System.currentTimeMillis();
      for (int i = 0; i < 12; i += 3) {
         uploadRun(ts + i, ts + i, runWithValue(schema, 1).put("config", "foo"), test.name);
         assertValue(datapointQueue, 1);
         uploadRun(ts + i + 1, ts + i + 1, runWithValue(schema, 2).put("config", "bar"), test.name);
         assertValue(datapointQueue, 2);
         uploadRun(ts + i + 2, ts + i + 2, runWithValue(schema, 3), test.name);
         assertValue(datapointQueue, 3);
      }
      assertNull(changeQueue.poll(50, TimeUnit.MILLISECONDS));

      int run13 = uploadRun(ts + 12, ts + 12, runWithValue(schema, 2).put("config", "foo"), test.name);
      Change.Event changeEvent1 = changeQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(changeEvent1);
      assertEquals(run13, changeEvent1.change.dataset.id);

      int run14 = uploadRun(ts + 13, ts + 13, runWithValue(schema, 2), test.name);
      Change.Event changeEvent2 = changeQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(changeEvent2);
      assertEquals(run14, changeEvent2.change.dataset.id);
   }

   private ChangeDetection addChangeDetectionVariable(Test test) {
      ChangeDetection rd = new ChangeDetection();
      rd.model = RelativeDifferenceChangeDetectionModel.NAME;
      rd.config = JsonNodeFactory.instance.objectNode().put("threshold", 0.1).put("minPrevious", 2).put("window", 2).put("filter", "mean");
      setTestVariables(test, "Value", "value", rd);
      return rd;
   }

   private void assertValue(BlockingQueue<DataPoint.Event> datapointQueue, double value) throws InterruptedException {
      DataPoint.Event dpe = datapointQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(dpe);
      assertEquals(value, dpe.dataPoint.value);
   }

   @org.junit.jupiter.api.Test
   public void testFingerprintLabelsChange(TestInfo info) throws Exception {
      Test test = createExampleTest(getTestName(info));
      test.fingerprintLabels = jsonArray("foo");
      test = createTest(test);
      addChangeDetectionVariable(test);
      Schema schema = createExampleSchema(info);
      addLabel(schema, "foo", null, new Extractor("foo", "$.foo", false));
      addLabel(schema, "bar", null, new Extractor("bar", "$.bar", false));

      uploadRun(runWithValue(schema, 42).put("foo", "aaa").put("bar", "bbb"), test.name);
      BlockingQueue<DataPoint.Event> datapointQueue = eventConsumerQueue(DataPoint.Event.class, DataPoint.EVENT_NEW);
      assertValue(datapointQueue, 42);

      List<Fingerprint> fingerprintsBefore = Fingerprint.listAll();
      assertEquals(1, fingerprintsBefore.size());
      // When there's just a single label the fingerprint doesn't contain the label name
      assertEquals(JsonNodeFactory.instance.textNode("aaa"), fingerprintsBefore.get(0).fingerprint);

      test.fingerprintLabels = ((ArrayNode) test.fingerprintLabels).add("bar");
      // We'll change the filter here but we do NOT expect to be applied to existing datapoints
      test.fingerprintFilter = "value => false";
      test = createTest(test); // this is update
      // the fingerprint should be updated within the same transaction as test update
      em.clear();
      List<Fingerprint> fingerprintsAfter = Fingerprint.listAll();
      assertEquals(1, fingerprintsAfter.size());
      assertEquals(JsonNodeFactory.instance.objectNode().put("foo", "aaa").put("bar", "bbb"), fingerprintsAfter.get(0).fingerprint);
      assertEquals(fingerprintsBefore.get(0).datasetId, fingerprintsAfter.get(0).datasetId);

      assertEquals(1L, DataPoint.findAll().count());
   }

   @org.junit.jupiter.api.Test
   public void testFingerprintFilter(TestInfo info) throws Exception {
      Test test = createExampleTest(getTestName(info));
      test.fingerprintLabels = jsonArray("foo");
      test.fingerprintFilter = "value => value === 'aaa'";
      test = createTest(test);
      addChangeDetectionVariable(test);
      Schema schema = createExampleSchema(info);
      addLabel(schema, "foo", null, new Extractor("foo", "$.foo", false));
      addLabel(schema, "bar", null, new Extractor("bar", "$.bar", false));

      BlockingQueue<DataPoint.Event> datapointQueue = eventConsumerQueue(DataPoint.Event.class, DataPoint.EVENT_NEW);

      uploadRun(runWithValue(schema, 1).put("foo", "aaa").put("bar", "bbb"), test.name);
      assertValue(datapointQueue, 1);

      // no fingerprint, should not match
      uploadRun(runWithValue(schema, 2), test.name);

      uploadRun(runWithValue(schema, 3).put("foo", "bbb"), test.name);
      assertNull(datapointQueue.poll(50, TimeUnit.MILLISECONDS));
      assertEquals(3, DataSet.count());
      assertEquals(1, DataPoint.count());
      em.clear();

      test.fingerprintLabels = ((ArrayNode) test.fingerprintLabels).add("bar");
      test.fingerprintFilter = "({foo, bar}) => bar !== 'bbb'";
      createTest(test); // update

      uploadRun(runWithValue(schema, 4).put("foo", "bbb").put("bar", "aaa"), test.name);
      assertValue(datapointQueue, 4);
      assertEquals(4, DataSet.count());
      assertEquals(2, DataPoint.count());

      recalculate(test.id);

      List<DataPoint> datapoints = DataPoint.listAll();
      assertEquals(3, datapoints.size());
      assertTrue(datapoints.stream().anyMatch(dp -> dp.value == 2));
      assertTrue(datapoints.stream().anyMatch(dp -> dp.value == 3));
      assertTrue(datapoints.stream().anyMatch(dp -> dp.value == 4));
   }

   private void recalculate(int testId) throws InterruptedException {
      jsonRequest()
            .queryParam("test", testId).queryParam("notify", true).queryParam("debug", true)
            .post("/api/alerting/recalculate")
            .then().statusCode(204);
      for (int i = 0; i < 200; ++i) {
         AlertingService.RecalculationStatus status = jsonRequest().queryParam("test", testId).get("/api/alerting/recalculate")
               .then().statusCode(200).extract().body().as(AlertingService.RecalculationStatus.class);
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
      }).when(notificationService).notifyMissingDataset(Mockito.anyInt(), Mockito.anyString(), Mockito.anyLong(), Mockito.any(Instant.class));
      QuarkusMock.installMockForType(notificationService, NotificationServiceImpl.class);

      Test test = createTest(createExampleTest(getTestName(info)));
      Schema schema = createExampleSchema(info);
      int firstRuleId = addMissingDataRule(test, "my rule", jsonArray("value"), "value => value > 2", 10000);
      assertTrue(firstRuleId > 0);

      BlockingQueue<DataSet> newDatasetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);
      long now = System.currentTimeMillis();
      uploadRun(now - 20000, runWithValue(schema, 3), test.name);
      DataSet firstDataset = newDatasetQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(firstDataset);
      uploadRun(now - 5000, runWithValue(schema, 1), test.name);
      DataSet secondDataset = newDatasetQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(secondDataset);
      // only the matching dataset will be present
      pollMissingDataRuleResultsByRule(firstRuleId, firstDataset.id);

      alertingService.checkMissingDataset();
      assertEquals(1, notifications.size());
      assertEquals("my rule", notifications.get(0));

      // The notification should not fire again because the last notification is now
      alertingService.checkMissingDataset();
      assertEquals(1, notifications.size());

      Util.withTx(tm, () -> {
         try (@SuppressWarnings("unused") CloseMe h = roleManager.withRoles(em, Collections.singletonList(Roles.HORREUM_SYSTEM))) {
            MissingDataRule currentRule = MissingDataRule.findById(firstRuleId);
            assertNotNull(currentRule.lastNotification);
            assertTrue(currentRule.lastNotification.isAfter(Instant.ofEpochMilli(now - 1)));
            // remove the last notification
            currentRule.lastNotification = null;
            currentRule.persistAndFlush();
            return null;
         }
      });
      em.clear();

      int thirdRunId = uploadRun(now - 5000, runWithValue(schema, 3), test.name);
      DataSet thirdDataset = newDatasetQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(thirdDataset);
      pollMissingDataRuleResultsByRule(firstRuleId, firstDataset.id, thirdDataset.id);
      alertingService.checkMissingDataset();
      assertEquals(1, notifications.size());

      em.clear();

      pollMissingDataRuleResultsByDataset(thirdDataset.id, 1);
      trashRun(thirdRunId);
      pollMissingDataRuleResultsByDataset(thirdDataset.id, 0);

      alertingService.checkMissingDataset();
      assertEquals(2, notifications.size());
      assertEquals("my rule", notifications.get(1));

      int otherRuleId = addMissingDataRule(test, null, null, null, 10000);
      pollMissingDataRuleResultsByRule(otherRuleId, firstDataset.id, secondDataset.id);
      alertingService.checkMissingDataset();
      assertEquals(2, notifications.size());

      Util.withTx(tm, () -> {
         try (@SuppressWarnings("unused") CloseMe h = roleManager.withRoles(em, Collections.singletonList(Roles.HORREUM_SYSTEM))) {
            MissingDataRule otherRule = MissingDataRule.findById(otherRuleId);
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
         try (@SuppressWarnings("unused") CloseMe h = roleManager.withRoles(em, Collections.singletonList(Roles.HORREUM_SYSTEM))) {
            MissingDataRule otherRule = MissingDataRule.findById(otherRuleId);
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
      assertEquals(0, MissingDataRuleResult.find("rule_id", otherRuleId).count());
   }

   private void pollMissingDataRuleResultsByRule(int ruleId, int... datasetIds) throws InterruptedException {
      try (CloseMe h = roleManager.withRoles(em, Collections.singletonList(Roles.HORREUM_SYSTEM))) {
         for (int i = 0; i < 1000; ++i) {
            em.clear();
            List<MissingDataRuleResult> results = MissingDataRuleResult.list("rule_id", ruleId);
            if (results.size() == datasetIds.length && results.stream().mapToInt(MissingDataRuleResult::datasetId)
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
      try (CloseMe h = roleManager.withRoles(em, Collections.singletonList(Roles.HORREUM_SYSTEM))) {
         // there's no event when the results are updated, we need to poll
         for (int i = 0; i < 1000; ++i) {
            em.clear();
            List<MissingDataRuleResult> results = MissingDataRuleResult.list("dataset_id", datasetId);
            if (expectedResults == results.size()) {
               return;
            } else {
               Thread.sleep(10);
            }
         }
         fail();
      }
   }

   private int addMissingDataRule(Test test, String ruleName, ArrayNode labels, String condition, int maxStaleness) {
      MissingDataRule rule = new MissingDataRule();
      rule.test = test;
      rule.name = ruleName;
      rule.condition = condition;
      rule.labels = labels;
      rule.maxStaleness = maxStaleness;
      String ruleIdString = jsonRequest().body(rule).post("/api/alerting/missingdatarule?testId=" + test.id).then().statusCode(200).extract().body().asString();
      return Integer.parseInt(ruleIdString);
   }

   private MockedStatic<Clock> mockInstant(AtomicLong current) {
      Clock spyClock = Mockito.spy(Clock.class);
      MockedStatic<Clock> clockMock = Mockito.mockStatic(Clock.class);
      clockMock.when(Clock::systemUTC).thenReturn(spyClock);
      when(spyClock.instant()).thenAnswer(invocation -> Instant.ofEpochMilli(current.get()));
      return clockMock;
   }

   private void withMockedRunExpectations(BiConsumer<AtomicLong, List<String>> consumer) {
      NotificationServiceImpl notificationService = Mockito.mock(NotificationServiceImpl.class);
      List<String> notifications = Collections.synchronizedList(new ArrayList<>());
      Mockito.doAnswer(invocation -> {
         notifications.add(invocation.getArgument(2, String.class));
         return null;
      }).when(notificationService).notifyExpectedRun(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyString(), Mockito.anyString());
      QuarkusMock.installMockForType(notificationService, NotificationServiceImpl.class);

      AtomicLong current = new AtomicLong(System.currentTimeMillis());
      try (var h = mockInstant(current)) {
         consumer.accept(current, notifications);
      }
   }

   @org.junit.jupiter.api.Test
   public void testExpectRunTimeout() {
      Test test = createTest(createExampleTest("timeout"));
      withMockedRunExpectations((current, notifications) -> {
         jsonUploaderRequest().post("/api/alerting/expectRun?test=" + test.name + "&timeout=10&expectedby=foo&backlink=bar").then().statusCode(204);
         List<RunExpectation> expectations = jsonRequest().get("/api/alerting/expectations")
               .then().statusCode(200).extract().body().as(new ParameterizedTypeImpl(List.class, RunExpectation.class));
         assertEquals(1, expectations.size());
         alertingService.checkExpectedRuns();
         assertEquals(0, notifications.size());

         current.addAndGet(20000);
         alertingService.checkExpectedRuns();
         assertEquals(1, notifications.size());
         assertEquals("foo", notifications.get(0));
      });
   }

   @org.junit.jupiter.api.Test
   public void testExpectRunUploaded() {
      Test test = createTest(createExampleTest("uploaded"));
      withMockedRunExpectations((current, notifications) -> {
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
      });
   }
}
