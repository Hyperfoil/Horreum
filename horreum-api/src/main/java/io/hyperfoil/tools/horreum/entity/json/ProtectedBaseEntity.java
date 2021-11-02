package io.hyperfoil.tools.horreum.entity.json;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;

import javax.persistence.MappedSuperclass;
import javax.validation.constraints.NotNull;

@RegisterForReflection
@MappedSuperclass
public abstract class ProtectedBaseEntity extends PanacheEntityBase {

    @NotNull
    public String owner;

    public String token;

    @NotNull
    public Access access = Access.PUBLIC;
}
