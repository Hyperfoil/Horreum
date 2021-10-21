package io.hyperfoil.tools.serializer;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.JsonNodeDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import io.hyperfoil.tools.yaup.json.Json;

public class JsonDeserializer extends StdDeserializer<Json> {
   public JsonDeserializer() {
      super(Json.class);
   }

   @Override
   public Json deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      JsonNode node = JsonNodeDeserializer.getDeserializer(null).deserialize(p, ctxt);
      return Json.fromJsonNode(node);
   }
}
