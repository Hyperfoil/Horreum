package io.hyperfoil.tools.horreum.api.client;

import java.util.List;
import java.util.Map;

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

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.api.ApiIgnore;
import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.ExportedLabelValues;
import io.hyperfoil.tools.horreum.api.data.Run;

/**
 * THIS IS A DUPLICATE CLASS specifically for the client.
 * This class is missing the `addRunFromData` that leaks resteasy reactive types in the API (FileUpload)
 * A Custom implementation for the `addRunFromData` method is included in RunServiceExtension
 */

@Path("/api/run")
@Consumes({ MediaType.APPLICATION_JSON })
@Produces(MediaType.APPLICATION_JSON)
public interface RunService {
    // This is the main change wrt to the original RunService
    // NB: this changes the data type to JsonNode
    @POST
    @Path("data")
    @Produces(MediaType.TEXT_PLAIN)
    Response addRunFromData(@QueryParam("start") String start,
            @QueryParam("stop") String stop,
            @QueryParam("test") String test,
            @QueryParam("owner") String owner,
            @QueryParam("access") Access access,
            @QueryParam("schema") String schemaUri,
            @QueryParam("description") String description,
            JsonNode data);

    @GET
    @Path("{id}")
    io.hyperfoil.tools.horreum.api.services.RunService.RunExtended getRun(@PathParam("id") int id);

    @GET
    @Path("{id}/summary")
    io.hyperfoil.tools.horreum.api.services.RunService.RunSummary getRunSummary(@PathParam("id") int id);

    @GET
    @Path("{id}/data")
    Object getData(@PathParam("id") int id,
            @QueryParam("schemaUri") String schemaUri);

    @GET
    @Path("{id}/labelValues")
    List<ExportedLabelValues> getRunLabelValues(
            @PathParam("id") int runId,
            @QueryParam("filter") @DefaultValue("{}") String filter,
            @QueryParam("sort") @DefaultValue("") String sort,
            @QueryParam("direction") @DefaultValue("Ascending") String direction,
            @QueryParam("limit") @DefaultValue("" + Integer.MAX_VALUE) int limit,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("include") List<String> include,
            @QueryParam("exclude") List<String> exclude,
            @QueryParam("multiFilter") @DefaultValue("false") boolean multiFilter);

    @GET
    @Path("{id}/metadata")
    Object getMetadata(@PathParam("id") int id,
            @QueryParam("schemaUri") String schemaUri);

    @POST
    @Path("{id}/updateAccess")
    // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
    void updateRunAccess(@PathParam("id") int id,
            @QueryParam("owner") String owner,
            @QueryParam("access") Access access);

    @POST
    @Path("test")
    @Consumes(MediaType.APPLICATION_JSON)
    List<Integer> addRun(@QueryParam("test") String testNameOrId,
            @QueryParam("owner") String owner,
            @QueryParam("access") Access access,
            Run run);

    @GET
    @Path("autocomplete")
    @ApiIgnore
    List<String> autocomplete(@QueryParam("query") String query);

    @GET
    @Path("list")
    io.hyperfoil.tools.horreum.api.services.RunService.RunsSummary listAllRuns(@QueryParam("query") String query,
            @QueryParam("matchAll") boolean matchAll,
            @QueryParam("roles") String roles,
            @QueryParam("trashed") boolean trashed,
            @QueryParam("limit") Integer limit,
            @QueryParam("page") Integer page,
            @QueryParam("sort") String sort,
            @QueryParam("direction") SortDirection direction);

    @GET
    @Path("count")
    io.hyperfoil.tools.horreum.api.services.RunService.RunCount runCount(@QueryParam("testId") int testId);

    @GET
    @Path("list/{testId}")
    io.hyperfoil.tools.horreum.api.services.RunService.RunsSummary listTestRuns(@PathParam("testId") int testId,
            @QueryParam("trashed") boolean trashed,
            @QueryParam("limit") Integer limit,
            @QueryParam("page") Integer page,
            @QueryParam("sort") String sort,
            @QueryParam("direction") SortDirection direction);

    @GET
    @Path("bySchema")
    io.hyperfoil.tools.horreum.api.services.RunService.RunsSummary listRunsBySchema(@QueryParam("uri") String uri,
            @QueryParam("limit") Integer limit,
            @QueryParam("page") Integer page,
            @QueryParam("sort") String sort,
            @QueryParam("direction") SortDirection direction);

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
    Map<Integer, String> updateRunSchema(@PathParam("id") int id,
            @QueryParam("path") String path, String schemaUri);

    @POST
    @Path("{id}/recalculate")
    List<Integer> recalculateRunDatasets(@PathParam("id") int runId);

    @POST
    @Path("recalculateAll")
    void recalculateAll(@QueryParam("from") String from, @QueryParam("to") String to);
}
