package io.hyperfoil.tools.repo.entity.converter;

import io.hyperfoil.tools.yaup.json.Json;

import javax.json.bind.serializer.DeserializationContext;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;
import java.lang.reflect.Type;
import java.math.BigDecimal;

public class JsonSerializer implements JsonbSerializer<Json>, JsonbDeserializer<Json> {


   public JsonSerializer(){}

   @Override
   public Json deserialize(JsonParser jsonParser, DeserializationContext deserializationContext, Type type) {
   try {
      Json rtrn = new Json();
      String key = null;
//      int level = 1;
      while ( /*level >= 1 && */jsonParser.hasNext()) {
         JsonParser.Event event = jsonParser.next();
         switch (event) {
            case KEY_NAME:
               key = jsonParser.getString();
               break;
            case START_OBJECT:
            case START_ARRAY:
               Json entry = deserialize(jsonParser,deserializationContext,type);
               if(key!=null){
                  rtrn.set(key,entry);
               }else{
                  rtrn.add(entry);
               }
               key=null;
               break;
            case END_OBJECT:
            case END_ARRAY:
               return rtrn;
               //break;
            case VALUE_STRING: {
               String value = jsonParser.getString();
               if(key!=null){
                  rtrn.set(key,value);
               }else{
                  rtrn.add(value);
               }
               key=null;
            }
            break;
            case VALUE_NUMBER: {
               BigDecimal bd = jsonParser.getBigDecimal();
               if (bd.signum() == 0 || bd.scale() <= 0 || bd.stripTrailingZeros().scale() <= 0) {//integer
                  if(key!=null){
                     rtrn.set(key,bd.longValue());
                  }else{
                     rtrn.add(bd.longValue());
                  }
               }else{
                  if(key!=null){
                     rtrn.set(key,bd.doubleValue());
                  }else{
                     rtrn.add(bd.doubleValue());
                  }
                  key=null;
               }
            }
            break;
            case VALUE_NULL:
               break;
            case VALUE_TRUE:
               if(key!=null){
                  rtrn.set(key,true);
               }else{
                  rtrn.add(true);
               }
               key=null;
               break;
            case VALUE_FALSE:
               if(key!=null){
                  rtrn.set(key,false);
               }else{
                  rtrn.add(false);
               }
               key=null;
               break;
            default:
               //TODO how do we get here?
         }

      }
      return rtrn;
   }catch(Exception e){

      e.printStackTrace();
   }
      return new Json(false);
   }

   @Override
   public void serialize(Json json, JsonGenerator jsonGenerator, SerializationContext serializationContext) {
      if(json == null){
         return;
      }
      if(json.isArray()){
         jsonGenerator.writeStartArray();
         json.forEach(value->{
            if(value instanceof Json){
               serialize((Json)value,jsonGenerator,serializationContext);
            }else{
               if(value instanceof Long) {
                  jsonGenerator.write((Long)value);
               }else if (value instanceof Double){
                  jsonGenerator.write((Double)value);
               }else {
                  jsonGenerator.write(value.toString());
               }
            }
         });
         jsonGenerator.writeEnd();
      }else{
         jsonGenerator.writeStartObject();
         json.forEach((key,value)->{
            jsonGenerator.writeKey(key.toString());
            if(value instanceof Json){
               serialize((Json)value,jsonGenerator,serializationContext);
            }else{
               if(value instanceof Long) {
                  jsonGenerator.write((Long)value);
               }else if (value instanceof Double){
                  jsonGenerator.write((Double)value);
               }else {
                  jsonGenerator.write(value.toString());
               }
            }
         });
         jsonGenerator.writeEnd();
      }
   }
}
