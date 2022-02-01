package io.hyperfoil.tools.horreum.entity.alerting;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.tools.horreum.entity.json.Run;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * For each {@link Variable} a datapoint will be created for each run.
 */
@Entity
public class DataPoint extends PanacheEntityBase {
   public static final String EVENT_NEW = "datapoint/new";
   @Id
   @GeneratedValue(strategy = GenerationType.AUTO)
   @Column(columnDefinition = "SERIAL")
   public Integer id;

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "runid")
   @JsonIgnore
   public Run run;

   @NotNull
   @Column(columnDefinition = "timestamp")
   public Instant timestamp;

   @NotNull
   public double value;

   // for easier use in lambdas
   public double value() {
      return value;
   }

   @NotNull
   @ManyToOne(fetch = FetchType.LAZY)
   public Variable variable;

   @JsonProperty("runId")
   public int getRunId() {
      return run.id;
   }

   public static class Event {
      public DataPoint dataPoint;
      public boolean notify;

      public Event(DataPoint dataPoint, boolean notify) {
         this.dataPoint = dataPoint;
         this.notify = notify;
      }

      @Override
      public String toString() {
         return "DataPoint.Event{" +
               "dataPoint=" + dataPoint +
               ", notify=" + notify +
               '}';
      }
   }
}
