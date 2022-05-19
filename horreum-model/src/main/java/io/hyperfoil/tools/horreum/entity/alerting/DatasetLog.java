package io.hyperfoil.tools.horreum.entity.alerting;

import javax.persistence.ConstraintMode;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.tools.horreum.entity.PersistentLog;
import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Test;

/**
 * This table is meant to host logged events with relation to {@link DataSet datasets},
 * as opposed to events related directly to {@link Run runs}.
 */
@Entity
public class DatasetLog extends PersistentLog {

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "testid", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
   @JsonIgnore
   public Test test;

   @ManyToOne(fetch = FetchType.EAGER, optional = false)
   @JoinColumn(name = "dataset_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
   @JsonIgnore
   public DataSet dataset;

   @NotNull
   public String source;

   public DatasetLog() {
      super(0, null);
   }

   public DatasetLog(Test test, DataSet dataset, int level, String source, String message) {
      super(level, message);
      this.test = test;
      this.dataset = dataset;
      this.source = source;
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
