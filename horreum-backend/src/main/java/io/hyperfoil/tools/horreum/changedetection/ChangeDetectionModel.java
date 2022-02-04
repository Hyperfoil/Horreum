package io.hyperfoil.tools.horreum.changedetection;

import io.hyperfoil.tools.horreum.api.ChangeDetectionModelConfig;
import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;

import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;

public interface ChangeDetectionModel {
    ChangeDetectionModelConfig config();

    void analyze(List<DataPoint> dataPoints, JsonNode configuration, Consumer<Change> changeConsumer);

}
