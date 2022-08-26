package io.hyperfoil.tools.horreum.action;

import com.fasterxml.jackson.databind.JsonNode;

public interface ActionPlugin {
   String type();
   void validate(JsonNode config, JsonNode secrets);
   void execute(JsonNode config, JsonNode secrets, Object payload);
}
