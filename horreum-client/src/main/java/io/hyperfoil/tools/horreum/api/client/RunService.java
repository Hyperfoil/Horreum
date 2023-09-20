package io.hyperfoil.tools.horreum.api.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.Run;

import io.hyperfoil.tools.horreum.api.services.RunService.RunsSummary;
import io.hyperfoil.tools.horreum.api.services.RunService.RunSummary;
import io.hyperfoil.tools.horreum.api.services.RunService.RunCount;

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
import java.util.List;
import java.util.Map;

import static io.hyperfoil.tools.horreum.api.services.RunService.RunExtended;

/**
 * THIS IS A DUPLICATE CLASS specifically for the client.
 * This class is missing the `addRunFromData` that leaks resteasy reactive types in the API
 * A Custom implementation for the `addRunFromData` method is included in RunServiceExtension
 */

@Path("/api/run")
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public interface RunService {
   @GET
   @Path("{id}")
   RunExtended getRun(@PathParam("id") int id,
                                                                         @QueryParam("token") String token);

   @GET
   @Path("{id}/summary")
   RunSummary getRunSummary(@PathParam("id") int id, @QueryParam("token") String token);

   @GET
   @Path("{id}/data")
   Object getData(@PathParam("id") int id, @QueryParam("token") String token, @QueryParam("schemaUri") String schemaUri);

   @GET
   @Path("{id}/metadata")
   Object getMetadata(@PathParam("id") int id, @QueryParam("token") String token, @QueryParam("schemaUri") String schemaUri);

//   @GET
//   @Path("{id}/query")
//   QueryResult queryData(@PathParam("id") int id,
//                         @QueryParam("query") String jsonpath,
//                         @QueryParam("uri") String schemaUri,
//                         @QueryParam("array") @DefaultValue("false") boolean array);

   @POST
   @Path("{id}/resetToken")
   String resetToken(@PathParam("id") int id);

   @POST
   @Path("{id}/dropToken")
   String dropToken(@PathParam("id") int id);

   @POST
   @Path("{id}/updateAccess")
   // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
   void updateAccess(@PathParam("id") int id,
                     @QueryParam("owner") String owner,
                     @QueryParam("access") Access access);

   @POST
   @Path("test/{test}")
   @Consumes(MediaType.APPLICATION_JSON)
   Response add(@PathParam("test") String testNameOrId,
              @QueryParam("owner") String owner,
              @QueryParam("access") Access access,
              @QueryParam("token") String token,
              Run run);

   @POST
   @Path("data")
   @Produces(MediaType.TEXT_PLAIN) // run ID as string
   Response addRunFromData(@QueryParam("start") String start,
                         @QueryParam("stop") String stop,
                         @QueryParam("test") String test,
                         @QueryParam("owner") String owner,
                         @QueryParam("access") Access access,
                         @QueryParam("token") String token,
                         @QueryParam("schema") String schemaUri,
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
                           @QueryParam("direction") SortDirection direction);

   @GET
   @Path("{id}/waitforDatasets")
   void waitForDatasets(@PathParam("id") int runId);

   @GET
   @Path("count")
   RunCount runCount(@QueryParam("testId") int testId);

   @GET
   @Path("list/{testId}")
   RunsSummary listTestRuns(@PathParam("testId") int testId,
                                                                               @QueryParam("trashed") boolean trashed,
                                                                               @QueryParam("limit") Integer limit,
                                                                               @QueryParam("page") Integer page,
                                                                               @QueryParam("sort") String sort,
                                                                               @QueryParam("direction") SortDirection direction);

   @GET
   @Path("bySchema")
   RunsSummary listBySchema(@QueryParam("uri") String uri,
                            @QueryParam("limit") Integer limit,
                            @QueryParam("page") Integer page,
                            @QueryParam("sort") String sort,
                            @QueryParam("direction") String direction);

   @POST
   @Path("{id}/trash")
   void trash(@PathParam("id") int id, @QueryParam("isTrashed") Boolean isTrashed);

   @POST
   @Path("{id}/description")
   @Consumes(MediaType.TEXT_PLAIN)
   void updateDescription(@PathParam("id") int id, String description);

   @POST
   @Path("{id}/schema")
   @Consumes(MediaType.TEXT_PLAIN)
   Map<Integer, String> updateSchema(@PathParam("id") int id, @QueryParam("path") String path, String schemaUri);

   @POST
   @Path("{id}/recalculate")
   List<Integer> recalculateDatasets(@PathParam("id") int runId);

   @POST
   @Path("recalculateAll")
   void recalculateAll(@QueryParam("from") String from, @QueryParam("to") String to);

}
