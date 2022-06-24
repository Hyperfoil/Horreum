package io.hyperfoil.tools.horreum.svc;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.ws.rs.core.HttpHeaders;

import org.hibernate.query.NativeQuery;
import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType;

import io.hyperfoil.tools.horreum.api.TestService;
import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.entity.json.Hook;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Schema;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.entity.json.View;
import io.hyperfoil.tools.horreum.entity.json.ViewComponent;
import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.server.RoleManager;
import io.hyperfoil.tools.horreum.test.NoGrafanaProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.response.Response;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(NoGrafanaProfile.class)
public class TestServiceTest extends BaseServiceTest {

   @Inject
   EntityManager em;

   @Inject
   RoleManager roleManager;

   @org.junit.jupiter.api.Test
   public void testCreateDelete(TestInfo info) {

      Test test = createTest(createExampleTest(getTestName(info)));
      try (CloseMe ignored = roleManager.withRoles(em, Arrays.asList(TESTER_ROLES))) {
         assertNotNull(Test.findById(test.id));
      }

      int runId = uploadRun("{ \"foo\" : \"bar\" }", test.name);

      deleteTest(test);
      em.clear();
      try (CloseMe ignored = roleManager.withRoles(em, Arrays.asList(TESTER_ROLES))) {
         assertNull(Test.findById(test.id));
         // There's no constraint between runs and tests; therefore the run is not deleted
         Run run = Run.findById(runId);
         assertNotNull(run);
         assertTrue(run.trashed);
      }
   }

   @org.junit.jupiter.api.Test
   public void testRecalculate(TestInfo info) throws InterruptedException {
      Test test = createTest(createExampleTest(getTestName(info)));
      Schema schema = createExampleSchema(info);

      BlockingQueue<DataSet.EventNew> newDatasetQueue = eventConsumerQueue(DataSet.EventNew.class, DataSet.EVENT_NEW);
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
      eventually(() -> {
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
   public void testAddTestHook(TestInfo info) {
      Test test = createTest(createExampleTest(getTestName(info)));
      addTestHook(test, Run.EVENT_NEW, "https://attacker.ru").then().statusCode(400);;

      addAllowedPrefix("https://example.com");

      Hook hook = addTestHook(test, Run.EVENT_NEW, "https://example.com/foo/bar").then().statusCode(200).extract().body().as(Hook.class);
      assertNotNull(hook.id);
      assertTrue(hook.active);
      hook.active = false;
      jsonRequest().body(hook).post("/api/test/" + test.id + "/hook").then().statusCode(204);
   }

   private void addAllowedPrefix(String prefix) {
      given().auth().oauth2(ADMIN_TOKEN).header(HttpHeaders.CONTENT_TYPE, "text/plain")
            .body(prefix).post("/api/hook/prefixes").then().statusCode(200);
   }

   private Response addTestHook(Test test, String type, String url) {
      Hook hook = new Hook();
      hook.type = type;
      hook.active = true;
      hook.url = url;
      return jsonRequest().body(hook).post("/api/test/" + test.id + "/hook");
   }

   @org.junit.jupiter.api.Test
   public void testAddGlobalHook() {
      String responseType = addGlobalHook(Test.EVENT_NEW, "https://attacker.ru")
            .then().statusCode(400).extract().header(HttpHeaders.CONTENT_TYPE);
      // constraint violations are mapped to 400 + JSON response, we want explicit error
      assertTrue(responseType.startsWith("text/plain")); // text/plain;charset=UTF-8

      addAllowedPrefix("https://example.com");

      Hook hook = addGlobalHook(Test.EVENT_NEW, "https://example.com/foo/bar").then().statusCode(200).extract().body().as(Hook.class);
      assertNotNull(hook.id);
      assertTrue(hook.active);
      given().auth().oauth2(ADMIN_TOKEN).delete("/api/hook/" + hook.id);
   }

   private Response addGlobalHook(String type, String url) {
      Hook hook = new Hook();
      hook.type = type;
      hook.active = true;
      hook.url = url;
      return  given().auth().oauth2(ADMIN_TOKEN).header(HttpHeaders.CONTENT_TYPE, "application/json").body(hook).post("/api/hook");
   }

   @org.junit.jupiter.api.Test
   public void testUpdateView(TestInfo info) throws InterruptedException {
      Test test = createTest(createExampleTest(getTestName(info)));
      Schema schema = createExampleSchema(info);

      BlockingQueue<DataSet.EventNew> newDatasetQueue = eventConsumerQueue(DataSet.EventNew.class, DataSet.EVENT_NEW);
      uploadRun(runWithValue(42, schema), test.name);
      DataSet.EventNew event = newDatasetQueue.poll(10, TimeUnit.SECONDS);
      assertNotNull(event);

      ViewComponent vc = new ViewComponent();
      vc.headerName = "Foobar";
      vc.labels = JsonNodeFactory.instance.arrayNode().add("value");
      test.defaultView.components.add(vc);
      updateView(test.id, test.defaultView);

      eventually(() -> {
         em.clear();
         List<JsonNode> list = em.createNativeQuery("SELECT value FROM dataset_view WHERE dataset_id = ?1 AND view_id = ?2")
               .setParameter(1, event.dataset.id).setParameter(2, test.defaultView.id)
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
}
