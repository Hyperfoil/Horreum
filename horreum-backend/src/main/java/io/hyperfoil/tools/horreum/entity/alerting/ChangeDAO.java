package io.hyperfoil.tools.horreum.entity.alerting;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.tools.horreum.entity.data.DataSetDAO;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * This marks certain run as following a change (regression) in tested criterion.
 * Change is not necessarily a negative one - e.g. improvement in throughput is still
 * a change to faciliate correct testing of subsequent runs.
 * Eventually the change should be manually confirmed (approved) with an explanatory description.
 */
@Entity(name = "Change")
@Table(name = "change")
public class ChangeDAO extends PanacheEntityBase {
   public static final String EVENT_NEW = "change/new";

   @JsonProperty(required = true)
   @Id
   @GeneratedValue
   public int id;

   @NotNull
   @ManyToOne
   public VariableDAO variable;

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "dataset_id")
   @JsonIgnore
   public DataSetDAO dataset;

   @NotNull
   @Column(columnDefinition = "timestamp")
   public Instant timestamp;

   @NotNull
   public boolean confirmed;

   public String description;

   @JsonProperty("dataset")
   public DataSetDAO.Info getDatasetId() {
      if (dataset != null) {
         return dataset.getInfo();
      } else {
         return null;
      }
   }

   @Override
   public String toString() {
      return "Change{" +
            "id=" + id +
            ", variable=" + variable.id +
            ", dataset=" + dataset.id + " (" + dataset.run.id + "/" + dataset.ordinal + ")" +
            ", timestamp=" + timestamp +
            ", confirmed=" + confirmed +
            ", description='" + description + '\'' +
            '}';
   }

   public static ChangeDAO fromDatapoint(DataPointDAO dp) {
      ChangeDAO change = new ChangeDAO();
      change.variable = dp.variable;
      change.timestamp = dp.timestamp;
      change.dataset = dp.dataset;
      return change;
   }

   public static class Event {
      public ChangeDAO change;
      public String testName;
      public DataSetDAO.Info dataset;
      public boolean notify;

      public Event() {}

      public Event(ChangeDAO change, String testName, DataSetDAO.Info dataset, boolean notify) {
         this.change = change;
         this.testName = testName;
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
