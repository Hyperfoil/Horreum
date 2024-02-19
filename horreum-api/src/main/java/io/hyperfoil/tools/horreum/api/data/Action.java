package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;


public class Action {

    public static class HttpAction {
        public String url;
    }
    public static class GithubIssueCommentAction {
        public String issueUrl;
        public String owner;
        public String repo;
        public String issue;
        public String formatter;
    }
    public static class GithubIssueCreateAction {
        public String owner;
        public String repo;
        public String title;
        public String formatter;
    }
    public static class Secret {
        public String token;
        public boolean modified;
    }

    @JsonProperty( required = true )
    public Integer id;
    @NotNull
    @JsonProperty( required = true )
    public String event;
    @NotNull
    @JsonProperty( required = true )
    public String type;

    @NotNull
    @JsonProperty( required = true )
    @Schema(type = SchemaType.OBJECT,
            oneOf = {
                    Action.HttpAction.class,
                    Action.GithubIssueCommentAction.class,
                    Action.GithubIssueCreateAction.class
            }
    )
    public ObjectNode config;

    @NotNull
    @JsonIgnore
    @Schema(type = SchemaType.OBJECT,
            allOf = {
                    Action.Secret.class
            }
    )
    public ObjectNode secrets;
    @NotNull
    @JsonProperty( required = true )
    public Integer testId;
    @NotNull
    @JsonProperty( required = true )
    public boolean active = true;
    @NotNull
    @JsonProperty( required = true )
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
    public void setSecrets(ObjectNode secrets) {
        this.secrets = secrets;
    }

    @JsonProperty("secrets")
    public JsonNode getMaskedSecrets() {
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
