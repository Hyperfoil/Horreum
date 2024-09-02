package io.hyperfoil.tools.horreum.action;

import static io.hyperfoil.tools.horreum.action.ActionUtil.replaceExpressions;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.http.HttpStatus;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.svc.Util;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

@ApplicationScoped
public class SlackChannelMessageAction extends SlackPluginBase implements ActionPlugin {
    private static final Logger log = Logger.getLogger(SlackChannelMessageAction.class);
    public static final String TYPE_SLACK_MESSAGE = "slack-channel-message";

    @Override
    public String type() {
        return TYPE_SLACK_MESSAGE;
    }

    @Override
    public void validate(JsonNode config, JsonNode secrets) {
        log.tracef("Validating config %s, secrets %s", config, secrets);
        requireProperties(secrets, "token");
        requireProperties(config, "formatter", "channel");
    }

    @Override
    public Uni<String> execute(JsonNode config, JsonNode secrets, Object payload) {
        JsonNode json = Util.OBJECT_MAPPER.valueToTree(payload);

        // Token should NOT be in the dataset, so don't evaluate expressions
        String token = secrets.path("token").asText();
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Missing access token!");
        }

        // Channel and formatter selections can be expressions
        String formatter = replaceExpressions(config.path("formatter").asText(), json);
        String channel = replaceExpressions(config.path("channel").asText(), json);
        String url = ConfigProvider.getConfig().getValue("horreum.action.slack.url", String.class);

        // Convert the payload object into markdown text based on formatter
        String comment = getFormatter(formatter).format(config, payload);

        // Construct the Slack message: a single text block with markdown formatting
        ObjectNode body = Util.OBJECT_MAPPER.createObjectNode();
        body.put("channel", channel);
        ArrayNode blocks = Util.OBJECT_MAPPER.createArrayNode();
        body.set("blocks", blocks);
        ObjectNode section = Util.OBJECT_MAPPER.createObjectNode();
        section.put("type", "section");
        ObjectNode text = Util.OBJECT_MAPPER.createObjectNode();
        section.set("text", text);
        text.put("type", "mrkdwn");
        text.put("text", comment);
        blocks.add(section);

        log.debugf("Slack URL %s, token %s, body %s", url, token, body);
        return post(url, secrets, body)
                .onItem().transformToUni(response -> {
                    if (response.statusCode() < 400) {
                        JsonObject status = response.bodyAsJsonObject();
                        if (!status.getBoolean("ok")) {
                            return Uni.createFrom().failure(
                                    new RuntimeException(
                                            String.format("Failed to post to channel %s, response %s", channel,
                                                    status.getString("error"))));
                        }
                        return Uni.createFrom()
                                .item(String.format("Successfully(%d) posted to channel %s", response.statusCode(), channel));
                    } else if (response.statusCode() == HttpStatus.SC_TOO_MANY_REQUESTS
                            && response.getHeader("Retry-After") != null) {
                        log.debugf("Slack POST needs retry: %s (%s)", response.toString(), response.bodyAsString());
                        return retry(response, config, secrets, payload);

                    } else {
                        log.debugf("Slack POST failed: %s (%s)", response.statusCode(), response.bodyAsString());
                        return Uni.createFrom().failure(new RuntimeException(
                                String.format("Failed to post to channel %s, response %d: %s",
                                        channel, response.statusCode(), response.bodyAsString())));
                    }
                }).onFailure()
                .transform(t -> new RuntimeException("Failed to post message to " + channel + ": " + t.getMessage()));
    }
}
