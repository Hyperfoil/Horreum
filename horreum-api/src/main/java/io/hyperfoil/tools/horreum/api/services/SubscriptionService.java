package io.hyperfoil.tools.horreum.api.services;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.api.alerting.Watch;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/api/subscriptions")
public interface SubscriptionService {
   @GET
   @Path("/")
   Map<Integer, Set<String>> all(@QueryParam("folder") String folder);

   @GET
   @Path("/{testId}")
   Watch get(@PathParam("testId") int testId);

   @POST
   @Path("/{testid}")
   void update(@PathParam("testid") int testId, @RequestBody(required = true) Watch watch);

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