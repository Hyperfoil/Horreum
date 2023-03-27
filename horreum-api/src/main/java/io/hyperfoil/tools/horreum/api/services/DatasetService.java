package io.hyperfoil.tools.horreum.api.services;

import java.util.List;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.api.data.ValidationError;
import io.hyperfoil.tools.horreum.api.data.DataSet;
import io.hyperfoil.tools.horreum.api.data.Label;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.data.Access;

@Path("/api/dataset")
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public interface DatasetService {
   @Path("{id}")
   @GET
   DataSet getDataSet(@PathParam("id") int datasetId);

   @Path("list/{testId}")
   @GET
   DatasetList listByTest(@PathParam("testId") int testId,
                          @QueryParam("filter") String filter,
                          @QueryParam("limit") Integer limit,
                          @QueryParam("page") Integer page,
                          @QueryParam("sort") String sort,
                          @QueryParam("direction") String direction,
                          @QueryParam("viewId") Integer viewId);

   @Path("{id}/query")
   @GET
   QueryResult queryData(@PathParam("id") int datasetId,
                         @Parameter(required = true) @QueryParam("query") String jsonpath,
                         @QueryParam("array") @DefaultValue("false") boolean array,
                         @QueryParam("schemaUri") String schemaUri);

   @GET
   @Path("bySchema")
   DatasetList listBySchema(@Parameter(required = true) @QueryParam("uri") String uri,
                            @QueryParam("limit") Integer limit,
                            @QueryParam("page") Integer page,
                            @QueryParam("sort") @DefaultValue("start") String sort,
                            @QueryParam("direction") @DefaultValue("Descending") String direction);

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
      public int id;
      @JsonProperty(required = true)
      public int runId;
      @JsonProperty(required = true)
      public int ordinal;
      @JsonProperty(required = true)
      public int testId;
      @NotNull
      public String testname;
      public String description;
      @JsonProperty(required = true)
      public long start;
      @JsonProperty(required = true)
      public long stop;
      @NotNull
      public String owner;
      @Schema(required = true, implementation = Access.class)
      public int access;
      public ObjectNode view;
      @JsonProperty(required = true)
      public List<SchemaService.SchemaUsage> schemas;
      @Schema(implementation = ValidationError[].class)
      public ArrayNode validationErrors;
   }

   class DatasetList {
      @JsonProperty(required = true)
      public long total;
      @NotNull
      public List<DatasetSummary> datasets;
   }

   class LabelValue {
      @JsonProperty(required = true)
      public int id;
      @NotNull
      public String name;
      @NotNull
      public SchemaService.SchemaDescriptor schema;
      public JsonNode value;
   }

   class LabelPreview {
      public JsonNode value;
      public String output;
   }
}
