package io.hyperfoil.tools.horreum.api;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.entity.json.Label;

@Path("/api/dataset")
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public interface DatasetService {
   @Path("{id}")
   @GET
   DataSet getDataSet(@PathParam("id") Integer datasetId);

   @Path("list/{testId}")
   @GET
   DatasetList listTestDatasets(@PathParam("testId") int testId,
                                @QueryParam("limit") Integer limit,
                                @QueryParam("page") Integer page,
                                @QueryParam("sort") String sort,
                                @QueryParam("direction") String direction);

   @Path("{id}/query")
   @GET
   QueryResult queryDataSet(@PathParam("id") Integer datasetId,
                            @QueryParam("query") String jsonpath,
                            @QueryParam("array") @DefaultValue("false") boolean array, @QueryParam("schemaUri") String schemaUri);

   @GET
   @Path("bySchema")
   DatasetList listDatasetsBySchema(@QueryParam("uri") String uri,
                                    @QueryParam("limit") Integer limit,
                                    @QueryParam("page") Integer page,
                                    @QueryParam("sort") @DefaultValue("start") String sort,
                                    @QueryParam("direction") @DefaultValue("Descending") String direction);

   @GET
   @Path("{datasetId}/labelValues")
   List<LabelValue> labelValues(@PathParam("datasetId") int datasetId);

   @POST
   @Path("{datasetId}/previewLabel")
   LabelPreview previewLabel(@PathParam("datasetId") int datasetId, Label label);

   class DatasetSummary {
      public int id;
      public int runId;
      public int ordinal;
      public int testId;
      public String testname;
      public String description;
      public long start;
      public long stop;
      public String owner;
      public int access;
      public ObjectNode view;
      public ArrayNode schemas; // list of URIs
   }

   class DatasetList {
      public long total;
      public List<DatasetSummary> datasets;
   }

   class LabelValue {
      public int id;
      public String name;
      public SchemaService.SchemaDescriptor schema;
      public JsonNode value;
   }

   class LabelPreview {
      public JsonNode value;
      public String output;
   }
}
