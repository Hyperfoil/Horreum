package io.hyperfoil.tools.horreum.api.internal.services;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.hyperfoil.tools.horreum.api.data.JsonpathValidation;
import io.hyperfoil.tools.horreum.api.data.QueryResult;

@Path("/api/sql")
@Consumes({ MediaType.APPLICATION_JSON })
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "sql", description = "Manage sql service")
public interface SqlService {
    @GET
    @Path("testjsonpath")
    @Operation(description = "Test a JSONPath for syntax errors using database")
    JsonpathValidation testJsonPath(
            @Parameter(required = true, name = "testjsonpath", description = "JSONPath to be tested", example = "$.qdup") @QueryParam("query") String jsonpath);

    @Path("roles")
    @GET
    @Produces("text/plain")
    String roles(@QueryParam("system") @DefaultValue("false") boolean system);

    @GET
    @Path("{id}/queryrun")
    QueryResult queryRunData(@PathParam("id") int id,
            @Parameter(required = true, name = "query", description = "JSONPath path executed on the Run", example = "$.results") @QueryParam("query") String jsonpath,
            @QueryParam("uri") String schemaUri,
            @QueryParam("array") @DefaultValue("false") boolean array);

    @Path("{id}/querydataset")
    @GET
    QueryResult queryDatasetData(@PathParam("id") int datasetId,
            @Parameter(required = true, name = "query", description = "JSONPath path executed on the Dataset", example = "$.test") @QueryParam("query") String jsonpath,
            @QueryParam("array") @DefaultValue("false") boolean array,
            @QueryParam("schemaUri") String schemaUri);

}
