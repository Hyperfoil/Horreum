package io.hyperfoil.tools.horreum.entity.alerting;

import java.time.Instant;

import javax.json.bind.annotation.JsonbTypeDeserializer;
import javax.json.bind.annotation.JsonbTypeSerializer;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import io.hyperfoil.tools.horreum.entity.converter.InstantSerializer;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * For each {@link Variable} a datapoint will be created for each run.
 */
@Entity(name = "datapoint")
public class DataPoint extends PanacheEntityBase {
   public static final String EVENT_NEW = "datapoint/new";
   @Id
   @GeneratedValue(strategy = GenerationType.AUTO)
   @Column(columnDefinition = "SERIAL")
   public Integer id;

   @NotNull
   public int runId;

   @NotNull
   @Column(columnDefinition = "timestamp")
   @JsonbTypeDeserializer(InstantSerializer.class)
   @JsonbTypeSerializer(InstantSerializer.class)
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
