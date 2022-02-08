package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import javax.inject.Inject;
import javax.persistence.EntityManager;

import org.junit.jupiter.api.TestInfo;

import com.arjuna.ats.jta.exceptions.RollbackException;

import io.hyperfoil.tools.horreum.entity.alerting.Watch;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.server.RoleManager;
import io.hyperfoil.tools.horreum.test.NoGrafanaProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;


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
   public void testDisableMuteMissingRuns(TestInfo info) throws RollbackException{

      // create test
      Test test = createExampleTest(getTestName(info));
      Test response = createTest(test);
      Watch watch = new Watch();
      test = checkTestAsExpected(test, response, watch);
      watch = checkWatchAsExpected(test, watch);
      watch.mutemissingruns = true;
      updateWatch(test, watch);
   }

   private Test checkTestAsExpected(Test test, Test response, Watch watch) {
      assertNotNull(response.id);
      Test t = null;
      try (CloseMe ignored = roleManager.withRoles(em, Arrays.asList(TESTER_ROLES))) {
         t = Test.findById(response.id);
      }
      assertNotNull(t.id);
      RestAssured.given().auth().oauth2(TESTER_TOKEN)
         .body(watch)
         .contentType(ContentType.JSON)
         .post("/api/subscriptions/" + response.id)
         .then()
         .statusCode(204);

      return t;
   }

   private Watch checkWatchAsExpected(Test t, Watch watch) {
      try (CloseMe ignored = roleManager.withRoles(em, Arrays.asList(TESTER_ROLES))) {
         Watch existing = Watch.find("testid", t.id).firstResult();
         assertNotNull(existing);
         assertFalse(existing.mutemissingruns);
         assertNotNull(existing.id);
         assertNotNull(existing.test.id);
         watch.id = existing.id;
         watch.test.id = existing.test.id;
         watch.optout = existing.optout;
         watch.teams = existing.teams;
         return watch;
      }
   }

   private void updateWatch(Test test, Watch watch) {
      RestAssured.given().auth().oauth2(TESTER_TOKEN)
         .body(watch)
         .contentType(ContentType.JSON)
         .post("/api/subscriptions/" + test.id)
         .then()
         .statusCode(204);
   }

}
