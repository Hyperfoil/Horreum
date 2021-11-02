package io.hyperfoil.tools.horreum.entity.alerting;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.validation.constraints.NotNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
public class CalculationLog extends PanacheEntityBase {
   public static final int DEBUG = 0;
   public static final int INFO = 1;
   public static final int WARN = 2;
   public static final int ERROR = 3;

   @Id
   @GeneratedValue
   public Long id;

   @NotNull
   public int testId;

   @NotNull
   public int runId;

   @NotNull
   public int level;

   @NotNull
   @Column(columnDefinition = "timestamp")
   public Instant timestamp;

   @NotNull
   public String message;

   public CalculationLog() {
   }

   public CalculationLog(int testId, int runId, int level, String message) {
      this.testId = testId;
      this.runId = runId;
      this.level = level;
      this.timestamp = Instant.now();
      this.message = message;
   }
}
