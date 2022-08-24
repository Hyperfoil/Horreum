package io.hyperfoil.tools.horreum.experiment;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.api.ConditionConfig;
import io.hyperfoil.tools.horreum.api.ExperimentService;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;

public interface ExperimentConditionModel {
   ConditionConfig config();

   ExperimentService.ComparisonResult compare(JsonNode config, List<DataPoint> baseline, DataPoint newDatapoint);
}
