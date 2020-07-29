package io.hyperfoil.tools.horreum.entity.alerting;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Tests a {@link #variable} outputs from a single test over time. A mean and standard deviance
 * is calculated from all results after last {@link Change} (but at most {@link #maxWindow results},
 * and if the newest result differs from the mean by more than <code>stddev * deviationFactor</code>
 * a new change is emitted.
 * This criterion also tests K latest results compared to N preceding results
 * (up to previous change or maxWindow) using Student's t-test with given {@link #confidence}.
 */
@Entity(name = "criterion")
public class Criterion extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Integer id;

   @ManyToOne
   public Variable variable;

   public int maxWindow;

   public double deviationFactor = 1.0;

   /**
    * This is <code>1 - p-value</code> used in the Student's t-test.
    */
   public double confidence = 0.95;
}
