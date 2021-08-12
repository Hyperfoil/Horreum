package io.hyperfoil.tools.horreum.svc;

import java.util.Set;

public class MissingRunValuesEvent {
   public final int runId;
   public final int testId;
   public final Set<String> variables;
   public final boolean notify;

   public MissingRunValuesEvent(int runId, int testId, Set<String> variables, boolean notify) {
      this.runId = runId;
      this.testId = testId;
      this.variables = variables;
      this.notify = notify;
   }
}
