package io.hyperfoil.tools.horreum.entity.alerting;

import javax.persistence.ConstraintMode;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import io.hyperfoil.tools.horreum.entity.PersistentLog;
import io.hyperfoil.tools.horreum.entity.data.DataSetDAO;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;

/**
 * This table is meant to host logged events with relation to {@link DataSetDAO datasets},
 * as opposed to events related directly to {@link RunDAO runs}.
 */
@Entity(name = "DatasetLog")
public class DatasetLogDAO extends PersistentLog {

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "testid", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
   public TestDAO test;

   @ManyToOne(fetch = FetchType.EAGER, optional = false)
   @JoinColumn(name = "dataset_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
   public DataSetDAO dataset;

   @NotNull
   public String source;

   public DatasetLogDAO() {
      super(0, null);
   }

   public DatasetLogDAO(TestDAO test, DataSetDAO dataset, int level, String source, String message) {
      super(level, message);
      this.test = test;
      this.dataset = dataset;
      this.source = source;
   }

   private int getTestId() {
      return test.id;
   }

   private int getRunId() {
      return dataset.run.id;
   }

   private int getDatasetId() {
      return dataset.id;
   }

   private int getDatasetOrdinal() {
      return dataset.ordinal;
   }
}
