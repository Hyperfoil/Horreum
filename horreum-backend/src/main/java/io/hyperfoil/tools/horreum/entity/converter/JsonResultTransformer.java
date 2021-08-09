package io.hyperfoil.tools.horreum.entity.converter;

import java.util.List;

import org.hibernate.transform.ResultTransformer;

import io.hyperfoil.tools.yaup.json.Json;

public class JsonResultTransformer implements ResultTransformer {
   public static final JsonResultTransformer INSTANCE = new JsonResultTransformer();

   private JsonResultTransformer() {}

   @Override
   public Object transformTuple(Object[] tuple, String[] aliases) {
      Json json = new Json(false);
      for (int i = 0; i < aliases.length; ++i) {
         Object value = tuple[i];
         if (value == null || value instanceof Number || value instanceof Boolean) {
            json.add(aliases[i], value);
         } else {
            json.add(aliases[i], value.toString());
         }
      }
      return json;
   }

   @SuppressWarnings("rawtypes")
   @Override
   public List transformList(List collection) {
      return collection;
   }
}
