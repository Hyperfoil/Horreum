package io.hyperfoil.tools.horreum.entity.alerting;

import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Variable emits a single value from the {@link io.hyperfoil.tools.horreum.entity.json.Run#data}
 * using the names of {@link io.hyperfoil.tools.horreum.entity.json.SchemaExtractor} for accessors and
 * JavaScript code in {@link #calculation} (calculation is not necessary if there's a single accessor).
 *
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

   @Column(name = "\"group\"")
   public String group;

   @Column(name = "\"order\"")
   @NotNull
   public int order;

   @NotNull
   public String accessors;

   @JsonInclude(Include.NON_NULL)
   public String calculation;

   @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "variable")
   public Set<RegressionDetection> regressionDetection;
}
