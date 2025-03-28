package io.hyperfoil.tools.horreum.api.internal.services;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.hyperfoil.tools.horreum.api.alerting.Watch;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/api/subscriptions")
@Tag(name = "subscriptions", description = "Manage subscriptions")
public interface SubscriptionService {

    @GET
    @Path("/{testId}")
    Watch getSubscription(@PathParam("testId") int testId);

    @POST
    @Path("/{testid}")
    void updateSubscription(@PathParam("testid") int testId, @RequestBody(required = true) Watch watch);

    @POST
    @Path("/{testid}/add")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
    List<String> addUserOrTeam(@PathParam("testid") int testId,
            @RequestBody(required = true) String userOrTeam);

    @POST
    @Path("/{testid}/remove")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
    List<String> removeUserOrTeam(@PathParam("testid") int testId,
            @RequestBody(required = true) String userOrTeam);
}
