package io.hyperfoil.tools.horreum.changedetection;

import io.hyperfoil.tools.horreum.api.ConditionConfig;
import io.hyperfoil.tools.horreum.entity.alerting.ChangeDAO;
import io.hyperfoil.tools.horreum.entity.alerting.DataPointDAO;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;

public class RelativeDifferenceChangeDetectionModel implements ChangeDetectionModel {

    public static final String NAME = "relativeDifference";
    private static final Logger log = Logger.getLogger(RelativeDifferenceChangeDetectionModel.class);

    @Override
    public ConditionConfig config() {
        return new ConditionConfig(NAME, "Relative difference of means",
            "This is a generic filter that splits the dataset into two subsets: the 'floating window' " +
                  "and preceding datapoints. It calculates the mean of preceding datapoints and applies " +
                  "the 'filter' function on the window of last datapoints; it compares these two values and " +
                  "if the relative difference is greater than the threshold the change is emitted.\n" +
                  "In case that window is set to 1 this becomes a simple comparison of the most recent datapoint " +
                  "against the previous average value.")
              .addComponent("threshold", new ConditionConfig.LogSliderComponent(100, 1, 1000, 0.2, false, "%"),
                    "Threshold for relative difference",
                    "Maximum difference between the aggregated value of last <window> datapoints and the mean of preceding values."
              )
              .addComponent("window", new ConditionConfig.LogSliderComponent(1, 1, 1000, 1, true, " "),
                    "Minimum window",
                    "Number of most recent datapoints used for aggregating the value for comparison.")
              .addComponent("minPrevious", new ConditionConfig.LogSliderComponent(1, 1, 1000, 5, true, " "),
                    "Minimal number of preceding datapoints",
                    "Number of datapoints preceding the aggregation window.")
              .addComponent("filter", new ConditionConfig.EnumComponent("mean").add("mean", "Mean value").add("min", "Minimum value").add("max", "Maximum value"),
                    "Aggregation function for the floating window",
                    "Function used to aggregate datapoints from the floating window.");
    }

    @Override
    public void analyze(List<DataPointDAO> dataPoints, JsonNode configuration, Consumer<ChangeDAO> changeConsumer) {
        DataPointDAO dataPoint = dataPoints.get(0);

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
            change.description = String.format("Datasets %d/%d (%s) - %d/%d (%s): %s %f, previous mean %f (stddev %f), relative change %.2f%%",
                    prevDataPoint.dataset.run.id, prevDataPoint.dataset.ordinal, prevDataPoint.timestamp,
                    lastDataPoint.dataset.run.id, lastDataPoint.dataset.ordinal, lastDataPoint.timestamp,
                    filter, filteredValue, previousStats.getMean(), previousStats.getStandardDeviation(), 100 * (ratio - 1));

            log.debug(change.description);
            changeConsumer.accept(change);
        }

    }
}
