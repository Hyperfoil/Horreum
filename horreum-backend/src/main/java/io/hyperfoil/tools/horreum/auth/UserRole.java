package io.hyperfoil.tools.horreum.auth;

import io.quarkus.security.jpa.RolesValue;

public enum UserRole {

   ADMIN, HORREUM_SYSTEM;

   @Override @RolesValue public String toString() {
      return super.toString();
   }
}
