package io.hyperfoil.tools.horreum.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import javax.inject.Inject;
import javax.persistence.EntityManager;

import org.junit.jupiter.api.TestInfo;

import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.test.NoGrafanaProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.RestAssured;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(NoGrafanaProfile.class)
public class TestServiceTest extends BaseServiceTest {

   @Inject
   EntityManager em;

   @Inject
   SqlService sqlService;

   @org.junit.jupiter.api.Test
   public void testCreateDelete(TestInfo info) {

      // create test
      Test test = createExampleTest(getTestName(info));
      Test response = createTest(test);
      try (CloseMe ignored = sqlService.withRoles(em, Arrays.asList(TESTER_ROLES))) {
         assertNotNull(Test.findById(response.id));
      }

      // add run to the test
      long timestamp = System.currentTimeMillis();
      int runId = uploadRun(timestamp, timestamp, "{ \"foo\" : \"bar\" }", test.name);

      // delete run
      RestAssured.given().auth().oauth2(TESTER_TOKEN)
            .delete("/api/test/" + response.id)
            .then()
            .statusCode(204);
      em.clear();
      try (CloseMe ignored = sqlService.withRoles(em, Arrays.asList(TESTER_ROLES))) {
         assertNull(Test.findById(response.id));
         // There's no constraint between runs and tests; therefore the run is not deleted
         Run run = Run.findById(runId);
         assertNotNull(run);
         assertTrue(run.trashed);
      }
   }

}
