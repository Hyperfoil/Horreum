package io.hyperfoil.tools.horreum.entity.alerting;

import java.time.Instant;

import javax.json.bind.annotation.JsonbTypeDeserializer;
import javax.json.bind.annotation.JsonbTypeSerializer;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import io.hyperfoil.tools.horreum.entity.converter.InstantSerializer;
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
   public Variable variable;

   @NotNull
   public int runId;

   @NotNull
   @Column(columnDefinition = "timestamp")
   @JsonbTypeDeserializer(InstantSerializer.class)
   @JsonbTypeSerializer(InstantSerializer.class)
   public Instant timestamp;

   @NotNull
   public boolean confirmed;

   public String description;

   public static class Event {
      public Change change;
      public boolean notify;

      public Event(Change change, boolean notify) {
         this.change = change;
         this.notify = notify;
      }

      @Override
      public String toString() {
         return "Change.Event{" +
               "change=" + change +
               ", notify=" + notify +
               '}';
      }
   }
}
