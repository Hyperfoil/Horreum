package io.hyperfoil.tools.horreum.changedetection;

import java.util.List;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.api.ChangeDetectionModelConfig;
import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;

public class FixedThresholdModel implements ChangeDetectionModel {
   private static final Logger log = Logger.getLogger(FixedThresholdModel.class);
   public static final String NAME = "fixedThreshold";

   @Override
   public ChangeDetectionModelConfig config() {
      return new ChangeDetectionModelConfig(NAME, "Fixed Threshold", "This model checks that the datapoint value is within fixed bounds.")
            .addComponent("min", new ChangeDetectionModelConfig.NumberBound(), "Minimum", "Lower bound for acceptable datapoint values.")
            .addComponent("max", new ChangeDetectionModelConfig.NumberBound(), "Maximum", "Upper bound for acceptable datapoint values.");
   }

   @Override
   public void analyze(List<DataPoint> dataPoints, JsonNode configuration, Consumer<Change> changeConsumer) {
      DataPoint dp = dataPoints.get(0);

      JsonNode min = configuration.path("min");
      boolean minEnabled = min.path("enabled").asBoolean();
      boolean minInclusive = min.path("inclusive").asBoolean();
      double minValue = min.path("value").asDouble();
      JsonNode max = configuration.path("max");
      boolean maxEnabled = max.path("enabled").asBoolean();
      boolean maxInclusive = max.path("inclusive").asBoolean();
      double maxValue = max.path("value").asDouble();

      if (minEnabled) {
         if ((!minInclusive && dp.value <= minValue) || dp.value < minValue) {
            Change c = Change.fromDatapoint(dp);
            c.description = String.format("%f is below lower bound %f (%s)", dp.value, minValue, minInclusive ? "inclusive" : "exclusive");
            log.debug(c.description);
            changeConsumer.accept(c);
            return;
         }
      }
      if (maxEnabled) {
         if ((!maxInclusive && dp.value >= maxValue) || dp.value > maxValue) {
            Change c = Change.fromDatapoint(dp);
            c.description = String.format("%f is above upper bound %f (%s)", dp.value, maxValue, maxInclusive ? "inclusive" : "exclusive");
            log.debug(c.description);
            changeConsumer.accept(c);
         }
      }
   }
}
