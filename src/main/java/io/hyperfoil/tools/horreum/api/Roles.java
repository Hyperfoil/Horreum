package io.hyperfoil.tools.horreum.api;

import javax.persistence.Query;

import io.quarkus.security.identity.SecurityIdentity;

public final class Roles {
   public static final String TESTER = "tester";
   public static final String ADMIN = "admin";
   public static final String UPLOADER = "uploader";

   static final String MY_ROLES = "__my";

   private Roles() {}

   static boolean hasRolesParam(String roles) {
      return roles != null && !roles.isEmpty() && !roles.equals("__all");
   }

   static boolean addRolesSql(SecurityIdentity identity, String table, StringBuilder sql, String roles, int position, String prepend) {
      if (hasRolesParam(roles) && !(identity.isAnonymous() && roles.equals(MY_ROLES))) {
         if (prepend != null) {
            sql.append(prepend);
         }
         sql.append(' ');
         sql.append(table);
         sql.append(".owner = ANY(string_to_array(?").append(position).append(", ';')) ");
         return true;
      }
      return false;
   }

   static boolean addRolesParam(SecurityIdentity identity, Query query, int position, String roles) {
      if (hasRolesParam(roles)) {
         String actualRoles = null;
         if (roles.equals(MY_ROLES)) {
            if (!identity.isAnonymous()) {
               actualRoles = String.join(";", identity.getRoles());
            }
         } else {
            actualRoles = roles;
         }
         if (actualRoles != null) {
            query.setParameter(position, actualRoles);
            return true;
         }
      }
      return false;
   }
}
