package io.hyperfoil.tools.horreum.entity.alerting;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import io.hyperfoil.tools.horreum.entity.PersistentLogDAO;
import io.hyperfoil.tools.horreum.entity.data.DataSetDAO;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

/**
 * This table is meant to host logged events with relation to {@link DataSetDAO datasets},
 * as opposed to events related directly to {@link RunDAO runs}.
 */
@Entity(name = "DatasetLog")
public class DatasetLogDAO extends PersistentLogDAO {

   @Id
   @GenericGenerator(
           name = "datasetlog_id_generator",
           strategy = "io.hyperfoil.tools.horreum.entity.SeqIdGenerator",
           parameters = {
                   @org.hibernate.annotations.Parameter(name = SequenceStyleGenerator.INCREMENT_PARAM, value = "1"),
           }
   )
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "datasetlog_id_generator")
   public Long id;

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
