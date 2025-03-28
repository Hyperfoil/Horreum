package io.hyperfoil.tools.horreum.api.services;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.Separator;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.tools.horreum.api.ApiIgnore;
import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.ExportedLabelValues;
import io.hyperfoil.tools.horreum.api.data.ProtectedTimeType;
import io.hyperfoil.tools.horreum.api.data.Run;
import io.hyperfoil.tools.horreum.api.data.ValidationError;

@Path("/api/run")
@Consumes({ MediaType.APPLICATION_JSON })
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Run", description = "Manage test runs. Runs are instances of results of a benchmark execution")
@Extension(name = "x-smallrye-profile-external", value = "")
public interface RunService {
    @GET
    @Path("{id}")
    @APIResponse(responseCode = "404", description = "If no Run have been found with the given id", content = @Content(mediaType = MediaType.APPLICATION_JSON))
    @APIResponseSchema(value = RunExtended.class, responseDescription = "Run data with the referenced schemas and generated datasets", responseCode = "200")
    @Operation(description = "Get extended Run information by Run ID")
    @Parameters(value = {
            @Parameter(name = "id", in = ParameterIn.PATH, description = "Run ID", example = "202"),

    })
    RunExtended getRun(@PathParam("id") int id);

    @GET
    @Path("{id}/summary")
    @APIResponse(responseCode = "404", description = "If no Run have been found with the given id", content = @Content(mediaType = MediaType.APPLICATION_JSON))
    @APIResponseSchema(value = RunSummary.class, responseDescription = "Run summary with the referenced schemas and generated datasets", responseCode = "200")
    @Operation(description = "Get Run Summary information by Run ID")
    @Parameters(value = {
            @Parameter(name = "id", in = ParameterIn.PATH, description = "Run ID", example = "202")
    })
    RunSummary getRunSummary(@PathParam("id") int id);

