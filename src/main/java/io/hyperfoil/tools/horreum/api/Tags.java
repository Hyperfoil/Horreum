package io.hyperfoil.tools.horreum.api;

import java.util.Map;
import java.util.TreeMap;

public class Tags {
   static void addTagQuery(Map<String, String> tags, StringBuilder sql, int counter) {
      if (tags != null) {
         for (String tag : tags.keySet()) {
            sql.append(" AND jsonb_path_query_first(run_tags.tags, '$.").append(tag).append("'::::jsonpath)#>>'{}' = ?").append(counter++);
         }
      }
   }

   static void addTagValues(Map<String, String> tags, javax.persistence.Query nativeQuery, int counter) {
      if (tags != null) {
         for (String value : tags.values()) {
            nativeQuery.setParameter(counter++, value);
         }
      }
   }

   static Map<String, String> parseTags(String tagString) {
      if (tagString == null || tagString.isEmpty()) {
         return null;
      }
      Map<String, String> tags = new TreeMap<>();
      for (String keyValue : tagString.split(";")) {
         int colon = keyValue.indexOf(":");
         if (colon < 0) continue;
         tags.put(keyValue.substring(0, colon), keyValue.substring(colon + 1));
      }
      if (tags.isEmpty()) {
         return null;
      }
      return tags;
   }
}
