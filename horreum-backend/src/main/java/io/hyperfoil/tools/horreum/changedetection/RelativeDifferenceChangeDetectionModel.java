package io.hyperfoil.tools.horreum.changedetection;

import java.util.List;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import io.hyperfoil.tools.horreum.api.data.ConditionConfig;
import io.hyperfoil.tools.horreum.api.data.changeDetection.ChangeDetectionModelType;
import io.hyperfoil.tools.horreum.api.data.changeDetection.RelativeDifferenceDetectionConfig;
import io.hyperfoil.tools.horreum.entity.alerting.ChangeDAO;
import io.hyperfoil.tools.horreum.entity.alerting.DataPointDAO;
import io.quarkus.logging.Log;

@ApplicationScoped
public class RelativeDifferenceChangeDetectionModel implements ChangeDetectionModel {

    @Inject
    ObjectMapper mapper;

    @Override
    public ConditionConfig config() {
        ConditionConfig conditionConfig = new ConditionConfig(ChangeDetectionModelType.names.RELATIVE_DIFFERENCE,
                "Relative difference of means",
                "This is a generic filter that splits the dataset into two subsets: the 'floating window' " +
                        "and preceding datapoints. It calculates the mean of preceding datapoints and applies " +
                        "the 'filter' function on the window of last datapoints; it compares these two values and " +
                        "if the relative difference is greater than the threshold the change is emitted.\n" +
                        "In case that window is set to 1 this becomes a simple comparison of the most recent datapoint " +
                        "against the previous average value.")
                .addComponent("threshold", new ConditionConfig.LogSliderComponent(100, 1, 1000, 0.2, false, "%"),
                        "Threshold for relative difference",
                        "Maximum difference between the aggregated value of last <window> datapoints and the mean of preceding values.")
                .addComponent("window", new ConditionConfig.LogSliderComponent(1, 1, 1000, 1, true, " "),
                        "Minimum window",
                        "Number of most recent datapoints used for aggregating the value for comparison.")
                .addComponent("minPrevious", new ConditionConfig.LogSliderComponent(1, 1, 1000, 5, true, " "),
                        "Minimal number of preceding datapoints",
                        "Number of datapoints preceding the aggregation window.")
                .addComponent("filter",
                        new ConditionConfig.EnumComponent("mean").add("mean", "Mean value").add("min", "Minimum value")
                                .add("max", "Maximum value"),
                        "Aggregation function for the floating window",
                        "Function used to aggregate datapoints from the floating window.");
        conditionConfig.defaults.put("model", new TextNode(ChangeDetectionModelType.names.RELATIVE_DIFFERENCE));
        return conditionConfig;
    }

    @Override
    public ChangeDetectionModelType type() {
        return ChangeDetectionModelType.RELATIVE_DIFFERENCE;
    }

    @Override
    public void analyze(List<DataPointDAO> dataPoints, JsonNode configuration, Consumer<ChangeDAO> changeConsumer)
            throws ChangeDetectionException {
        DataPointDAO dataPoint = dataPoints.get(0);

        try {
            RelativeDifferenceDetectionConfig config = mapper.treeToValue(configuration,
                    RelativeDifferenceDetectionConfig.class);

            int window = Math.max(1, config.window);
            int minPrevious = Math.max(window, config.minPrevious);

            if (dataPoints.size() < minPrevious + window) {
                Log.debugf("Too few (%d) previous datapoints for variable %d, skipping analysis", dataPoints.size() - window,
                        dataPoint.variable.id);
                return;
            }
            SummaryStatistics previousStats = new SummaryStatistics();
            dataPoints.stream().skip(window).mapToDouble(dp -> dp.value).forEach(previousStats::addValue);

            double filteredValue;
            switch (config.filter) {
                case "min":
                    //noinspection OptionalGetWithoutIsPresent
                    filteredValue = dataPoints.stream().limit(window).mapToDouble(dp -> dp.value).min().getAsDouble();
                    break;
                case "max":
                    //noinspection OptionalGetWithoutIsPresent
                    filteredValue = dataPoints.stream().limit(window).mapToDouble(dp -> dp.value).max().getAsDouble();
                    break;
                case "mean":
                    SummaryStatistics windowStats = new SummaryStatistics();
                    dataPoints.stream().limit(window).mapToDouble(dp -> dp.value).forEach(windowStats::addValue);
                    filteredValue = windowStats.getMean();
                    break;
                default:
                    String errMsg = "Unsupported option 'filter'='%s' for variable %d, skipping analysis"
                            .formatted(config.filter, dataPoint.variable.id);
                    Log.error(errMsg);
                    throw new ChangeDetectionException(errMsg);
            }

            double ratio = filteredValue / previousStats.getMean();
            Log.tracef("Previous mean %f, filtered value %f, ratio %f", previousStats.getMean(), filteredValue, ratio);
            if (ratio < 1 - config.threshold || ratio > 1 + config.threshold) {
                DataPointDAO dp = null;
                // We cannot know which datapoint is first with the regression; as a heuristic approach
                // we'll select first datapoint with value lower than mean (if this is a drop, e.g. throughput)
                // or above the mean (if this is an increase, e.g. memory usage).
                for (int i = window - 1; i >= 0; --i) {
                    dp = dataPoints.get(i);
                    if (ratio < 1 && dp.value < previousStats.getMean()) {
                        break;
                    } else if (ratio > 1 && dp.value > previousStats.getMean()) {
                        break;
                    }
                }
                assert dp != null;
                ChangeDAO change = ChangeDAO.fromDatapoint(dp);
                DataPointDAO prevDataPoint = dataPoints.get(window - 1);
                DataPointDAO lastDataPoint = dataPoints.get(0);
                change.description = "Datasets %d/%d (%s) - %d/%d (%s): %s %f, previous mean %f (stddev %f), relative change %.2f%%"
                        .formatted(prevDataPoint.dataset.run.id, prevDataPoint.dataset.ordinal, prevDataPoint.timestamp,
                                lastDataPoint.dataset.run.id, lastDataPoint.dataset.ordinal, lastDataPoint.timestamp,
                                config.filter, filteredValue, previousStats.getMean(), previousStats.getStandardDeviation(),
                                100 * (ratio - 1));

                Log.debug(change.description);
                changeConsumer.accept(change);
            }

        } catch (JsonProcessingException e) {
            String errMsg = "Failed to parse configuration for variable %d".formatted(dataPoint.variable.id);
            Log.error(errMsg, e);
            throw new ChangeDetectionException(errMsg, e);
        }

    }

    @Override
    public ModelType getType() {
        return ModelType.CONTINOUS;
    }
}
