package io.hyperfoil.tools.horreum.svc;

import static io.hyperfoil.tools.horreum.test.TestUtil.eventually;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import io.hyperfoil.tools.horreum.api.data.Action;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import jakarta.ws.rs.core.HttpHeaders;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.TestInfo;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.hyperfoil.tools.horreum.entity.ActionLogDAO;
import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class ActionServiceTest extends BaseServiceTest {

   @ConfigProperty(name = "quarkus.http.port")
   String port;

   @org.junit.jupiter.api.Test
   public void testFailingHttp(TestInfo testInfo) {
      Test test = createTest(createExampleTest(getTestName(testInfo)));

      addAllowedSite("http://localhost:".concat(port));
      addTestHttpAction(test, AsyncEventChannels.RUN_NEW, "http://localhost:".concat(port));

      uploadRun(JsonNodeFactory.instance.objectNode(), test.name);

      eventually((Runnable) () -> Util.withTx(tm, () -> {
         em.clear();
         try (CloseMe ignored = roleManager.withRoles(Arrays.asList(TESTER_ROLES))) {
            return ActionLogDAO.find("testId", test.id).count() == 1;
         }
      }));
   }

   @org.junit.jupiter.api.Test
   public void testAddGlobalAction() {
      String responseType = addGlobalAction(AsyncEventChannels.TEST_NEW, "https://attacker.com")
            .then().statusCode(400).extract().header(HttpHeaders.CONTENT_TYPE);
      // constraint violations are mapped to 400 + JSON response, we want explicit error
      assertTrue(responseType.startsWith("text/plain")); // text/plain;charset=UTF-8

      addAllowedSite("https://example.com");

      Action action = addGlobalAction(AsyncEventChannels.TEST_NEW, "https://example.com/foo/bar").then().statusCode(200).extract().body().as(Action.class);
      assertNotNull(action.id);
      assertTrue(action.active);
      given().auth().oauth2(getAdminToken()).delete("/api/action/" + action.id);
   }
}
