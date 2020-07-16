package io.hyperfoil.tools.horreum.entity.converter;

import javax.json.JsonValue;
import javax.json.bind.serializer.DeserializationContext;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;
import java.lang.reflect.Type;
import java.time.Instant;

public class InstantSerializer implements JsonbDeserializer<Instant>, JsonbSerializer<Instant> {
   @Override
   public Instant deserialize(JsonParser jsonParser, DeserializationContext deserializationContext, Type type) {
      JsonValue value = jsonParser.getValue();
      if(value.getValueType().equals(JsonValue.ValueType.NUMBER)){
         return Instant.ofEpochMilli(jsonParser.getLong());
      }else if (value.getValueType().equals(JsonValue.ValueType.STRING)){
         return Instant.parse(jsonParser.getString());
      }
      return null;
   }

   @Override
   public void serialize(Instant instant, JsonGenerator jsonGenerator, SerializationContext serializationContext) {
      jsonGenerator.write(instant.toEpochMilli());
   }
}
