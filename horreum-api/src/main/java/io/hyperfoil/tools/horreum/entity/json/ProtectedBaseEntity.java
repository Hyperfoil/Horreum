package io.hyperfoil.tools.horreum.entity.json;

import javax.persistence.MappedSuperclass;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@MappedSuperclass
public abstract class ProtectedBaseEntity extends OwnedEntityBase {

    public String token;
}
