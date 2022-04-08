package io.hyperfoil.tools.horreum.entity.alerting;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.tools.horreum.api.AlertingService;
import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * This marks certain run as following a change (regression) in tested criterion.
 * Change is not necessarily a negative one - e.g. improvement in throughput is still
 * a change to faciliate correct testing of subsequent runs.
 * Eventually the change should be manually confirmed (approved) with an explanatory description.
 */
@Entity
public class Change extends PanacheEntityBase {
   public static final String EVENT_NEW = "change/new";

   @Id
   @GeneratedValue
   public int id;

   @NotNull
   @ManyToOne
   public Variable variable;

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "dataset_id")
   @JsonIgnore
   public DataSet dataset;

   @NotNull
   @Column(columnDefinition = "timestamp")
   public Instant timestamp;

   @NotNull
   public boolean confirmed;

   public String description;

   @JsonProperty("runId")
   public int getRunId() {
      return dataset.id;
   }

   @Override
   public String toString() {
      return "Change{" +
            "id=" + id +
            ", variable=" + variable +
            ", runId=" + dataset.id +
            ", timestamp=" + timestamp +
            ", confirmed=" + confirmed +
            ", description='" + description + '\'' +
            '}';
   }

   public static class Event {
      public Change change;
      public AlertingService.DatasetInfo dataset;
      public boolean notify;

      public Event(Change change, AlertingService.DatasetInfo dataset, boolean notify) {
         this.change = change;
         this.dataset = dataset;
         this.notify = notify;
      }

      @Override
      public String toString() {
         return "Change.Event{" +
               "change=" + change +
               ", dataset=" + dataset +
               ", notify=" + notify +
               '}';
      }
   }
}
