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
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.tools.horreum.entity.data.DataSetDAO;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * For each {@link VariableDAO} a datapoint will be created for each run.
 */
@Entity(name = "DataPoint")
@Table(name = "DataPoint")
public class DataPointDAO extends PanacheEntityBase {
   public static final String EVENT_NEW = "datapoint/new";
   public static final String EVENT_DELETED = "datapoint/deleted";
   public static final String EVENT_DATASET_PROCESSED = "datapoint/dataset_processed";

   @Id
   @GeneratedValue(strategy = GenerationType.AUTO)
   @Column(columnDefinition = "SERIAL")
   public Integer id;

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "dataset_id")
   @JsonIgnore
   public DataSetDAO dataset;

   @NotNull
   @Column(columnDefinition = "timestamp")
   public Instant timestamp;

   @NotNull
   public double value;

   // for easier use in lambdas
   public double value() {
      return value;
   }

   @Override
   public String toString() {
      return id + "|" + dataset.id + "@" + timestamp + ": " + value;
   }

   @NotNull
   @ManyToOne(fetch = FetchType.LAZY)
   public VariableDAO variable;

   @JsonProperty("datasetId")
   public void setDatasetId(int datasetId) {
      dataset = DataSetDAO.getEntityManager().getReference(DataSetDAO.class, datasetId);
   }

   @JsonProperty("datasetId")
   public int getDatasetId() {
      return dataset.id;
   }

   public static class Event {
      public DataPointDAO dataPoint;
      public int testId;
      public boolean notify;

      public Event() {
      }

      public Event(DataPointDAO dataPoint, int testId, boolean notify) {
         this.dataPoint = dataPoint;
         this.testId = testId;
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

   public static class DatasetProcessedEvent {
      public DataSetDAO.Info dataset;
      public boolean notify;

      public DatasetProcessedEvent() {}

      public DatasetProcessedEvent(DataSetDAO.Info dataset, boolean notify) {
         this.dataset = dataset;
         this.notify = notify;
      }
   }
}
