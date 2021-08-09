package io.hyperfoil.tools.horreum.svc;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public class Tags {
   static int addTagQuery(Map<String, String> tags, StringBuilder sql, int counter) {
      if (tags != null) {
         for (String tag : tags.keySet()) {
            if (tag == null) {
               // special case for no-tags query
               sql.append(" AND run_tags.tags IS NULL");
            } else {
               sql.append(" AND jsonb_path_query_first(run_tags.tags, '$.").append(tag).append("'::::jsonpath)#>>'{}' = ?").append(counter++);
            }
         }
      }
      return counter;
   }

   static int addTagValues(Map<String, String> tags, javax.persistence.Query nativeQuery, int counter) {
      if (tags != null) {
         for (var entry : tags.entrySet()) {
            if (entry.getKey() != null) {
               nativeQuery.setParameter(counter++, entry.getValue());
            }
         }
      }
      return counter;
   }

   static Map<String, String> parseTags(String tagString) {
      if (tagString == null || tagString.isEmpty()) {
         // all tags
         return null;
      } else if ("<no tags>".equals(tagString)) {
         return Collections.singletonMap(null, null);
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
