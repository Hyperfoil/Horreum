package io.hyperfoil.tools.horreum.api.services;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

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