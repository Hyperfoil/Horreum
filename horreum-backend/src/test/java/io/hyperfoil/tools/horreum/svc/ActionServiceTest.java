package io.hyperfoil.tools.horreum.svc;

import static io.hyperfoil.tools.horreum.test.TestUtil.eventually;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.hyperfoil.tools.horreum.action.SlackChannelMessageAction;
import io.hyperfoil.tools.horreum.api.data.Action;
import io.hyperfoil.tools.horreum.api.data.ActionLog;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.entity.ActionLogDAO;
import io.hyperfoil.tools.horreum.entity.PersistentLogDAO;
import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.http.ContentType;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class ActionServiceTest extends BaseServiceTest {

   @ConfigProperty(name = "quarkus.http.port")
   String port;
   Logger log = Logger.getLogger(ActionServiceTest.class);

   @Inject
   private ActionServiceImpl actionService;

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
      // constraint violations are mapped to 400 + JSON response, we want explicit
      // error
      assertTrue(responseType.startsWith("text/plain")); // text/plain;charset=UTF-8

      addAllowedSite("https://example.com");

      Action action = addGlobalAction(AsyncEventChannels.TEST_NEW, "https://example.com/foo/bar").then().statusCode(200).extract().body().as(Action.class);
      assertNotNull(action.id);
      assertTrue(action.active);
      given().auth().oauth2(getAdminToken()).delete("/api/action/" + action.id);
   }

   /**
    * @param testInfo
    */
   @org.junit.jupiter.api.Test
   public void testSlackAction(TestInfo testInfo) {
      Test test = createTest(createExampleTest(getTestName(testInfo)));

      Map<String, Integer> channels = Map.of(
            "BADCHANNEL", 0,
            "GOODCHANNEL", 1,
            "BUSYCHANNEL", 429,
            "ERRORCHANNEL", 403);
      String token = "BADTOKEN";

      // Build an Action JSON directly because Action DTO serialization will
      // mask the secrets.
      ObjectNode config = Util.OBJECT_MAPPER.createObjectNode().put("formatter", "testToSlack");
      ObjectNode secrets = Util.OBJECT_MAPPER.createObjectNode().put("token", token);
      ObjectNode action_json = Util.OBJECT_MAPPER.createObjectNode()
            .put("id", -1)
            .put("event", AsyncEventChannels.TEST_NEW.name())
            .put("type", SlackChannelMessageAction.TYPE_SLACK_MESSAGE)
            .put("testId", test.id)
            .put("active", true)
            .put("runAlways", true);
      action_json.set("config", config);
      action_json.set("secrets", secrets);
      for (Map.Entry<String, Integer> test_case : channels.entrySet()) {
         config.put("channel", test_case.getKey());
         Action action = jsonRequest().auth().oauth2(getAdminToken())
               .contentType(ContentType.JSON).body(action_json)
               .post("/api/action")
               .then().statusCode(200)
               .contentType(ContentType.JSON).extract().body().as(Action.class);

         // Send the new test to action service
         actionService.onNewTest(test);

         // If we asking for a retry, wait for it to happen
         if (test_case.getValue() == 413) {
            log.infof("Expecting retry: wait for it ...");
            try {
               Thread.sleep(3);
            } catch (InterruptedException e) {
               log.infof("Beauty sleep interrupted");
            }
         }

         log.infof("Checking action %d results for %s", action.id, test_case.getKey());
         int errors = 0;

         List<ActionLog> logs = jsonRequest().auth().oauth2(getTesterToken())
               .queryParam("level", PersistentLogDAO.ERROR).get("/api/log/action/-1")
               .then().statusCode(200)
               .contentType(ContentType.JSON).extract().body().jsonPath().getList(".", ActionLog.class);
         log.infof("Retrieved %d log entries", logs.size());
         for (ActionLog l : logs) {
            if (l.type == SlackChannelMessageAction.TYPE_SLACK_MESSAGE) {
               log.infof("Found log for %d: %d, %s, %s: %s", l.testId, l.level, l.event, l.type, l.message);
               errors++;
            }
         }

         // If only ...
         // Assert.assertTrue("No slack action errors found", errors > 0);
         log.infof("%s: found %d errors for %s on test %d", test_case.getKey(), errors,
               SlackChannelMessageAction.TYPE_SLACK_MESSAGE, -1);

         // Remove the action. (The test framework will remove the test.)
         given().auth().oauth2(getAdminToken()).delete("/api/action/" + action.id);
      }
   }
}
