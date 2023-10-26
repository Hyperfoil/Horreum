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
@Schema(type = SchemaType.STRING, required = true,
        description = "Resources have different visibility within the UI. 'PUBLIC', 'PROTECTED' and 'PRIVATE'. Restricted resources are not visible to users who do not have the correct permissions")
public enum Access {
   /** Anyone can see */
   PUBLIC ("PUBLIC"),
   /** Anyone who is authenticated (logged in) can see */
   PROTECTED  ("PROTECTED"),
   /** Only the owner can see */
   PRIVATE  ("PRIVATE"),
   ;
   private static final Access[] VALUES = values();

   private final String name;

   private Access(String s) {
      name = s;
   }

   public boolean equalsName(String otherName) {
      // (otherName == null) check is not needed because name.equals(null) returns false
      return name.equals(otherName);
   }

   @JsonValue
   public String toString() {
      return this.name;
   }

//   @JsonValue
//   public int serialize() {
//      return ordinal();
//   }

   @JsonCreator
   public static Access fromString(String str) {
      try {
         return VALUES[Integer.parseInt(str)];
      } catch (NumberFormatException e) {
         return Access.valueOf(str);
      }
   }
   public static Access fromInt(int a) {
      return VALUES[a];
   }
}
