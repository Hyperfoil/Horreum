package io.hyperfoil.tools.horreum;

import io.hyperfoil.tools.yaup.json.Json;

import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.json.bind.adapter.JsonbAdapter;

public class JsonAdapter implements JsonbAdapter<Json, JsonStructure> {

   @Override
   public JsonStructure adaptToJson(Json json) {
      JsonStructure rtrn;
      if (json == null) {
         return null;
      }
      if (json.isArray()) {
         JsonArrayBuilder builder =  javax.json.Json.createArrayBuilder();
         json.forEach((value) -> {
            try {
               if (value == null) {
                  builder.addNull();
               } else if (value instanceof Json) {
                  builder.add(adaptToJson((Json) value));
               } else if (value instanceof Integer) {
                  builder.add(((Number) value).intValue());
               } else if (value instanceof Long) {
                  builder.add(((Number) value).longValue());
               } else if (value instanceof Number) {
                  builder.add(((Number) value).doubleValue());
               } else {
                  builder.add(value.toString());
               }
            } catch (Exception e) {
               e.printStackTrace();
            }
         });
         rtrn = builder.build();
      } else {
         JsonObjectBuilder builder = javax.json.Json.createObjectBuilder();
         json.forEach((key, value) -> {
            String keyString = key.toString();
            try {
               if (value == null) {
                  builder.addNull(keyString);
               } else if (value instanceof Json) {
                  builder.add(keyString, adaptToJson((Json) value));
               } else if (value instanceof Integer) {
                  builder.add(keyString, ((Number) value).intValue());
               } else if (value instanceof Long) {
                  builder.add(keyString, ((Number) value).longValue());
               } else if (value instanceof Number) {
                  builder.add(keyString, ((Number) value).doubleValue());
               } else {
                  builder.add(keyString, value.toString());
               }
            } catch (Exception e) {
               e.printStackTrace();
            }
         });
         rtrn = builder.build();
      }
      return rtrn;
   }

   private Object jsonValueToObject(JsonValue value){
      JsonValue.ValueType type = value.getValueType();
      Object rtrn = value.toString();
      switch(type){
         case ARRAY:
            try {
               rtrn = adaptFromJson((JsonArray)value);
            } catch (Exception e) {
               e.printStackTrace();
            }
            break;
         case OBJECT:
            try {
               rtrn = adaptFromJson((JsonObject)value);
            } catch (Exception e) {
               e.printStackTrace();
            }
            break;
         case NUMBER:
            rtrn = ((JsonNumber)value).isIntegral() ? ((JsonNumber)value).longValue() : ((JsonNumber)value).doubleValue();
            break;
         case TRUE:
            rtrn = true;
            break;
         case FALSE:
            rtrn = false;
            break;
         case STRING:
            rtrn = ((JsonString)value).getString();
            break;
         case NULL:
            rtrn = null;
            break;
         default:
      }
      return rtrn;
   }

   @Override
   public Json adaptFromJson(JsonStructure jsonStructure) throws Exception {
      if(jsonStructure instanceof JsonArray){
         Json rtrn = new Json(true);
         JsonArray jsonArray = (JsonArray)jsonStructure;
         jsonArray.listIterator().forEachRemaining(jsonValue -> {
            rtrn.add(jsonValueToObject(jsonValue));
         });
         return rtrn;
      }else if (jsonStructure instanceof JsonObject){
         Json rtrn = new Json(false);
         JsonObject jsonObject = (JsonObject)jsonStructure;
         jsonObject.keySet().forEach(key->{
            rtrn.set(key,jsonValueToObject(jsonObject.get(key)));
         });
         return rtrn;
      }else{
         System.out.println("Unsupported JsonStructure type "+jsonStructure);
      }
      return null;
   }
}
