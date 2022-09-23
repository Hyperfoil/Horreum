package io.hyperfoil.tools.horreum.svc;

import java.util.Set;

import io.hyperfoil.tools.horreum.entity.json.DataSet;

public class MissingValuesEvent {
   public final DataSet.Info dataset;
   public final Set<String> variables;
   public final boolean notify;

   public MissingValuesEvent(DataSet.Info dataset, Set<String> variables, boolean notify) {
      this.dataset = dataset;
      this.variables = variables;
      this.notify = notify;
   }
}
