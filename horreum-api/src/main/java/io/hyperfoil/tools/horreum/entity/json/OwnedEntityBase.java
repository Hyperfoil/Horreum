package io.hyperfoil.tools.horreum.entity.json;

import javax.persistence.MappedSuperclass;
import javax.validation.constraints.NotNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@MappedSuperclass
public class OwnedEntityBase extends PanacheEntityBase {

   @NotNull
   public String owner;

   @NotNull
   public Access access = Access.PUBLIC;

}
