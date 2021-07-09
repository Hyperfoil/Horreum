package io.hyperfoil.tools.horreum.regression;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;
import io.hyperfoil.tools.horreum.entity.alerting.Variable;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.function.Consumer;

public class StatisticalVarianceRegressionModel implements RegressionModel{

    private static final Logger log = Logger.getLogger(StatisticalVarianceRegressionModel.class);

    @Override
    public void analyze(DataPoint dataPoint, List<DataPoint> previousDataPoints, Consumer<Change> regressionCallback) {
        // Last datapoint is already in the list
        if (previousDataPoints.isEmpty()) {
            log.error("The published datapoint should be already in the list");
            return;
        }

        Variable variable = Variable.findById(dataPoint.variable.id);

        DataPoint firstDatapoint = previousDataPoints.get(0);
        if (!firstDatapoint.id.equals(dataPoint.id)) {
            log.debugf("Ignoring datapoint %d from %s as there's a newer datapoint %d from %s",
                    dataPoint.id, dataPoint.timestamp, firstDatapoint.id, firstDatapoint.timestamp);
            return;
        }
        if (previousDataPoints.size() <= Math.max(1, variable.minWindow)) {
            log.debugf("Too few (%d) previous datapoints for variable %d, skipping analysis", previousDataPoints.size() - 1, variable.id);
            return;
        }
        SummaryStatistics statistics = new SummaryStatistics();
        previousDataPoints.stream().skip(1).mapToDouble(DataPoint::value).forEach(statistics::addValue);
        double ratio = dataPoint.value/statistics.getMean();
        if (ratio < 1 - variable.maxDifferenceLastDatapoint || ratio > 1 + variable.maxDifferenceLastDatapoint) {
            log.debugf("Value %f exceeds %f +- %f%% (based on %d datapoints stddev is %f)",
                    dataPoint.value, statistics.getMean(),
                    variable.maxDifferenceLastDatapoint, previousDataPoints.size() - 1, statistics.getStandardDeviation());
            Change change = new Change();
            change.variable = firstDatapoint.variable;
            change.timestamp = firstDatapoint.timestamp;
            change.runId = firstDatapoint.runId;
            change.description = "Last datapoint is out of range: value=" +
                    dataPoint.value + ", mean=" + statistics.getMean() + ", count=" + statistics.getN() + " stddev=" + statistics.getStandardDeviation() +
                    ", range=" + ((1 - variable.maxDifferenceLastDatapoint) * statistics.getMean()) +
                    ".." + ((1 + variable.maxDifferenceLastDatapoint) * statistics.getMean());
            regressionCallback.accept(change);
        } else if (previousDataPoints.size() >= 2 * variable.floatingWindow){
            SummaryStatistics older = new SummaryStatistics(), window = new SummaryStatistics();
            previousDataPoints.stream().skip(variable.floatingWindow).mapToDouble(dp -> dp.value).forEach(older::addValue);
            previousDataPoints.stream().limit(variable.floatingWindow).mapToDouble(dp -> dp.value).forEach(window::addValue);

            double floatingRatio = window.getMean() / older.getMean();
            if (floatingRatio < 1 - variable.maxDifferenceFloatingWindow || floatingRatio > 1 + variable.maxDifferenceFloatingWindow) {
                DataPoint dp = null;
                // We cannot know which datapoint is first with the regression; as a heuristic approach
                // we'll select first datapoint with value lower than mean (if this is a drop, e.g. throughput)
                // or above the mean (if this is an increase, e.g. memory usage).
                for (int i = variable.floatingWindow - 1; i >= 0; --i) {
                    dp = previousDataPoints.get(i);
                    if (floatingRatio < 1 && dp.value < older.getMean()) {
                        break;
                    } else if (floatingRatio > 1 && dp.value > older.getMean()) {
                        break;
                    }
                }
                Change change = new Change();
                change.variable = dp.variable;
                change.timestamp = dp.timestamp;
                change.runId = dp.runId;
                change.description = String.format("Change detected in floating window, runs %d (%s) - %d (%s): mean %f (stddev %f), previous mean %f (stddev %f)",
                        previousDataPoints.get(variable.floatingWindow - 1).runId, previousDataPoints.get(variable.floatingWindow - 1).timestamp,
                        previousDataPoints.get(0).runId, previousDataPoints.get(0).timestamp,
                        window.getMean(), window.getStandardDeviation(), older.getMean(), older.getStandardDeviation());

                log.debug(change.description);
                regressionCallback.accept(change);
            }
        }
    }
}
