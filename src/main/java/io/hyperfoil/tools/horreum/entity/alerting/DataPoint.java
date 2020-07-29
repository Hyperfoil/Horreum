package io.hyperfoil.tools.horreum.entity.alerting;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.validation.constraints.NotNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * For each {@link Variable} a datapoint will be created for each run.
 */
@Entity(name = "datapoint")
public class DataPoint extends PanacheEntityBase {
   public static final String EVENT_NEW = "datapoint/new";
   @Id
   @Column(columnDefinition = "SERIAL")
   public int id;

   @NotNull
   public int runId;

   @NotNull
   public double value;

   @NotNull
   @ManyToOne(fetch = FetchType.LAZY)
   public Variable variable;
}
