package io.hyperfoil.tools.horreum.experiment;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.api.ConditionConfig;
import io.hyperfoil.tools.horreum.api.services.ExperimentService;
import io.hyperfoil.tools.horreum.entity.alerting.DataPointDAO;

public interface ExperimentConditionModel {
   ConditionConfig config();

   ExperimentService.ComparisonResult compare(JsonNode config, List<DataPointDAO> baseline, DataPointDAO newDatapoint);
}
