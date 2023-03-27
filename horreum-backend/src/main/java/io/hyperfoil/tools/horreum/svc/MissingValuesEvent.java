package io.hyperfoil.tools.horreum.svc;

import java.util.Set;

import io.hyperfoil.tools.horreum.entity.data.DataSetDAO;

public class MissingValuesEvent {
   public DataSetDAO.Info dataset;
   public Set<String> variables;
   public boolean notify;

   public MissingValuesEvent() {
   }

   public MissingValuesEvent(DataSetDAO.Info dataset, Set<String> variables, boolean notify) {
      this.dataset = dataset;
      this.variables = variables;
      this.notify = notify;
   }
}
