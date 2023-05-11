package io.hyperfoil.tools.horreum.svc;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Query;

import io.quarkus.security.identity.SecurityIdentity;

public final class Roles {
   public static final String VIEWER = "viewer";
   public static final String TESTER = "tester";
   public static final String UPLOADER = "uploader";
   public static final String MANAGER = "manager";
   public static final String ADMIN = "admin";
   public static final String HORREUM_SYSTEM = "horreum.system";
   public static final String HORREUM_MESSAGEBUS = "horreum.messagebus";

   private static final String MY_ROLES = "__my";
   private static final String ALL_ROLES = "__all";

   private Roles() {}

   static boolean hasRolesParam(String roles) {
      return roles != null && !roles.isEmpty() && !roles.equals(ALL_ROLES);
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

   static Set<String> expandRoles(String roles, SecurityIdentity identity) {
      if (roles == null || roles.isEmpty() || roles.equals(ALL_ROLES)){
         return null;
      } else if (roles.equals(MY_ROLES)) {
         if (!identity.isAnonymous()) {
            return identity.getRoles();
         }
      } else if (roles.indexOf(';') >= 0){
         return new HashSet<>(Arrays.asList(roles.split(";")));
      } else {
         return Collections.singleton(roles);
      }
      return null;
   }

   static boolean hasRoleWithSuffix(SecurityIdentity identity, String owner, String suffix) {
      return identity.hasRole(owner.substring(0, owner.length() - 5) + suffix);
   }
}
