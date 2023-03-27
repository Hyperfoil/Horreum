package io.hyperfoil.tools.horreum.api.services;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.api.ConditionConfig;
import io.hyperfoil.tools.horreum.api.data.ExperimentComparison;
import io.hyperfoil.tools.horreum.api.data.ExperimentProfile;
import io.hyperfoil.tools.horreum.api.alerting.DatasetLog;
import io.hyperfoil.tools.horreum.api.data.DataSet;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;


@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
@Path("/api/experiment")
public interface ExperimentService {
   @GET
   @Path("{testId}/profiles")
   Collection<ExperimentProfile> profiles(@PathParam("testId") int testId);

   @POST
   @Path("{testId}/profiles")
   int addOrUpdateProfile(@PathParam("testId") int testId, @RequestBody(required = true) ExperimentProfile profile);

   @DELETE
   @Path("{testId}/profiles/{profileId}")
   void deleteProfile(@PathParam("testId") int testId, @PathParam("profileId") int profileId);

   @GET
   @Path("models")
   List<ConditionConfig> models();

   @GET
   @Path("run")
   List<ExperimentResult> runExperiments(@QueryParam("datasetId") int datasetId);

   enum BetterOrWorse {
      BETTER,
      SAME,
      WORSE
   }

   @Schema(name = "ExperimentResult")
   class ExperimentResult {
      public static final String NEW_RESULT = "experiment_result/new";

      public final ExperimentProfile profile;
      public final List<DatasetLog> logs;
      public final DataSet.Info datasetInfo;
      public final List<DataSet.Info> baseline;
      @JsonSerialize(keyUsing = ExperimentComparisonSerializer.class)
      public final Map<ExperimentComparison, ComparisonResult> results;
      public final JsonNode extraLabels;
      public final boolean notify;

      public ExperimentResult(ExperimentProfile profile, List<DatasetLog> logs,
                              DataSet.Info datasetInfo, List<DataSet.Info> baseline,
                              Map<ExperimentComparison, ComparisonResult> results,
                              JsonNode extraLabels, boolean notify) {
         this.profile = profile;
         this.logs = logs;
         this.datasetInfo = datasetInfo;
         this.baseline = baseline;
         this.results = results;
         this.extraLabels = extraLabels;
         this.notify = notify;
      }
   }

   @Schema(name = "ComparisonResult")
   class ComparisonResult {
      public final BetterOrWorse overall;
      public final double experimentValue;
      public final double baselineValue;
      public final String result;

      public ComparisonResult(BetterOrWorse overall, double experimentValue, double baselineValue, String result) {
         this.overall = overall;
         this.experimentValue = experimentValue;
         this.baselineValue = baselineValue;
         this.result = result;
      }
   }

   class ExperimentComparisonSerializer extends JsonSerializer<ExperimentComparison> {
      @Override
      public void serialize(ExperimentComparison value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
         gen.writeFieldName(value.variableName);
      }
   }
}
