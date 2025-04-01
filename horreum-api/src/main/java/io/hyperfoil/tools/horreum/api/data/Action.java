package io.hyperfoil.tools.horreum.api.data;

import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.data.ActionConfig.GithubIssueCommentActionConfig;
import io.hyperfoil.tools.horreum.api.data.ActionConfig.GithubIssueCreateActionConfig;
import io.hyperfoil.tools.horreum.api.data.ActionConfig.HttpActionConfig;
import io.hyperfoil.tools.horreum.api.data.ActionConfig.SlackChannelMessageActionConfig;

public class Action {

    public static class Secret {
        public String token;
        public boolean modified;
    }

    @JsonProperty(required = true)
    public Integer id;
    @NotNull
    @JsonProperty(required = true)
    public String event;
    @NotNull
    @JsonProperty(required = true)
    public String type;

    @NotNull
    @JsonProperty(required = true)
    @Schema(type = SchemaType.OBJECT, discriminatorProperty = "type", discriminatorMapping = {
            @DiscriminatorMapping(schema = GithubIssueCommentActionConfig.class, value = "github-issue-comment"),
            @DiscriminatorMapping(schema = GithubIssueCreateActionConfig.class, value = "github-issue-create"),
            @DiscriminatorMapping(schema = HttpActionConfig.class, value = "http"),
            @DiscriminatorMapping(schema = SlackChannelMessageActionConfig.class, value = "slack-channel-message"),
    }, oneOf = {
            GithubIssueCommentActionConfig.class,
            GithubIssueCreateActionConfig.class,
            HttpActionConfig.class,
            SlackChannelMessageActionConfig.class,
    })
    public ObjectNode config;

    @NotNull
    @JsonIgnore
    @Schema(type = SchemaType.OBJECT, implementation = Action.Secret.class)
    public ObjectNode secrets;
    @NotNull
    @JsonProperty(required = true)
    public Integer testId;
    @NotNull
    @JsonProperty(required = true)
    public boolean active = true;
    @NotNull
    @JsonProperty(required = true)
    public boolean runAlways;

    public Action() {
    }

    public Action(Integer id, String event, String type, ObjectNode config, ObjectNode secrets,
            Integer testId, boolean active, boolean runAlways) {
        this.id = id;
        this.event = event;
        this.type = type;
        this.config = config;
        this.secrets = secrets;
        this.testId = testId;
        this.active = active;
        this.runAlways = runAlways;
    }

    @JsonProperty("secrets")
    @Schema(type = SchemaType.OBJECT, implementation = Secret.class)
    public void setSecrets(ObjectNode secrets) {
        this.secrets = secrets;
    }

    @JsonProperty("secrets")
    @Schema(type = SchemaType.OBJECT, implementation = Secret.class)
    public ObjectNode getSecrets() {
        if (this.secrets != null && this.secrets.isObject()) {
            ObjectNode masked = JsonNodeFactory.instance.objectNode();
            this.secrets.fieldNames().forEachRemaining((name) -> {
                masked.put(name, "********");
            });
            return masked;
        } else {
            return JsonNodeFactory.instance.objectNode();
        }
    }
}
