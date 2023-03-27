package io.hyperfoil.tools.horreum.experiment;

import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.api.ConditionConfig;
import io.hyperfoil.tools.horreum.api.services.ExperimentService;
import io.hyperfoil.tools.horreum.entity.alerting.DataPointDAO;

public class RelativeDifferenceExperimentModel implements ExperimentConditionModel {
   public static final String NAME = "relativeDifference";

   @Override
   public ConditionConfig config() {
      return new ConditionConfig(NAME, "Relative difference of mean", "This model compares mean value of previous datapoints vs. current value.")
            .addComponent("threshold", new ConditionConfig.LogSliderComponent(100, 1, 1000, 0.1, false, "%"), "Threshold", "Threshold value that marks the difference as significant, in percent.")
            .addComponent("greaterBetter", new ConditionConfig.SwitchComponent(), "Greater is better", "If the datapoint has higher value than the mean of baseline the result is considered better.")
            .addComponent("maxBaselineDatasets", new ConditionConfig.LogSliderComponent(1, 0, 1000, 0, true, ""), "Max. datasets in baseline", "Maximum number of (newest) datasets in the baseline considered for calculating the average. Zero means no limit.");
   }

   @Override
   public ExperimentService.ComparisonResult compare(JsonNode config, List<DataPointDAO> baseline, DataPointDAO newDatapoint) {
      int maxBaselineDatasets = config.get("maxBaselineDatasets").asInt(0);
      Stream<DataPointDAO> stream = baseline.stream();
      if (maxBaselineDatasets > 0) {
         stream = stream.limit(maxBaselineDatasets);
      }
      OptionalDouble mean = stream.mapToDouble(dp -> dp.value).average();
      if (mean.isEmpty()) {
         throw new IllegalArgumentException("Empty baseline");
      }
      double diff = newDatapoint.value / mean.getAsDouble() - 1;
      double threshold = config.get("threshold").asDouble(0);
      boolean greaterBetter = config.get("greaterBetter").asBoolean(true);
      ExperimentService.BetterOrWorse overall = ExperimentService.BetterOrWorse.SAME;
      if (diff > threshold) {
         overall = greaterBetter ? ExperimentService.BetterOrWorse.BETTER : ExperimentService.BetterOrWorse.WORSE;
      } else if (diff < -threshold) {
         overall = greaterBetter ? ExperimentService.BetterOrWorse.WORSE : ExperimentService.BetterOrWorse.BETTER;
      }
      return new ExperimentService.ComparisonResult(overall, newDatapoint.value, mean.getAsDouble(), String.format("%+.2f%%", 100 * diff));
   }
}
