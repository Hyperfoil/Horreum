package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.hibernate.query.NativeQuery;
import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType;

import io.hyperfoil.tools.horreum.action.ExperimentResultToMarkdown;
import io.hyperfoil.tools.horreum.api.ExperimentService;
import io.hyperfoil.tools.horreum.api.TestService;
import io.hyperfoil.tools.horreum.entity.ExperimentProfile;
import io.hyperfoil.tools.horreum.entity.alerting.ChangeDetection;
import io.hyperfoil.tools.horreum.entity.alerting.MissingDataRule;
import io.hyperfoil.tools.horreum.entity.alerting.Variable;
import io.hyperfoil.tools.horreum.entity.alerting.Watch;
import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.entity.json.Action;
import io.hyperfoil.tools.horreum.entity.json.Extractor;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Schema;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.entity.json.Transformer;
import io.hyperfoil.tools.horreum.entity.json.View;
import io.hyperfoil.tools.horreum.entity.json.ViewComponent;
import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.test.NoGrafanaProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.hyperfoil.tools.horreum.test.TestUtil;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(NoGrafanaProfile.class)
public class TestServiceTest extends BaseServiceTest {

   @org.junit.jupiter.api.Test
   public void testCreateDelete(TestInfo info) throws InterruptedException {

      Test test = createTest(createExampleTest(getTestName(info)));
      try (CloseMe ignored = roleManager.withRoles(Arrays.asList(TESTER_ROLES))) {
         assertNotNull(Test.findById(test.id));
      }

      int runId = uploadRun("{ \"foo\" : \"bar\" }", test.name);

      deleteTest(test);
      BlockingQueue<Integer> events = eventConsumerQueue(Integer.class, Run.EVENT_TRASHED, id -> id == runId);
      assertNotNull(events.poll(10, TimeUnit.SECONDS));

      em.clear();
      try (CloseMe ignored = roleManager.withRoles(Arrays.asList(TESTER_ROLES))) {
         assertNull(Test.findById(test.id));
         // There's no constraint between runs and tests; therefore the run is not deleted
         Run run = Run.findById(runId);
         assertNotNull(run);
         assertTrue(run.trashed);

         assertEquals(0, DataSet.count("testid", test.id));
      }
   }

