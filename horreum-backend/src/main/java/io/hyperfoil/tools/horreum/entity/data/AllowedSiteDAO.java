package io.hyperfoil.tools.horreum.entity.data;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity(name = "allowedsite")
public class AllowedSiteDAO extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Long id;

   @NotNull
   public String prefix;
}
