package io.hyperfoil.tools.horreum.api.services;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.Dataset;
import io.hyperfoil.tools.horreum.api.data.Label;
import io.hyperfoil.tools.horreum.api.data.ValidationError;

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

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/dataset")
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Dataset", description = "Datasets are used as the basis for all change detection and reporting")
@Extension(name = "x-smallrye-profile-external", value = "")
public interface DatasetService {
   @Path("{id}")
   @GET
   @Produces(MediaType.APPLICATION_JSON)
   @Operation(description="Retrieve Dataset by ID")
   @Parameters(value = {
           @Parameter(name = "id", description = "Dataset ID to retrieve", example = "101"),
   })
   @APIResponse(
           responseCode = "404",
           description = "No Dataset with the given id was found",
           content = @Content(mediaType = "application/json"))
   @APIResponseSchema(value = Dataset.class,
           responseDescription = "JVM system properties of a particular host.",
           responseCode = "200")
   Dataset getDataset(@PathParam("id") int datasetId);

   @Path("list/{testId}")
   @GET
   @Operation(description="Retrieve a paginated list of Datasets, with total count, by Test")
   @Parameters(value = {
           @Parameter(name = "testId", description = "Test ID of test to retrieve list of Datasets", example = "101"),
           @Parameter(name = "filter", description = "JOSN Filter expression to apply to query", example = "{\"buildID\":111111}"),
           @Parameter(name = "limit", description = "limit the number of results", example = "20"),
           @Parameter(name = "page", description = "filter by page number of a paginated list of Schemas", example = "2"),
           @Parameter(name = "sort", description = "Field name to sort results", example = "name"),
           @Parameter(name = "direction", description = "Sort direction", example ="Ascending"),
           @Parameter(name = "viewId", description = "Optional View ID to filter datasets by view", example ="202"),
   })
   DatasetList listByTest(@PathParam("testId") int testId,
                          @QueryParam("filter") String filter,
                          @QueryParam("limit") Integer limit,
                          @QueryParam("page") Integer page,
                          @QueryParam("sort") String sort,
                          @QueryParam("direction") SortDirection direction,
                          @QueryParam("viewId") Integer viewId);


   @GET
   @Path("bySchema")
   @Operation(description="Retrieve a paginated list of Datasets, with total count, by Schema")
   @Parameters(value = {
           @Parameter(name = "uri", required = true, description = "Schema URI", example = "uri:techempower:0.1"),
           @Parameter(name = "limit", description = "limit the number of results", example = "20"),
           @Parameter(name = "page", description = "filter by page number of a paginated list of Schemas", example = "2"),
           @Parameter(name = "sort", description = "Field name to sort results", example = "name"),
           @Parameter(name = "direction", description = "Sort direction", example ="Ascending"),
   })
   DatasetList listBySchema(@QueryParam("uri") String uri,
                            @QueryParam("limit") Integer limit,
                            @QueryParam("page") Integer page,
                            @QueryParam("sort") @DefaultValue("start") String sort,
                            @QueryParam("direction") SortDirection direction);

   @GET
   @Path("{datasetId}/labelValues")
   List<LabelValue> labelValues(@PathParam("datasetId") int datasetId);

   @POST
   @Path("{datasetId}/previewLabel")
   LabelPreview previewLabel(@PathParam("datasetId") int datasetId, @RequestBody(required = true) Label label);

   @GET
   @Path("{datasetId}/summary")
   DatasetSummary getSummary(@PathParam("datasetId") int datasetId, @QueryParam("viewId") int viewId);

   class DatasetSummary {
      @JsonProperty(required = true)
      @Schema(description = "Unique Dataset ID", example = "101")
      public int id;
      @JsonProperty(required = true)
      @Schema(description = "Run ID that Dataset relates to", example = "202")
      public int runId;
      @JsonProperty(required = true)
      @Schema(description = "Ordinal position of Dataset Summary on returned List", example = "3")
      public int ordinal;
      @JsonProperty(required = true)
      @Schema(description = "Test ID that Dataset relates to", example = "202")
      public int testId;
      @NotNull
      @Schema(description="Test name that the Dataset relates to",
              example="my-comprehensive-benchmark")
      public String testname;
      @Schema(description="Dataset description",
              example = "Run on AWS with m7g.large")
      public String description;
      @JsonProperty(required = true)
      @Schema(type = SchemaType.INTEGER, implementation = Long.class,
              description = "Dataset Start timestamp", example = "1698013206000")
      public long start;
      @JsonProperty(required = true)
      @Schema(type = SchemaType.INTEGER, implementation = Long.class,
              description = "Dataset Stop timestamp", example = "1698013206000")
      public long stop;
      @Schema(required = true, description="Name of the team that owns the test. Users must belong to the team that owns a test to make modifications",
              example="performance-team")
      public String owner;
      @NotNull
      @JsonProperty(required = true)
//      TODO:: https://github.com/Hyperfoil/Horreum/issues/801
//      @Schema( type = SchemaType.INTEGER, implementation = Access.class,
//              description = "Access rights for the test. This defines the visibility of the Test in the UI",
//              example = "0")
      @Schema(required = true, implementation = Access.class)
      public int access;
      @Schema(implementation = String.class, description = "View definition")
      public ObjectNode view;
      @JsonProperty(required = true)
      @Schema(description = "List of Schema usages")
      public List<SchemaService.SchemaUsage> schemas;
//TODO:: https://github.com/Hyperfoil/Horreum/issues/801
//      @Schema(type = SchemaType.ARRAY, implementation = ValidationError.class, description = "Array of validation Errors")
      @Schema(type = SchemaType.ARRAY, implementation = ValidationError.class)
      public ArrayNode validationErrors;
   }

   @Schema(description = "Result containing a subset of Dataset Summaries and the total count of available. Used in paginated tables")
   class DatasetList {
      @JsonProperty(required = true)
      @Schema(description = "Total number of Dataset Summaries available", example = "64")
      public long total;
      @NotNull
      @Schema(description = "List of Dataset Summaries. This is often a subset of total available.")
      public List<DatasetSummary> datasets;
   }

   @Schema(description = "Label Value derived from Label definition and Dataset Data")
   class LabelValue {
      @JsonProperty(required = true)
      @Schema(description = "Unique ID for Label Value", example = "101")
      public int id;
      @NotNull
      @Schema(description = "Label name", example = "buildID")
      public String name;
      @NotNull
      @Schema(description = "Summary description of Schema")
      public SchemaService.SchemaDescriptor schema;
      @Schema(implementation = String.class, description = "Value value extracted from Dataset. This can be a scalar, array or JSON object", example = "1724")
      public JsonNode value;
   }

   @Schema(description = "Preview a Label Value derived from a Dataset Data. A preview allows users to apply a Label to a dataset and preview the Label Value result and processing errors in the UI")
   class LabelPreview {
      @Schema(implementation = String.class, description = "Value value extracted from Dataset. This can be a scalar, array or JSON object")
      public JsonNode value;
      @Schema(description = "Description of errors occurred attempting to generate Label Value Preview")
      public String output;
   }
}
