package io.hyperfoil.tools.horreum.entity.alerting;


import javax.persistence.ConstraintMode;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import io.hyperfoil.tools.horreum.entity.PersistentLog;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;

@Entity(name = "TransformationLog")
public class TransformationLogDAO extends PersistentLog {

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
