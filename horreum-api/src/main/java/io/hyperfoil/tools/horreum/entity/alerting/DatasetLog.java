package io.hyperfoil.tools.horreum.entity.alerting;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.ConstraintMode;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * This table is meant to host logged events with relation to {@link DataSet datasets},
 * as opposed to events related directly to {@link Run runs}.
 */
@Entity
public class DatasetLog extends PanacheEntityBase {
   public static final int DEBUG = 0;
   public static final int INFO = 1;
   public static final int WARN = 2;
   public static final int ERROR = 3;

   @Id
   @GeneratedValue
   public Long id;

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "testid", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
   @JsonIgnore
   public Test test;

   @ManyToOne(fetch = FetchType.EAGER, optional = false)
   @JoinColumn(name = "dataset_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
   @JsonIgnore
   public DataSet dataset;

   @NotNull
   public int level;

   @NotNull
   @Column(columnDefinition = "timestamp")
   public Instant timestamp;

   @NotNull
   public String source;

   @NotNull
   public String message;

   public DatasetLog() {
   }

   public DatasetLog(Test test, DataSet dataset, int level, String source, String message) {
      this.test = test;
      this.dataset = dataset;
      this.level = level;
      this.timestamp = Instant.now();
      this.source = source;
      this.message = message;
   }

   @JsonProperty("testId")
   private int getTestId() {
      return test.id;
   }

   @JsonProperty("runId")
   private int getRunId() {
      return dataset.run.id;
   }

   @JsonProperty("datasetId")
   private int getDatasetId() {
      return dataset.id;
   }

   @JsonProperty("datasetOrdinal")
   private int getDatasetOrdinal() {
      return dataset.ordinal;
   }
}
