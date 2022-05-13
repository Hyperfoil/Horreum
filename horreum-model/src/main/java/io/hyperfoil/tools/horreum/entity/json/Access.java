package io.hyperfoil.tools.horreum.entity.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Defines extra restrictions for read-only access.
 *
 * Do not change unless changing constants in SQL policies.
 */
public enum Access {
   /** Anyone can see */
   PUBLIC,
   /** Anyone who is authenticated (logged in) can see */
   PROTECTED,
   /** Only the owner can see */
   PRIVATE,
   ;
   private static final Access[] VALUES = values();

   @JsonValue
   public int serialize() {
      return ordinal();
   }

   @JsonCreator
   public Access create(String str) {
      try {
         return VALUES[Integer.parseInt(str)];
      } catch (NumberFormatException e) {
         return Access.valueOf(str);
      }
   }
}
