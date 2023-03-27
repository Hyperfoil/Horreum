package io.hyperfoil.tools.horreum.api.data;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Defines extra restrictions for read-only access.
 *
 * Do not change unless changing constants in SQL policies.
 */
@Schema(type = SchemaType.INTEGER, required = true)
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
   public static Access fromString(String str) {
      try {
         return VALUES[Integer.parseInt(str)];
      } catch (NumberFormatException e) {
         return Access.valueOf(str);
      }
   }
}
