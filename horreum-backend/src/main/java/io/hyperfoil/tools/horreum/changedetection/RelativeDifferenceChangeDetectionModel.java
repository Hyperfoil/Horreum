package io.hyperfoil.tools.horreum.changedetection;

import static io.hyperfoil.tools.horreum.api.ChangeDetectionModelConfig.ComponentType.ENUM;
import static io.hyperfoil.tools.horreum.api.ChangeDetectionModelConfig.ComponentType.LOG_SLIDER;

import io.hyperfoil.tools.horreum.api.ChangeDetectionModelConfig;
import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public class RelativeDifferenceChangeDetectionModel implements ChangeDetectionModel {

    public static final String NAME = "relativeDifference";
    private static final Logger log = Logger.getLogger(RelativeDifferenceChangeDetectionModel.class);

    @Override
    public ChangeDetectionModelConfig config() {
        return new ChangeDetectionModelConfig(NAME, "Relative difference of means",
            "This is a generic filter that splits the dataset into two subsets: the 'floating window' " +
                  "and preceding datapoints. It calculates the mean of preceding datapoints and applies " +
                  "the 'filter' function on the window of last datapoints; it compares these two values and " +
                  "if the relative difference is greater than the threshold the change is emitted.\n" +
                  "In case that window is set to 1 this becomes a simple comparison of the most recent datapoint " +
                  "against the previous average value.")
              .addComponent("threshold", JsonNodeFactory.instance.numberNode(0.2), LOG_SLIDER, "Threshold for relative difference",
                    "Maximum difference between the aggregated value of last <window> datapoints and the mean of preceding values.")
                .addProperty("scale", 100).addProperty("min", 1).addProperty("max", 1000).addProperty("unit", "%").end()
              .addComponent("window", JsonNodeFactory.instance.numberNode(1), LOG_SLIDER, "Minimum window",
                    "Number of most recent datapoints used for aggregating the value for comparison.")
                .addProperty("min", 1).addProperty("max", 1000).addProperty("unit", " ").end()
              .addComponent("minPrevious", JsonNodeFactory.instance.numberNode(5), LOG_SLIDER, "Minimal number of preceding datapoints",
                    "Number of datapoints preceding the aggregation window.")
                .addProperty("min", 1).addProperty("max", 1000).addProperty("unit", " ").end()
              .addComponent("filter", JsonNodeFactory.instance.textNode("mean"), ENUM, "Aggregation function for the floating window",
                    "Function used to aggregate datapoints from the floating window.")
                .addProperty("options", Map.of("mean", "Mean value", "min", "Minimum value", "max", "Maximum value")).end();
    }

    @Override
    public void analyze(List<DataPoint> dataPoints, JsonNode configuration, Consumer<Change> changeConsumer) {
        DataPoint dataPoint = dataPoints.get(0);

        double threshold = Math.max(0, configuration.get("threshold").asDouble());
        int window = Math.max(1, configuration.get("window").asInt());
        int minPrevious = Math.max(window, configuration.get("minPrevious").asInt());
        String filter = configuration.get("filter").asText();

        if (dataPoints.size() < minPrevious + window) {
            log.debugf("Too few (%d) previous datapoints for variable %d, skipping analysis", dataPoints.size() - window, dataPoint.variable.id);
            return;
        }
        SummaryStatistics previousStats = new SummaryStatistics();
        dataPoints.stream().skip(window).mapToDouble(dp -> dp.value).forEach(previousStats::addValue);

        double filteredValue;
        switch (filter) {
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
                log.errorf("Unsupported option 'filter'='%s' for variable %d, skipping analysis.", filter, dataPoint.variable.id);
                return;
        }

        double ratio = filteredValue / previousStats.getMean();
        log.tracef("Previous mean %f, filtered value %f, ratio %f", previousStats.getMean(), filteredValue, ratio);
        if (ratio < 1 - threshold || ratio > 1 + threshold) {
            DataPoint dp = null;
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
            Change change = new Change();
            change.variable = dp.variable;
            change.timestamp = dp.timestamp;
            change.run = dp.run;
            change.description = String.format("Change detected, runs %d (%s) - %d (%s): %s %f, previous mean %f (stddev %f)",
                    dataPoints.get(window - 1).run.id, dataPoints.get(window - 1).timestamp,
                    dataPoints.get(0).run.id, dataPoints.get(0).timestamp, filter, filteredValue,
                    previousStats.getMean(), previousStats.getStandardDeviation());

            log.debug(change.description);
            changeConsumer.accept(change);
        }

    }
}
