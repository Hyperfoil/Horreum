package io.hyperfoil.tools.horreum.changedetection;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.api.data.ConditionConfig;
import io.hyperfoil.tools.horreum.api.data.changeDetection.ChangeDetectionModelType;
import io.hyperfoil.tools.horreum.entity.alerting.ChangeDAO;
import io.hyperfoil.tools.horreum.entity.alerting.DataPointDAO;

import java.util.List;
import java.util.function.Consumer;

public interface ChangeDetectionModel {
    ConditionConfig config();

    ChangeDetectionModelType type();
    void analyze(List<DataPointDAO> dataPoints, JsonNode configuration, Consumer<ChangeDAO> changeConsumer);
    ModelType getType();

}
