package io.hyperfoil.tools.horreum.action;

import com.fasterxml.jackson.databind.JsonNode;

public interface BodyFormatter {
   String name();
   String format(JsonNode config, Object payload);
}
