package io.hyperfoil.tools.horreum.entity;

import javax.persistence.Entity;
import javax.validation.constraints.NotNull;

@Entity
public class ActionLog extends PersistentLog {
   @NotNull
   public int testId;

   @NotNull
   public String event;

   public String type;

   public ActionLog() {
      super(0, null);
   }

   public ActionLog(int level, int testId, String event, String type, String message) {
      super(level, message);
      this.testId = testId;
      this.event = event;
      this.type = type;
   }
}
