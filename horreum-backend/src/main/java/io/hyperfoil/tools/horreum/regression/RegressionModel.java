package io.hyperfoil.tools.horreum.regression;

import io.hyperfoil.tools.horreum.api.RegressionModelConfig;
import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;

import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;

public interface RegressionModel {
    RegressionModelConfig config();

    void analyze(List<DataPoint> dataPoints, JsonNode configuration, Consumer<Change> regressionCallback);

}
