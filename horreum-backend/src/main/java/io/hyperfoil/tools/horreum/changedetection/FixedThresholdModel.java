package io.hyperfoil.tools.horreum.changedetection;

import java.util.List;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import io.hyperfoil.tools.horreum.api.data.ConditionConfig;
import io.hyperfoil.tools.horreum.api.data.changeDetection.ChangeDetectionModelType;
import io.hyperfoil.tools.horreum.api.data.changeDetection.FixedThresholdDetectionConfig;
import io.hyperfoil.tools.horreum.entity.alerting.ChangeDAO;
import io.hyperfoil.tools.horreum.entity.alerting.DataPointDAO;
import io.quarkus.logging.Log;

@ApplicationScoped
public class FixedThresholdModel implements ChangeDetectionModel {

    @Inject
    ObjectMapper mapper;

    @Override
    public ConditionConfig config() {
        ConditionConfig conditionConfig = new ConditionConfig(ChangeDetectionModelType.names.FIXED_THRESHOLD, "Fixed Threshold",
                "This model checks that the datapoint value is within fixed bounds.")
                .addComponent("min", new ConditionConfig.NumberBound(), "Minimum",
                        "Lower bound for acceptable datapoint values.")
                .addComponent("max", new ConditionConfig.NumberBound(), "Maximum",
                        "Upper bound for acceptable datapoint values.");
        conditionConfig.defaults.put("model", new TextNode(ChangeDetectionModelType.names.FIXED_THRESHOLD));
        return conditionConfig;
    }

    @Override
    public ChangeDetectionModelType type() {
        return ChangeDetectionModelType.FIXED_THRESHOLD;
    }

    @Override
    public void analyze(List<DataPointDAO> dataPoints, JsonNode configuration, Consumer<ChangeDAO> changeConsumer)
            throws ChangeDetectionException {
        DataPointDAO dp = dataPoints.get(0);

        try {
            FixedThresholdDetectionConfig config = mapper.treeToValue(configuration, FixedThresholdDetectionConfig.class);

            if (config.min.enabled) {
                if ((!config.min.inclusive && dp.value <= config.min.value) || dp.value < config.min.value) {
                    ChangeDAO c = ChangeDAO.fromDatapoint(dp);
                    c.description = "%f is below lower bound %f (%s)".formatted(dp.value, config.min.value,
                            config.min.inclusive ? "inclusive" : "exclusive");
                    Log.debug(c.description);
                    changeConsumer.accept(c);
                    return;
                }
            }
            if (config.max.enabled) {
                if ((!config.max.inclusive && dp.value >= config.max.value) || dp.value > config.max.value) {
                    ChangeDAO c = ChangeDAO.fromDatapoint(dp);
                    c.description = "%f is above upper bound %f (%s)".formatted(dp.value, config.max.value,
                            config.max.inclusive ? "inclusive" : "exclusive");
                    Log.debug(c.description);
                    changeConsumer.accept(c);
                }
            }

        } catch (JsonProcessingException e) {
            String errMsg = "Failed to parse configuration for variable %d".formatted(dp.variable.id);
            Log.error(errMsg, e);
            throw new ChangeDetectionException(errMsg, e);
        }
    }

    @Override
    public ModelType getType() {
        return ModelType.CONTINOUS;
    }
}
