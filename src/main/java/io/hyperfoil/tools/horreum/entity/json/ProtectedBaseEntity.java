package io.hyperfoil.tools.horreum.entity.json;

import io.hyperfoil.tools.horreum.entity.converter.AccessSerializer;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;

import javax.json.bind.annotation.JsonbTypeDeserializer;
import javax.json.bind.annotation.JsonbTypeSerializer;
import javax.persistence.MappedSuperclass;
import javax.validation.constraints.NotNull;

@RegisterForReflection
@MappedSuperclass
public abstract class ProtectedBaseEntity extends PanacheEntityBase {

    @NotNull
    public String owner;

    public String token;

    @NotNull
    @JsonbTypeSerializer(AccessSerializer.class)
    @JsonbTypeDeserializer(AccessSerializer.class)
    public Access access = Access.PUBLIC;

}
