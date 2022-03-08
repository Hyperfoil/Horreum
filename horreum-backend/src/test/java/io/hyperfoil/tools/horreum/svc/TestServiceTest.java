package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import javax.inject.Inject;
import javax.persistence.EntityManager;

import org.junit.jupiter.api.TestInfo;

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

}
