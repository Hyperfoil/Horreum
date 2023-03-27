package io.hyperfoil.tools.horreum.entity.converter;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.api.ApiUtil;

@Converter
public class JsonConverter implements AttributeConverter<JsonNode, String> {
   @Override
   public String convertToDatabaseColumn(JsonNode json) {
      return json.toString();
   }

   @Override
   public JsonNode convertToEntityAttribute(String s) {
      try {
         return ApiUtil.OBJECT_MAPPER.readTree(s);
      } catch (JsonProcessingException e) {
         throw new RuntimeException(e);
      }
   }
}
