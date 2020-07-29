package io.hyperfoil.tools.horreum.entity.alerting;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.validation.constraints.NotNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * This should emit a single value from the {@link io.hyperfoil.tools.horreum.entity.json.Run#data}
 * using the names of {@link io.hyperfoil.tools.horreum.entity.json.SchemaExtractor} for accessors and
 * JavaScript code in {@link #calculation} (calculation is not necessary if there's a single accessor).
 */
@Entity(name = "variable")
public class Variable extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Integer id;

   @NotNull
   public int testId;

   @NotNull
   public String name;

   @NotNull
   public String accessors;

   public String calculation;
}
