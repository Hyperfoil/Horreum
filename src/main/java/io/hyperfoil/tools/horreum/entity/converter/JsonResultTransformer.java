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
         json.add(aliases[i], tuple[i]);
      }
      return json;
   }

   @SuppressWarnings("rawtypes")
   @Override
   public List transformList(List collection) {
      return collection;
   }
}
