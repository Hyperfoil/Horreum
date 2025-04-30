package io.hyperfoil.tools.horreum.api.services;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.api.alerting.DatasetLog;
import io.hyperfoil.tools.horreum.api.data.ConditionConfig;
import io.hyperfoil.tools.horreum.api.data.Dataset;
import io.hyperfoil.tools.horreum.api.data.ExperimentProfile;

@Consumes({ MediaType.APPLICATION_JSON })
@Produces(MediaType.APPLICATION_JSON)
@Path("/api/experiment")
@Tag(name = "Experiment", description = "Experiments allow users to apply change detection rules to two different datasets. This allows for pass/fail of KPIS based on A/B testing")
@Extension(name = "x-smallrye-profile-external", value = "")
public interface ExperimentService {
    @GET
    @Path("{testId}/profiles")
    @Operation(description = "Retrieve Experiment Profiles by Test ID")
    @Parameters(value = {
            @Parameter(name = "testId", description = "Test ID to retrieve Experiment Profiles for", example = "101"),
    })
    Collection<ExperimentProfile> profiles(@PathParam("testId") int testId);

    @POST
    @Path("{testId}/profiles")
    @Operation(description = "Save new or update existing Experiment Profiles for a Test ")
    @Parameters(value = {
            @Parameter(name = "testId", description = "Test ID to retrieve Experiment Profiles for", example = "101"),
    })
    int addOrUpdateProfile(@PathParam("testId") int testId,
            @RequestBody(required = true) ExperimentProfile profile);

    @DELETE
    @Path("{testId}/profiles/{profileId}")
    @Operation(description = "Delete an Experiment Profiles for a Test")
    @Parameters(value = {
            @Parameter(name = "testId", description = "Test ID", example = "101"),
            @Parameter(name = "profileId", description = "Experiment Profile ID", example = "202"),
    })
    void deleteProfile(@PathParam("testId") int testId,
            @PathParam("profileId") int profileId);

    @GET
    @Path("models")
    @Operation(description = "Retrieve a list of Condition Config models")
    List<ConditionConfig> models();

    @GET
    @Path("run")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Run an experiment for a given dataset and experiment profile")
    @Parameters(value = {
            @Parameter(name = "datasetId", description = "The dataset to run the experiment on", example = "101"),
    })
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Array of experiment results", content = {
                    @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = ExperimentResult.class))
            })
    })
    List<ExperimentResult> runExperiments(@QueryParam("datasetId") int datasetId);

    @Schema(description = "Result of running an Experiment", type = SchemaType.STRING)
    enum BetterOrWorse {
        BETTER("BETTER"),
        SAME("SAME"),
        WORSE("WORSE");

        private static final BetterOrWorse[] VALUES = BetterOrWorse.values();

        private final String name;

        BetterOrWorse(String s) {
            this.name = s;
        }

        public static BetterOrWorse fromString(String s) {
            for (BetterOrWorse v : VALUES) {
                if (v.name.equals(s)) {
                    return v;
                }
            }
            return null;
        }

        public static BetterOrWorse fromInt(int b) {
            return VALUES[b];
        }
    }

    @Schema(description = "Result of running an Experiment", type = SchemaType.OBJECT)
    class ExperimentResult {

        @Schema(description = "Experiment profile that results relates to")
        public ExperimentProfile profile;
        @Schema(description = "A list of log statements recorded while Experiment was evaluated")
        public List<DatasetLog> logs;
        @Schema(description = "Dataset Info about dataset used for experiment")
        public Dataset.Info datasetInfo;
        @Schema(description = "A list of Dataset Info for experiment baseline(s)")
        public List<Dataset.Info> baseline;

        @Schema(description = "A Map of all comparisons and results evaluated during an Experiment")
        public Map<String, ComparisonResult> results;

        @Schema(implementation = String.class)
        public JsonNode extraLabels;
        public boolean notify;

        public ExperimentResult() {
        }

        public ExperimentResult(ExperimentProfile profile, List<DatasetLog> logs,
                Dataset.Info datasetInfo, List<Dataset.Info> baseline,
                Map<String, ComparisonResult> results,
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

    @Schema(description = "Result of performing a Comparison", type = SchemaType.OBJECT)
    class ComparisonResult {
        @Schema(description = "Was the Experiment dataset better or worse than the baseline dataset", type = SchemaType.STRING, implementation = BetterOrWorse.class)
        public BetterOrWorse overall;
        @Schema(description = "Experiment value")
        public double experimentValue;
        @Schema(description = "Baseline value")
        public double baselineValue;
        @Schema(description = "The relative difference between the Experiment and Baseline Datasets")
        public String result;

        public ComparisonResult() {
        }

        public ComparisonResult(BetterOrWorse overall, double experimentValue, double baselineValue, String result) {
            this.overall = overall;
            this.experimentValue = experimentValue;
            this.baselineValue = baselineValue;
            this.result = result;
        }
    }
}
