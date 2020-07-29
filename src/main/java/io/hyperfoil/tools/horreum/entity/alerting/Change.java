package io.hyperfoil.tools.horreum.entity.alerting;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.OneToOne;
import javax.validation.constraints.NotNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * This marks certain run as following a change (regression) in tested criterion.
 * Change is not necessarily a negative one - e.g. improvement in throughput is still
 * a change to faciliate correct testing of subsequent runs.
 * Eventually the change should be manually confirmed (approved) with an explanatory description.
 */
@Entity(name = "change")
public class Change extends PanacheEntityBase {
   public static final String EVENT_NEW = "change/new";

   @Id
   @GeneratedValue
   public int id;

   @NotNull
   @ManyToOne
   public DataPoint dataPoint;

   @NotNull
   @ManyToOne
   public Criterion criterion;

   @NotNull
   public boolean confirmed;

   public String description;
}
