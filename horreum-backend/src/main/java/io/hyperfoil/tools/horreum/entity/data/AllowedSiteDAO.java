package io.hyperfoil.tools.horreum.entity.data;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.validation.constraints.NotNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity(name = "allowedsite")
public class AllowedSiteDAO extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Long id;

   @NotNull
   public String prefix;
}
