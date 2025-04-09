package io.hyperfoil.tools.horreum.svc;

import static io.hyperfoil.tools.horreum.test.TestUtil.eventually;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.action.SlackChannelMessageAction;
import io.hyperfoil.tools.horreum.api.data.Action;
import io.hyperfoil.tools.horreum.api.data.ActionLog;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.entity.ActionLogDAO;
import io.hyperfoil.tools.horreum.entity.PersistentLogDAO;
import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
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

    @Inject
    ActionServiceImpl actionService;

    @org.junit.jupiter.api.Test
    public void testFailingHttp(TestInfo testInfo) {
        Test test = createTest(createExampleTest(getTestName(testInfo)));

        addAllowedSite("http://localhost:" + port);
        addTestHttpAction(test, AsyncEventChannels.RUN_NEW, "http://localhost:" + port);

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

        Action action = addGlobalAction(AsyncEventChannels.TEST_NEW, "https://example.com/foo/bar").then().statusCode(200)
                .extract().body().as(Action.class);
        assertNotNull(action.id);
        assertTrue(action.active);
        given().auth().oauth2(getAdminToken()).delete("/api/action/" + action.id);
    }

    /**
     * @param testInfo
     */
    @org.junit.jupiter.api.Test
    public void testSlackGoodChannelAction(TestInfo testInfo) {
        executeTestCase(testInfo, "GOODCHANNEL", true);
    }

    /**
     * @param testInfo
     */
    @org.junit.jupiter.api.Test
    public void testSlackBadChannelAction(TestInfo testInfo) {
        executeTestCase(testInfo, "BADCHANNEL", false);
    }

    /**
     * @param testInfo
     */
    @org.junit.jupiter.api.Test
    public void testSlackBusyChannelAction(TestInfo testInfo) {
        executeTestCase(testInfo, "BUSYCHANNEL", true);
    }

    /**
     * @param testInfo
     */
    @org.junit.jupiter.api.Test
    public void testSlackErrorChannelAction(TestInfo testInfo) {
        executeTestCase(testInfo, "ERRORCHANNEL", false);
    }

    private void executeTestCase(TestInfo testInfo, String channel, Boolean expectError) {
        Test test = createTest(createExampleTest(getTestName(testInfo)));

        String token = "FAKETOKEN";

        /*
         * Build an Action JSON directly because Action DTO serialization will
         * mask the secrets.
         */
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

        // Iterate through the set of test cases.
        config.put("channel", channel);
        Action action = jsonRequest().auth().oauth2(getTesterToken())
                .contentType(ContentType.JSON).body(action_json)
                .post("/api/action")
                .then().statusCode(200)
                .contentType(ContentType.JSON).extract().body().as(Action.class);

        // Dispatch the action manually
        actionService.onNewTest(test);
        int errors = 0;

        // Look for Action log messages corresponding to this test case.
        List<ActionLog> logs = jsonRequest().auth().oauth2(getTesterToken())
                .queryParam("level", PersistentLogDAO.ERROR).get("/api/log/action/" + test.id)
                .then().statusCode(200)
                .contentType(ContentType.JSON).extract().body().jsonPath().getList(".", ActionLog.class);
        for (ActionLog l : logs) {
            if (l.type.equals(SlackChannelMessageAction.TYPE_SLACK_MESSAGE)
                    && l.message.contains(channel) && l.level == 3) {
                errors++;
            }

            /*
             * Assert that we received 0 or 1 action error logs, depending on the
             * expected status. This is imperfect, as the "BUSY" (413) case is
             * retried asynchronously and we won't necessarily catch an error, but
             * it's sufficient for the other test cases.
             */
            Assertions.assertEquals(expectError ? 0 : 1, errors, "Test case " + channel + " failed");

            // Remove the action. (The test framework will remove the test.)
            given().auth().oauth2(getAdminToken()).delete("/api/action/" + action.id);
        }
    }
}