    @GET
    @Path("{id}/data")
    @Operation(description = "Get Run data by Run ID")
    @Parameters(value = {
            @Parameter(name = "id", in = ParameterIn.PATH, description = "Run ID", example = "202"),
            @Parameter(name = "schemaUri", in = ParameterIn.QUERY, description = "FIlter by Schmea URI", example = "uri:my-benchmark:0.1")

    })
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Run payload", content = {
                    @Content(schema = @Schema(type = SchemaType.OBJECT), example = "{ \"buildID\": 1709, ...}")
            })
    })
    Object getData(@PathParam("id") int id,
            @QueryParam("schemaUri") String schemaUri);

    @GET
    @Path("{id}/labelValues")
    @Operation(description = "Get all the label values for the run")
    @Parameters(value = {
            @Parameter(name = "id", in = ParameterIn.PATH, description = "Run Id", example = "101"),
            @Parameter(name = "filter", description = "either a required json sub-document or path expression", examples = {
                    @ExampleObject(name = "object", value = "{labelName:necessaryValue,...}", description = "json object that must exist in the values object"),
                    @ExampleObject(name = "string", value = "$.count ? (@ < 20 && @ > 10)", description = "valid filtering jsonpath that returns null if not found (not predicates)")
            }),
            @Parameter(name = "sort", description = "label name for sorting"),
            @Parameter(name = "direction", description = "either Ascending or Descending", example = "count"),
            @Parameter(name = "limit", description = "the maximum number of results to include", example = "10"),
            @Parameter(name = "page", description = "which page to skip to when using a limit", example = "2"),
            @Parameter(name = "include", description = "label name(s) to include in the result as scalar or comma separated", examples = {
                    @ExampleObject(name = "single", value = "id", description = "including a single label"),
                    @ExampleObject(name = "multiple", value = "id,count", description = "including multiple labels")
            }),
            @Parameter(name = "exclude", description = "label name(s) to exclude from the result as scalar or comma separated", examples = {
                    @ExampleObject(name = "single", value = "id", description = "excluding a single label"),
                    @ExampleObject(name = "multiple", value = "id,count", description = "excluding multiple labels")
            }),
            @Parameter(name = "multiFilter", description = "enable filtering for multiple values with an array of values", example = "true")
    })
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "label Values", content = {
                    @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = ExportedLabelValues.class), example = "[ { \"datasetId\" : 101, \"runId\": 201, \"values\" : { [labelName] : labelValue } },...]")
            })
    })

    List<ExportedLabelValues> labelValues(
            @PathParam("id") int runId,
            @QueryParam("filter") @DefaultValue("{}") String filter,
            @QueryParam("sort") @DefaultValue("") String sort,
            @QueryParam("direction") @DefaultValue("Ascending") String direction,
            @QueryParam("limit") @DefaultValue("" + Integer.MAX_VALUE) int limit,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("include") @Separator(",") List<String> include,
            @QueryParam("exclude") @Separator(",") List<String> exclude,
            @QueryParam("multiFilter") @DefaultValue("false") boolean multiFilter);

    @GET
    @Path("{id}/metadata")
    @Operation(description = "Get Run  meta data by Run ID")
    @Parameters(value = {
            @Parameter(name = "id", in = ParameterIn.PATH, description = "Run ID", example = "202"),
            @Parameter(name = "schemaUri", in = ParameterIn.QUERY, description = "Filter by Schmea URI", example = "uri:my-benchmark:0.1")

    })
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Run payload", content = {
                    @Content(schema = @Schema(type = SchemaType.OBJECT), example = "{ \"metaDataID\": 1709, ...}")
            })
    })
    Object getMetadata(@PathParam("id") int id,
            @QueryParam("schemaUri") String schemaUri);

    @POST
    @Path("{id}/updateAccess")
    // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
    @Operation(description = "Update the Access configuration for a Run")
    @Parameters(value = {
            @Parameter(name = "id", required = true, description = "Run ID to update Access", example = "101"),
            @Parameter(name = "owner", required = true, description = "Name of the new owner", example = "perf-team"),
            @Parameter(name = "access", required = true, description = "New Access level", example = "0")
    })
    void updateAccess(@PathParam("id") int id,
            @QueryParam("owner") String owner,
            @QueryParam("access") Access access);

    @POST
    @Path("test")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Upload a new Run")
    @Parameters(value = {
            @Parameter(name = "test", description = "test name of ID", example = "my-benchmark"),
            @Parameter(name = "owner", description = "Name of the new owner", example = "perf-team"),
            @Parameter(name = "access", description = "New Access level", example = "0"),
    })
    @RequestBody(name = "runBody", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Run.class)), required = true)
    @ResponseStatus(202) // ACCEPTED
    @APIResponses(value = {
            @APIResponse(responseCode = "202", description = "The request has been accepted for processing. Returns a list of created run IDs if available, "
                    + "or an empty list if processing is still ongoing. Label values and change detection processing " +
                    "is performed asynchronously.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = Integer.class, example = "[101, 102, 103]"), example = "[101, 102, 103]")),
            @APIResponse(responseCode = "400", description = "Some fields are missing or invalid", content = @Content(mediaType = MediaType.APPLICATION_JSON))
    })
    List<Integer> add(@QueryParam("test") String testNameOrId,
            @QueryParam("owner") String owner,
            @QueryParam("access") Access access,
            Run run);

    @POST
    @Path("data")
    @RequestBody(content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.STRING, implementation = String.class), example = "[\n"
            +
            "  {\n" +
            "    \"tag\": \"main\",\n" +
            "    \"score\": 2031.7424089224041,\n" +
            "    \"params\": {\n" +
            "      \"size\": \"1000\",\n" +
            "      \"useTreeSet\": \"true\"\n" +
            "    },\n" +
            "    \"$schema\": \"urn:jmh:0.2\",\n" +
            "    \"testName\": \"org.drools.benchmarks.datastructures.QueueBenchmark.benchmark\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"$schema\": \"urn:horreum:jenkins-plugin:0.1\",\n" +
            "    \"jobName\": \"upstream-perf-bre-datastructures\",\n" +
            "    \"buildUrl\": \"https://qe.com/job/TESTING/job/upstream-perfx-datastructures/125/\",\n" +
            "    \"startTime\": 1698020160763,\n" +
            "    \"uploadTime\": 1698020592674,\n" +
            "    \"buildNumber\": 125,\n" +
            "    \"jobFullName\": \"TESTING/RHBA/_upstream/decisions/8.x/performance/nightly/upstream-perf-bre-datastructures\",\n"
            +
            "    \"scheduleTime\": 1698020160756,\n" +
            "    \"jobDisplayName\": \"upstream-perf-bre-datastructures\",\n" +
            "    \"buildDisplayName\": \"#125\"\n" +
            "  }\n" +
            "]"))
    @Operation(description = "Upload a new Run")
    @Parameters(value = {
            @Parameter(name = "start", required = true, description = "start timestamp of run, or json path expression", examples = {
                    @ExampleObject(name = "scalar value", value = "2023-10-23T00:13:35Z"),
                    @ExampleObject(name = "json path", value = "$.buildTimeStamp"),
            }),
            @Parameter(name = "stop", required = true, description = "stop timestamp of run, or json path expression", examples = {
                    @ExampleObject(name = "scalar value", value = "2023-10-23T00:13:35Z"),
                    @ExampleObject(name = "json path", value = "$.buildTimeStamp"),
            }),
            @Parameter(name = "test", required = true, description = "test name of ID", example = "my-benchmark"),
            @Parameter(name = "owner", description = "Name of the new owner", example = "perf-team"),
            @Parameter(name = "access", description = "New Access level", example = "0"),
            @Parameter(name = "schema", in = ParameterIn.QUERY, description = "Schema URI", example = "uri:my-benchmark:0.2"),
            @Parameter(name = "description", description = "Run description", example = "AWS runs"),

    })
    @Produces(MediaType.TEXT_PLAIN)
    @APIResponses(value = {
            @APIResponse(responseCode = "202", description = "The request has been accepted for processing. Returns a list of created run IDs if available, "
                    + "or an empty list if processing is still ongoing. Label values and change detection processing " +
                    "is performed asynchronously.", content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = SchemaType.STRING, example = "101,102,103"), example = "101,102,103")),
            @APIResponse(responseCode = "204", description = "Data is valid but no run was created", content = @Content(mediaType = MediaType.TEXT_PLAIN)),
            @APIResponse(responseCode = "400", description = "Some fields are missing or invalid", content = @Content(mediaType = MediaType.TEXT_PLAIN))
    })
    Response addRunFromData(@QueryParam("start") String start,
            @QueryParam("stop") String stop,
            @QueryParam("test") String test,
            @QueryParam("owner") String owner,
            @QueryParam("access") Access access,
            @QueryParam("schema") String schemaUri,
            @QueryParam("description") String description,
            @RequestBody(required = true) String data);

    @POST
    @Path("data")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(description = "Upload a new Run with metadata", hidden = true)
    @Produces(MediaType.TEXT_PLAIN)
    @APIResponses(value = {
            @APIResponse(responseCode = "202", description = "The request has been accepted for processing. Returns a list of created run IDs if available, "
                    + "or an empty list if processing is still ongoing. Label values and change detection processing " +
                    "is performed asynchronously.", content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = SchemaType.STRING, example = "101,102,103"), example = "101,102,103")),
            @APIResponse(responseCode = "204", description = "Data is valid but no run was created", content = @Content(mediaType = MediaType.TEXT_PLAIN)),
            @APIResponse(responseCode = "400", description = "Some fields are missing or invalid", content = @Content(mediaType = MediaType.TEXT_PLAIN))
    })
    Response addRunFromData(@Parameter(required = true) @QueryParam("start") String start,
            @Parameter(required = true) @QueryParam("stop") String stop,
            @Parameter(required = true) @QueryParam("test") String test,
            @QueryParam("owner") String owner,
            @QueryParam("access") Access access,
            @QueryParam("schema") String schemaUri,
            @QueryParam("description") String description,
            @RestForm("data") FileUpload data,
            @RestForm("metadata") FileUpload metadata);

    @GET
    @Path("autocomplete")
    @ApiIgnore
    List<String> autocomplete(
            @Parameter(required = true, name = "query", description = "JSONPath to be autocompleted", example = "$.") @QueryParam("query") String query);

    @GET
    @Path("list")
    @Operation(description = "Retrieve a paginated list of Runs with available count")
    @Parameters(value = {
            @Parameter(name = "query", description = "query string to filter runs", example = "$.*"),
            @Parameter(name = "matchAll", description = "match all Runs?", example = "false"),
            @Parameter(name = "roles", description = "__my, __all or a comma delimited  list of roles", example = "__my"),
            @Parameter(name = "trashed", description = "show trashed runs", example = "false"),
            @Parameter(name = "limit", description = "limit the number of results", example = "20"),
            @Parameter(name = "page", description = "filter by page number of a paginated list of Tests", example = "2"),
            @Parameter(name = "sort", description = "Field name to sort results", example = "name"),
            @Parameter(name = "direction", description = "Sort direction", example = "Ascending")
    })
    RunsSummary listAllRuns(@QueryParam("query") String query,
            @QueryParam("matchAll") boolean matchAll,
            @QueryParam("roles") String roles,
            @QueryParam("trashed") boolean trashed,
            @QueryParam("limit") Integer limit,
            @QueryParam("page") Integer page,
            @QueryParam("sort") String sort,
            @QueryParam("direction") SortDirection direction);

    @GET
    @Path("count")
    @Operation(description = "Run count summary for given Test ID")
    @Parameters(value = {
            @Parameter(name = "testId", required = true, description = "Test ID", example = "101"),
    })
    RunCount runCount(@QueryParam("testId") int testId);

    @GET
    @Path("list/{testId}")
    @Operation(description = "Retrieve a paginated list of Runs with available count for a given Test ID")
    @Parameters(value = {
            @Parameter(name = "testId", description = "Test ID", example = "101"),
            @Parameter(name = "trashed", description = "include trashed runs", example = "false"),
            @Parameter(name = "limit", description = "limit the number of results", example = "20"),
            @Parameter(name = "page", description = "filter by page number of a paginated list of Tests", example = "2"),
            @Parameter(name = "sort", description = "Field name to sort results", example = "name"),
            @Parameter(name = "direction", description = "Sort direction", example = "Ascending")
    })
    RunsSummary listTestRuns(@PathParam("testId") int testId,
            @QueryParam("trashed") boolean trashed,
            @QueryParam("limit") Integer limit,
            @QueryParam("page") Integer page,
            @QueryParam("sort") String sort,
            @QueryParam("direction") SortDirection direction);

    @GET
    @Path("bySchema")
    @Operation(description = "Retrieve a paginated list of Runs with available count for a given Schema URI")
    @Parameters(value = {
            @Parameter(name = "uri", required = true, description = "Schema URI", example = "uri:my-schema:0.1"),
            @Parameter(name = "limit", description = "limit the number of results", example = "20"),
            @Parameter(name = "page", description = "filter by page number of a paginated list of Tests", example = "2"),
            @Parameter(name = "sort", description = "Field name to sort results", example = "name"),
            @Parameter(name = "direction", description = "Sort direction", example = "Ascending")
    })
    RunsSummary listBySchema(@QueryParam("uri") String uri,
            @QueryParam("limit") Integer limit,
            @QueryParam("page") Integer page,
            @QueryParam("sort") String sort,
            @QueryParam("direction") SortDirection direction);

    @POST
    @Path("{id}/trash")
    @Operation(description = "Trash a Run with a given ID")
    @Parameters(value = {
            @Parameter(name = "id", description = "Run ID", example = "101"),
            @Parameter(name = "isTrashed", description = "should run be trashed?", example = "true"),
    })
    void trash(@PathParam("id") int id, @QueryParam("isTrashed") Boolean isTrashed);

    @POST
    @Path("{id}/description")
    @Consumes(MediaType.TEXT_PLAIN)
    @Operation(description = "Update Run description")
    @Parameters(value = {
            @Parameter(name = "id", description = "Run ID", example = "101"),
    })
    void updateDescription(@PathParam("id") int id, @RequestBody(required = true) String description);

    @POST
    @Path("{id}/schema")
    @Consumes(MediaType.TEXT_PLAIN)
    @Operation(description = "Update Run schema for part of JSON data")
    @Parameters(value = {
            @Parameter(name = "id", description = "Run ID", example = "101"),
            @Parameter(name = "path", description = "JSON path expression to update schema", example = "$.schemaURI"),
    })
    //   TODO:: I can not find a way of defining a Map response type correctly via smallrye annotations
    //   @APIResponses(
    //           value = {
    //                   @APIResponse( responseCode = "200",
    //                           description = "Map of schema by ID",
    //                           content = {
    //                                   @Content ( example = "")
    //                           }
    //                   )
    //           }
    //   )
    Map<Integer, String> updateSchema(@PathParam("id") int id,
            @QueryParam("path") String path,
            @RequestBody(required = true) String schemaUri);

    @POST
    @Path("{id}/recalculate")
    @Operation(description = "Recalculate Datasets for Run")
    @Parameters(value = {
            @Parameter(name = "id", description = "Run ID", example = "101"),
    })
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Array of generated Datasets", content = {
                    @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = Integer.class), example = "[101, 102, 103]")
            })
    })
    List<Integer> recalculateDatasets(@PathParam("id") int runId);

    @POST
    @Path("recalculateAll")
    @Operation(description = "Recalculate Datasets for Runs between two dates")
    @Parameters(value = {
            @Parameter(name = "from", description = "start timestamp", example = "1698013206000"),
            @Parameter(name = "to", description = "end timestamp", example = "1698013206000"),
    })
    void recalculateAll(@QueryParam("from") String from, @QueryParam("to") String to);

    @Schema(type = SchemaType.OBJECT, allOf = ProtectedTimeType.class)
    class RunSummary extends ProtectedTimeType {
        @JsonProperty(required = true)
        @Schema(required = true, description = "Run unique ID", example = "202")
        public int id;
        @JsonProperty(required = true)
        @Schema(description = "test ID run relates to", example = "101")
        public int testid;

        @NotNull
        @Schema(description = "test ID run relates to", example = "My benchmark")
        public String testname;
        @JsonProperty(required = true)
        @Schema(description = "has Run been trashed in the UI", example = "false")
        public boolean trashed;
        @JsonProperty(required = true)
        @Schema(description = "does Run have metadata uploaded alongside Run data", example = "false")
        public boolean hasMetadata;
        @Schema(description = "Run description", example = "Run on AWS with m7g.large")
        public String description;
        @Schema(description = "List of all Schema Usages for Run")
        public List<SchemaService.SchemaUsage> schemas;
        @Schema(required = true, description = "Array of datasets ids", example = "[101, 102, 103]")
        public Integer[] datasets;
        @Schema(description = "Array of validation errors")
        public ValidationError[] validationErrors;
    }

    @JsonIgnoreProperties({ "old_start" }) //ignore properties that have not been mapped
    @Schema(type = SchemaType.OBJECT)
    class RunExtended extends Run {
        @NotNull
        @Schema(required = true, description = "List of Schema Usages")
        public List<SchemaService.SchemaUsage> schemas;
        @NotNull
        @Schema(required = true, description = "Test name run references", example = "My benchmark")
        public String testname;
        @Schema(required = true, description = "List of DatasetIDs", example = "[ 101, 102, 104, 106 ]")
        public Integer[] datasets;
    }

    class RunsSummary {
        @JsonProperty(required = true)
        @Schema(description = "Total count of Runs visible", example = "1")
        public long total;
        @NotNull
        @Schema(description = "List of Run Summaries")
        public List<RunSummary> runs;
    }

    class RunCount {
        @JsonProperty(required = true)
        @Schema(description = "Total count of Runs visible", example = "100")
        public long total;
        @Schema(description = "Total count of active Runs visible", example = "95")
        @JsonProperty(required = true)
        public long active;
        @Schema(description = "Total count of trashed Runs", example = "5")
        @JsonProperty(required = true)
        public long trashed;
    }
}
