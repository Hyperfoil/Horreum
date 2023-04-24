package io.hyperfoil.tools.horreum.svc;

import static io.hyperfoil.tools.horreum.test.TestUtil.eventually;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.hyperfoil.tools.horreum.entity.ActionLog;
import io.hyperfoil.tools.horreum.entity.json.Action;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.test.NoGrafanaProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(NoGrafanaProfile.class)
public class ActionServiceTest extends BaseServiceTest {

   @org.junit.jupiter.api.Test
   public void testFailingHttp(TestInfo testInfo) {
      Test test = createTest(createExampleTest(getTestName(testInfo)));

      addAllowedSite("http://some-non-existent-domain.com");
      addTestHttpAction(test, Run.EVENT_NEW, "http://some-non-existent-domain.com");

      uploadRun(JsonNodeFactory.instance.objectNode(), test.name);

      eventually(() -> (Boolean) Util.withTx(tm, () -> {
         em.clear();
         try (CloseMe ignored = roleManager.withRoles(Arrays.asList(TESTER_ROLES))) {
            return ActionLog.find("testId", test.id).count() == 1;
         }
      }));
   }

   @org.junit.jupiter.api.Test
   public void testAddGlobalAction() {
      String responseType = addGlobalAction(Test.EVENT_NEW, "https://attacker.com")
            .then().statusCode(400).extract().header(javax.ws.rs.core.HttpHeaders.CONTENT_TYPE);
      // constraint violations are mapped to 400 + JSON response, we want explicit error
      assertTrue(responseType.startsWith("text/plain")); // text/plain;charset=UTF-8

      addAllowedSite("https://example.com");

      Action action = addGlobalAction(Test.EVENT_NEW, "https://example.com/foo/bar").then().statusCode(200).extract().body().as(Action.class);
      assertNotNull(action.id);
      assertTrue(action.active);
      given().auth().oauth2(getAdminToken()).delete("/api/action/" + action.id);
   }
}
