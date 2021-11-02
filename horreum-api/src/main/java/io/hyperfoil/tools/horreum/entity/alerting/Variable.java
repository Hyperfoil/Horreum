package io.hyperfoil.tools.horreum.entity.alerting;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Variable emits a single value from the {@link io.hyperfoil.tools.horreum.entity.json.Run#data}
 * using the names of {@link io.hyperfoil.tools.horreum.entity.json.SchemaExtractor} for accessors and
 * JavaScript code in {@link #calculation} (calculation is not necessary if there's a single accessor).
 *
 * The criteria part tests a {@link DataPoint datapoints} from a single test over time. A mean value from
 * all datapoints since last change is calculated and if the new datapoint
 * is not within bounds of <code>(1 - maxDifferenceLastDatapoint) * mean</code> and
 * <code>(1 + maxDifferenceLastDatapoint) * mean</code> a new change is emitted. This test is skipped if we don't
 * have at least {@link #minWindow} previous datapoints to calculate the mean.
 * A similar comparison is calculated using last {@link #floatingWindow} datapoints (including the newest one);
 * we calculate the mean of older datapoints since last change and compare if the mean from this floating window
 * is within the bounds of <code>(1 - maxDifferenceFloatingWindow) * oldMean</code> and
 * <code>(1 + maxDifferenceFloatingWindow) * oldMean</code>.
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

   public double maxDifferenceLastDatapoint = 0.2;

   public int minWindow = 5;

   public double maxDifferenceFloatingWindow = 0.1;

   public int floatingWindow = 7;
}
