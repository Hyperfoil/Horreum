package io.hyperfoil.tools.repo.entity.converter;

import io.hyperfoil.tools.yaup.json.Json;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class JsonConverter implements AttributeConverter<Json,String> {
   @Override
   public String convertToDatabaseColumn(Json json) {
      return json.toString();
   }

   @Override
   public Json convertToEntityAttribute(String s) {
      return Json.fromString(s);
   }
}
