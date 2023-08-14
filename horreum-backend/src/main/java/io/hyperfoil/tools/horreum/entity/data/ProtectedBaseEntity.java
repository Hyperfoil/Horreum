package io.hyperfoil.tools.horreum.entity.data;

import javax.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class ProtectedBaseEntity extends OwnedEntityBase {

    public String token;
}
