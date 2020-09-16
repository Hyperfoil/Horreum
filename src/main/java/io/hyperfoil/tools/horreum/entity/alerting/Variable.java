package io.hyperfoil.tools.horreum.entity.alerting;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.validation.constraints.NotNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Variable emits a single value from the {@link io.hyperfoil.tools.horreum.entity.json.Run#data}
 * using the names of {@link io.hyperfoil.tools.horreum.entity.json.SchemaExtractor} for accessors and
 * JavaScript code in {@link #calculation} (calculation is not necessary if there's a single accessor).
 *
 * The criteria part tests a {@link DataPoint datapoints} from a single test over time. A mean and standard deviance
 * is calculated from all results after last {@link Change} (but at most {@link #maxWindow results},
 * and if the newest result differs from the mean by more than <code>stddev * deviationFactor</code>
 * a new change is emitted.
 * This criterion also tests K latest results compared to N preceding results
 * (up to previous change or maxWindow) using Student's t-test with given {@link #confidence}.
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

   public String calculation;

   public int minWindow;

   public int maxWindow;

   public double deviationFactor = 2.0;

   /**
    * This is <code>1 - p-value</code> used in the Student's t-test.
    */
   public double confidence = 0.95;
}
