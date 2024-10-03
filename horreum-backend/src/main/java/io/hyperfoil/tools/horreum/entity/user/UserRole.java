package io.hyperfoil.tools.horreum.entity.user;

import io.quarkus.security.jpa.RolesValue;

public enum UserRole {

    ADMIN,
    HORREUM_SYSTEM;

    @Override
    @RolesValue
    public String toString() {
        return super.toString();
    }
}
