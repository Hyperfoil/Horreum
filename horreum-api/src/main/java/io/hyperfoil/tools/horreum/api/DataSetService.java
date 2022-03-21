package io.hyperfoil.tools.horreum.api;

import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.entity.json.Label;

public interface DataSetService {

   public Label materialize(DataSet dataSet);
}
