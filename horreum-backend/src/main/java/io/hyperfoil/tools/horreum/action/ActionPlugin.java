package io.hyperfoil.tools.horreum.action;

import com.fasterxml.jackson.databind.JsonNode;

import io.smallrye.mutiny.Uni;

public interface ActionPlugin {
    String type();

    void validate(JsonNode config, JsonNode secrets);

    Uni<String> execute(JsonNode config, JsonNode secrets, Object payload);
}
