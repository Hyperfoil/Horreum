package io.hyperfoil.tools.horreum.entity;

import javax.persistence.Entity;
import javax.validation.constraints.NotNull;

@Entity(name = "ActionLog")
public class ActionLogDAO extends PersistentLog {
   @NotNull
   public int testId;

   @NotNull
   public String event;

   public String type;

   public ActionLogDAO() {
      super(0, null);
   }

   public ActionLogDAO(int level, int testId, String event, String type, String message) {
      super(level, message);
      this.testId = testId;
      this.event = event;
      this.type = type;
   }
}
