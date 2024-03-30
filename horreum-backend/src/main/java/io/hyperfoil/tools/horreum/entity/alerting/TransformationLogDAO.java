package io.hyperfoil.tools.horreum.entity.alerting;

import io.hyperfoil.tools.horreum.entity.SeqIdGenerator;
import jakarta.persistence.*;

import io.hyperfoil.tools.horreum.entity.PersistentLogDAO;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import static jakarta.persistence.GenerationType.SEQUENCE;
import static org.hibernate.id.OptimizableGenerator.INCREMENT_PARAM;

@Entity(name = "TransformationLog")
public class TransformationLogDAO extends PersistentLogDAO {
   @Id
   @GenericGenerator(
           name = "transformationlog_id_generator",
           type = SeqIdGenerator.class,
           parameters = { @Parameter(name = INCREMENT_PARAM, value = "1") }
   )
   @GeneratedValue(strategy = SEQUENCE, generator = "transformationlog_id_generator")
   public Long id;

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "testid", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
   public TestDAO test;

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "runid", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
   public RunDAO run;

   public TransformationLogDAO() {
      super(0, null);
   }

   public TransformationLogDAO(TestDAO test, RunDAO run, int level, String message) {
      super(level, message);
      this.test = test;
      this.run = run;
   }

   private int getTestId() {
      return test.id;
   }

   private int getRunId() {
      return run.id;
   }

}
