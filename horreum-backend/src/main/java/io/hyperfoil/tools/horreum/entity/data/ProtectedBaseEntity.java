package io.hyperfoil.tools.horreum.entity.data;

import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class ProtectedBaseEntity extends OwnedEntityBase {

    public String token;
}
