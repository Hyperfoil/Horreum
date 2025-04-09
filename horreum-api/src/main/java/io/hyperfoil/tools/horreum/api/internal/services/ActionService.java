package io.hyperfoil.tools.horreum.api.internal.services;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.Action;
import io.hyperfoil.tools.horreum.api.data.AllowedSite;

@Path("/api/action")
@Consumes({ MediaType.APPLICATION_JSON })
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "action", description = "Manage Actions")
public interface ActionService {
    @POST
    Action addAction(Action action);

    @POST
    @Path("global")
    Action addGlobalAction(Action action);

    @PUT
    Action updateAction(@RequestBody(required = true) Action action);

    @GET
    @Path("{id}")
    Action getAction(@PathParam("id") int id);

    @DELETE
    @Path("{id}")
    void deleteAction(@PathParam("id") int id);

    @DELETE
    @Path("global/{id}")
    void deleteGlobalAction(@PathParam("id") int id);

    @GET
    @Path("list")
    List<Action> listActions(@QueryParam("limit") Integer limit,
            @QueryParam("page") Integer page,
            @QueryParam("sort") @DefaultValue("id") String sort,
            @QueryParam("direction") @DefaultValue("Ascending") SortDirection direction);

    @GET
    @Path("test/{id}")
    List<Action> getTestActions(@PathParam("id") int testId);

    @GET
    @Path("allowedSites")
    List<AllowedSite> allowedSites();

    @Consumes("text/plain")
    @POST
    @Path("allowedSites")
    AllowedSite addSite(@RequestBody(required = true) String prefix);

    @DELETE
    @Path("allowedSites/{id}")
    void deleteSite(@PathParam("id") long id);
}
