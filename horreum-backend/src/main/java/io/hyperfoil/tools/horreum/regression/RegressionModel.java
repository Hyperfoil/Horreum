package io.hyperfoil.tools.horreum.regression;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;

import java.util.List;
import java.util.function.Consumer;

public interface RegressionModel {

    void analyze(DataPoint dataPoint, List<DataPoint> previousDataPoints, Consumer<Change> regressionCallback);

}
