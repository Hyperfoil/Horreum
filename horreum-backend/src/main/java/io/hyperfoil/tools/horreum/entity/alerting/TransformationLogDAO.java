package io.hyperfoil.tools.horreum.entity.alerting;


import javax.persistence.ConstraintMode;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.tools.horreum.entity.PersistentLog;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;

@Entity(name = "TransformationLog")
public class TransformationLogDAO extends PersistentLog {

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "testid", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
   @JsonIgnore
   public TestDAO test;

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "runid", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
   @JsonIgnore
   public RunDAO run;

   public TransformationLogDAO() {
      super(0, null);
   }

   public TransformationLogDAO(TestDAO test, RunDAO run, int level, String message) {
      super(level, message);
      this.test = test;
      this.run = run;
   }

   @JsonProperty("testId")
   private int getTestId() {
      return test.id;
   }

   @JsonProperty("runId")
   private int getRunId() {
      return run.id;
   }

}
