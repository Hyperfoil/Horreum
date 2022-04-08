package io.hyperfoil.tools.horreum.svc;

import java.util.Set;

public class MissingValuesEvent {
   public final int runId;
   public final int datasetId;
   public final int datasetOrdinal;
   public final int testId;
   public final Set<String> variables;
   public final boolean notify;

   public MissingValuesEvent(int runId, int datasetId, int datasetOrdinal, int testId, Set<String> variables, boolean notify) {
      this.runId = runId;
      this.datasetId = datasetId;
      this.datasetOrdinal = datasetOrdinal;
      this.testId = testId;
      this.variables = variables;
      this.notify = notify;
   }
}
