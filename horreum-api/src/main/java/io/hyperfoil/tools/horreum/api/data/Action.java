package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class Action {
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
    @Schema(implementation = JsonNode.class)
    public JsonNode config;
    @NotNull
    @JsonIgnore
    @Schema(implementation = JsonNode.class)
    public JsonNode secrets;
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

    public Action(Integer id, String event, String type, JsonNode config, JsonNode secrets,
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
    public void setSecrets(JsonNode secrets) {
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
