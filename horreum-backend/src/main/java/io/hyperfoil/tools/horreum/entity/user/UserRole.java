package io.hyperfoil.tools.horreum.entity.user;

import io.quarkus.security.jpa.RolesValue;

public enum UserRole {

   ADMIN, HORREUM_SYSTEM, MACHINE;

   @Override @RolesValue public String toString() {
      return super.toString();
   }
}
