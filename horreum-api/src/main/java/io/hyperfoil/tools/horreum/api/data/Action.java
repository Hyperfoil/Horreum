package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Action {
    @JsonProperty( required = true )
    public Integer id;
    @JsonProperty( required = true )
    public String event;
    @JsonProperty( required = true )
    public String type;
    @JsonProperty( required = true )
    public JsonNode config;
    @JsonIgnore
    public JsonNode secrets;
    @JsonProperty( required = true )
    public Integer testId;
    @JsonProperty( required = true )
    public boolean active = true;
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
