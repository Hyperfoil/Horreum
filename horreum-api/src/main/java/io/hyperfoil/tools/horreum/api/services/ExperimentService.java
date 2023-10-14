package io.hyperfoil.tools.horreum.api.services;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hyperfoil.tools.horreum.api.ConditionConfig;
import io.hyperfoil.tools.horreum.api.alerting.DatasetLog;
import io.hyperfoil.tools.horreum.api.data.DataSet;
import io.hyperfoil.tools.horreum.api.data.ExperimentComparison;
import io.hyperfoil.tools.horreum.api.data.ExperimentProfile;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;


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

      public ExperimentProfile profile;
      public List<DatasetLog> logs;
      public DataSet.Info datasetInfo;
      public List<DataSet.Info> baseline;

      @JsonSerialize(keyUsing = ExperimentComparisonSerializer.class)
      @JsonDeserialize(keyUsing = ExperimentComparisonDeserializer.class)
      public Map<ExperimentComparison, ComparisonResult> results;

      @Schema(implementation = String.class)
      public JsonNode extraLabels;
      public boolean notify;

      public ExperimentResult() {
      }

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
      public BetterOrWorse overall;
      public double experimentValue;
      public double baselineValue;
      public String result;

      public ComparisonResult() {
         this.overall = null;
         this.experimentValue = 0.0;
         this.baselineValue = 0.0;
         this.result = null;
      }

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

   class ExperimentComparisonDeserializer extends KeyDeserializer {
      @Override
      public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
         return key;
      }
   }
}
