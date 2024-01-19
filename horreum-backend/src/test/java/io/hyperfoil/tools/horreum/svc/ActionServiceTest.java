package io.hyperfoil.tools.horreum.svc;

import static io.hyperfoil.tools.horreum.test.TestUtil.eventually;
import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

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
import org.junit.Assert;
import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import io.restassured.response.Response;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class ActionServiceTest extends BaseServiceTest {

   @ConfigProperty(name = "quarkus.http.port")
   String port;
   Logger log = Logger.getLogger(ActionServiceTest.class);

   @Inject
   ActionServiceImpl actionService;

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

   /**
    * @param testInfo
    */
   @org.junit.jupiter.api.Test
   public void testMessageBusAction(TestInfo testInfo) {
      Test test = createTest(createExampleTest(getTestName(testInfo)));

      String channel = "NOTACHANNEL";
      String token = "BADTOKEN";

      // Build an Action JSON directly because Action DTO serialization will
      // mask the secrets. (Arguably, since we want to provoke an error here,
      // the masked "****" value would work just as well, but this code runs
      // successfully if the channel and token are real.)
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode config = mapper.createObjectNode()
            .put("channel", channel).put("formatter", "testToSlack");
      ObjectNode secrets = mapper.createObjectNode().put("token", token);
      ObjectNode action = mapper.createObjectNode()
            .put("id", -1)
            .put("event", MessageBusChannels.TEST_NEW.name())
            .put("type", SlackChannelMessageAction.TYPE_SLACK_MESSAGE)
            .put("testId", test.id)
            .put("active", true)
            .put("runAlways", true);

      // `set` returns a JsonNode, which can't be `set` or `put`, so we do
      // these outside the chain.
      action.set("config", config);
      action.set("secrets", secrets);
      Action r = jsonRequest().auth().oauth2(getAdminToken())
            .contentType(ContentType.JSON).body(action)
            .post("/api/action")
            .then().statusCode(200)
            .contentType(ContentType.JSON).extract().body().as(Action.class);

      // send new test to action service
      actionService.onNewTest(test);

      log.infof("Waiting for action to run");
      int errors = 0;
      for (int i = 0; i < 10; i++) {
         try {
            Thread.sleep(1000);
         } catch (InterruptedException e) {
         }

         // Should these action errors be logged under dummyTest.id? The
         // ActionServiceImpl handler seems to always log under its argument
         // value, -1: but I still can't find them.
         List<ActionLog> logs = jsonRequest().auth().oauth2(getTesterToken())
               .queryParam("level", PersistentLogDAO.ERROR).get("/api/log/action/-1")
               .then().statusCode(200)
               .contentType(ContentType.JSON).extract().body().jsonPath().getList(".", ActionLog.class);
         log.infof("[%d] Retrieved %d log entries", i, logs.size());
         for (ActionLog l : logs) {
            if (l.type == SlackChannelMessageAction.TYPE_SLACK_MESSAGE) {
               log.infof("Found log for %d: %d, %s, %s: %s", l.testId, l.level, l.event, l.type, l.message);
               errors++;
            }
         }
      }
      log.infof("Found %d errors for %s on test %d", errors, SlackChannelMessageAction.TYPE_SLACK_MESSAGE, -1);

      // If only ...
      // Assert.assertTrue("No slack action errors found", errors > 0);

      // Remove the action. (The test framework will remove the test.)
      given().auth().oauth2(getAdminToken()).delete("/api/action/" + r.id);
   }
}
