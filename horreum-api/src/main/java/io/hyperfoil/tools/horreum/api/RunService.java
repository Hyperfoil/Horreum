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

import io.hyperfoil.tools.horreum.entity.json.Access;
import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.entity.json.Run;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

@Path("/api/run")
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public interface RunService {
   @GET
   @Path("{id}")
   Object getRun(@PathParam("id") Integer id,
                 @QueryParam("token") String token);

   @GET
   @Path("{id}/summary")
   RunSummary getRunSummary(@PathParam("id") Integer id, @QueryParam("token") String token);

   @GET
   @Path("{id}/data")
   Object getData(@PathParam("id") Integer id, @QueryParam("token") String token);

   @GET
   @Path("{id}/query")
   QueryResult queryData(@PathParam("id") Integer id,
                         @QueryParam("query") String jsonpath,
                         @QueryParam("uri") String schemaUri,
                         @QueryParam("array") @DefaultValue("false") boolean array);

   @POST
   @Path("{id}/resetToken")
   String resetToken(@PathParam("id") Integer id);

   @POST
   @Path("{id}/dropToken")
   String dropToken(@PathParam("id") Integer id);

   @POST
   @Path("{id}/updateAccess")
   // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
   void updateAccess(@PathParam("id") Integer id,
                     @QueryParam("owner") String owner,
                     @QueryParam("access") Access access);

   @POST
   @Path("test/{test}")
   @Consumes(MediaType.APPLICATION_JSON)
   String add(@PathParam("test") String testNameOrId,
              @QueryParam("owner") String owner,
              @QueryParam("access") Access access,
              @QueryParam("token") String token,
              Run run);

   @POST
   @Path("data")
   @Produces(MediaType.TEXT_PLAIN) // run ID as string
   String addRunFromData(@QueryParam("start") String start,
                         @QueryParam("stop") String stop,
                         @QueryParam("test") String test,
                         @QueryParam("owner") String owner,
                         @QueryParam("access") Access access,
                         @Parameter(description = "Horreum internal token. Incompatible with Keycloak") @QueryParam("token") String token, @QueryParam("schema") String schemaUri,
                         @QueryParam("description") String description,
                         JsonNode data);

   @GET
   @Path("autocomplete")
   List<String> autocomplete(@QueryParam("query") String query);

   @GET
   @Path("list")
   RunsSummary listAllRuns(@QueryParam("query") String query,
                           @QueryParam("matchAll") boolean matchAll,
                           @QueryParam("roles") String roles,
                           @QueryParam("trashed") boolean trashed,
                           @QueryParam("limit") Integer limit,
                           @QueryParam("page") Integer page,
                           @QueryParam("sort") String sort,
                           @QueryParam("direction") String direction);

   @GET
   @Path("count")
   RunCounts runCount(@QueryParam("testId") Integer testId);

   @GET
   @Path("list/{testId}/")
   TestRunsSummary listTestRuns(@PathParam("testId") Integer testId,
                                @QueryParam("trashed") boolean trashed,
                                @QueryParam("tags") String tags,
                                @QueryParam("limit") Integer limit,
                                @QueryParam("page") Integer page,
                                @QueryParam("sort") String sort,
                                @QueryParam("direction") String direction);

   @GET
   @Path("bySchema")
   RunsSummary listBySchema(@QueryParam("uri") String uri,
                            @QueryParam("limit") Integer limit,
                            @QueryParam("page") Integer page,
                            @QueryParam("sort") String sort,
                            @QueryParam("direction") String direction);

   @POST
   @Path("{id}/trash")
   void trash(@PathParam("id") Integer id, @QueryParam("isTrashed") Boolean isTrashed);

   @POST
   @Path("{id}/description")
   @Consumes(MediaType.TEXT_PLAIN)
   void updateDescription(@PathParam("id") Integer id, String description);

   @POST
   @Path("{id}/schema")
   @Consumes(MediaType.TEXT_PLAIN)
   Object updateSchema(@PathParam("id") Integer id, @QueryParam("path") String path, String schemaUri);

   @Path("dataset/{id}")
   @GET
   DataSet getDataSet(@PathParam("id") Integer datasetId);

   @Path("dataset/list/{testId}")
   @GET
   DatasetList listTestDatasets(@PathParam("testId") int testId,
                                @QueryParam("limit") Integer limit,
                                @QueryParam("page") Integer page,
                                @QueryParam("sort") String sort,
                                @QueryParam("direction") String direction);

   @Path("dataset/{id}/query")
   @GET
   QueryResult queryDataSet(@PathParam("id") Integer datasetId,
                            @QueryParam("query") String jsonpath,
                            @QueryParam("array") @DefaultValue("false") boolean array, @QueryParam("schemaUri") String schemaUri);

   @GET
   @Path("dataset/bySchema")
   DatasetList listDatasetsBySchema(@QueryParam("uri") String uri,
                            @QueryParam("limit") Integer limit,
                            @QueryParam("page") Integer page,
                            @QueryParam("sort") String sort,
                            @QueryParam("direction") String direction);

   class RunSummary {
      public int id;
      public long start;
      public long stop;
      public int testid;
      public String owner;
      public int access;
      public String token;
      public String testname;
      public boolean trashed;
      public String description;
      public JsonNode tags;
      public JsonNode schema; // id -> uri mapping
      public ArrayNode datasets;
   }

   class RunsSummary {
      public long total;
      public List<RunSummary> runs;
   }

   class RunCounts {
      public long total;
      public long active;
      public long trashed;
   }

   class TestRunSummary extends RunSummary {
      public JsonNode schema;
      public JsonNode view;
   }

   class TestRunsSummary {
      public long total;
      public List<TestRunSummary> runs;
   }

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
      public ArrayNode schemas; // list of URIs
   }

   class DatasetList {
      public long total;
      public List<DatasetSummary> datasets = new ArrayList<>();
   }

   class QueryResult extends SqlService.JsonpathValidation {
      public String value;
   }
}