   @org.junit.jupiter.api.Test
   public void testRecalculate(TestInfo info) throws InterruptedException {
      Test test = createTest(createExampleTest(getTestName(info)));
      Schema schema = createExampleSchema(info);

      BlockingQueue<DataSet.EventNew> newDatasetQueue = eventConsumerQueue(DataSet.EventNew.class, DataSet.EVENT_NEW, e -> e.dataset.testid.equals(test.id));
      final int NUM_DATASETS = 5;
      for (int i = 0; i < NUM_DATASETS; ++i) {
         uploadRun(runWithValue(i, schema), test.name);
         DataSet.EventNew event = newDatasetQueue.poll(10, TimeUnit.SECONDS);
         assertNotNull(event);
         assertFalse(event.isRecalculation);
      }
      List<DataSet> datasets = DataSet.list("testid", test.id);
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
         DataSet.EventNew event = newDatasetQueue.poll(10, TimeUnit.SECONDS);
         assertNotNull(event);
         assertTrue(event.dataset.id > maxId);
         assertTrue(event.isRecalculation);
      }
      datasets = DataSet.list("testid", test.id);
      assertEquals(NUM_DATASETS, datasets.size());
      datasets.forEach(ds -> {
         assertTrue(ds.id > maxId);
         assertEquals(0, ds.ordinal);
      });
      assertEquals(NUM_DATASETS, datasets.stream().map(ds -> ds.run.id).collect(Collectors.toSet()).size());
   }

   @org.junit.jupiter.api.Test
   public void testAddTestAction(TestInfo info) {
      Test test = createTest(createExampleTest(getTestName(info)));
      addTestHttpAction(test, Run.EVENT_NEW, "https://attacker.ru").then().statusCode(400);

      addAllowedSite("https://example.com");

      Action action = addTestHttpAction(test, Run.EVENT_NEW, "https://example.com/foo/bar").then().statusCode(200).extract().body().as(Action.class);
      assertNotNull(action.id);
      assertTrue(action.active);
      action.active = false;
      jsonRequest().body(action).post("/api/test/" + test.id + "/action").then().statusCode(204);
   }

   @org.junit.jupiter.api.Test
   public void testUpdateView(TestInfo info) throws InterruptedException {
      Test test = createTest(createExampleTest(getTestName(info)));
      Schema schema = createExampleSchema(info);

      BlockingQueue<DataSet.EventNew> newDatasetQueue = eventConsumerQueue(DataSet.EventNew.class, DataSet.EVENT_NEW, e -> e.dataset.testid.equals(test.id));
      uploadRun(runWithValue(42, schema), test.name);
      DataSet.EventNew event = newDatasetQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(event);

      ViewComponent vc = new ViewComponent();
      vc.headerName = "Foobar";
      vc.labels = JsonNodeFactory.instance.arrayNode().add("value");
      View defaultView = test.views.stream().filter(v -> "Default".equals(v.name)).findFirst().orElseThrow();
      defaultView.components.add(vc);
      updateView(test.id, defaultView);

      TestUtil.eventually(() -> {
         em.clear();
         @SuppressWarnings("unchecked") List<JsonNode> list = em.createNativeQuery(
               "SELECT value FROM dataset_view WHERE dataset_id = ?1 AND view_id = ?2")
               .setParameter(1, event.dataset.id).setParameter(2, defaultView.id)
               .unwrap(NativeQuery.class).addScalar("value", JsonNodeBinaryType.INSTANCE)
               .getResultList();
         return !list.isEmpty() && !list.get(0).isEmpty();
      });
   }

   private void updateView(int testId, View view) {
      Integer viewId = jsonRequest().body(view).post("/api/test/" + testId + "/view")
            .then().statusCode(200).extract().body().as(Integer.class);
      if (view.id != null) {
         assertEquals(view.id, viewId);
      }
   }

   @org.junit.jupiter.api.Test
   public void testLabelValues(TestInfo info) throws InterruptedException {
      Test test = createTest(createExampleTest(getTestName(info)));
      Schema schema = createExampleSchema(info);

      BlockingQueue<DataSet.LabelsUpdatedEvent> newDatasetQueue = eventConsumerQueue(DataSet.LabelsUpdatedEvent.class, DataSet.EVENT_LABELS_UPDATED, e -> checkTestId(e.datasetId, test.id));
      uploadRun(runWithValue(42, schema), test.name);
      uploadRun(JsonNodeFactory.instance.objectNode(), test.name);
      assertNotNull(newDatasetQueue.poll(10, TimeUnit.SECONDS));
      assertNotNull(newDatasetQueue.poll(10, TimeUnit.SECONDS));

      String response = jsonRequest().get("/api/test/" + test.id + "/labelValues").then().statusCode(200).
            extract().body().asString();
      JsonNode obj = Util.toJsonNode(response);
      assertNotNull(obj);
      assertTrue(obj.isArray());
      assertEquals(1, StreamSupport.stream(obj.spliterator(), false).filter(item -> item.size() == 0).count());
      assertEquals(1, StreamSupport.stream(obj.spliterator(), false).filter(item -> item.size() == 1 && item.has("value")).count());
      assertEquals(2, obj.size());
   }

   @org.junit.jupiter.api.Test
   public void testImportExportWithWipe() {
      testImportExport(true);
   }

   @org.junit.jupiter.api.Test
   public void testImportExportWithoutWipe() {
      testImportExport(false);
   }

   private void testImportExport(boolean wipe) {
      Schema schema = createSchema("Example", "urn:example:1.0");
      Transformer transformer = createTransformer("Foobar", schema, null, new Extractor("foo", "$.foo", false));

      Test test = createTest(createExampleTest("to-be-exported"));
      addToken(test, 5, "some-secret-string");
      addTransformer(test, transformer);
      View view = new View();
      view.name = "Another";
      ViewComponent vc = new ViewComponent();
      vc.labels = JsonNodeFactory.instance.arrayNode().add("foo");
      vc.headerName = "Some foo";
      view.components = Collections.singletonList(vc);
      updateView(test.id, view);

      addTestHttpAction(test, Run.EVENT_NEW, "http://example.com");
      addTestGithubIssueCommentAction(test, ExperimentService.ExperimentResult.NEW_RESULT,
            ExperimentResultToMarkdown.NAME, "hyperfoil", "horreum", "123", "super-secret-github-token");

      addChangeDetectionVariable(test);
      addMissingDataRule(test, "Let me know", JsonNodeFactory.instance.arrayNode().add("foo"), null,
            (int) TimeUnit.DAYS.toMillis(1));

      addExperimentProfile(test, "Some profile", Variable.<Variable>listAll().get(0));
      addSubscription(test);

      HashMap<String, List<JsonNode>> db = dumpDatabaseContents();

      String testJson = jsonRequest().get("/api/test/" + test.id + "/export").then()
            .statusCode(200).extract().body().asString();

      if (wipe) {
         deleteTest(test);

         TestUtil.eventually(() -> {
            em.clear();
            try (var h = roleManager.withRoles(Collections.singleton(Roles.HORREUM_SYSTEM))) {
               assertEquals(0, Test.count());
               assertEquals(0, Action.count());
               assertEquals(0, Variable.count());
               assertEquals(0, ChangeDetection.count());
               assertEquals(0, MissingDataRule.count());
               assertEquals(0, ExperimentProfile.count());
               assertEquals(0, Watch.count());
            }
         });
      }

      jsonRequest().body(testJson).post("/api/test/import").then().statusCode(204);

      validateDatabaseContents(db);
   }

   private void addSubscription(Test test) {
      Watch watch = new Watch();
      watch.test = test;
      watch.users = Arrays.asList("john", "bill");
      watch.teams = Collections.singletonList("dev-team");
      watch.optout = Collections.singletonList("ignore-me");

      jsonRequest().body(watch).post("/api/subscriptions/" + test.id);
   }

}
