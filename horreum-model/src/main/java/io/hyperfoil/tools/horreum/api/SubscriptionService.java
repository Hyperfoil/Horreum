package io.hyperfoil.tools.horreum.api;

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

import io.hyperfoil.tools.horreum.entity.alerting.Watch;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/api/subscriptions")
public interface SubscriptionService {
   @GET
   @Path("/")
   Map<Integer, Set<String>> all(@QueryParam("folder") String folder);

   @GET
   @Path("/{testId}")
   Watch get(@PathParam("testId") Integer testId);

   @POST
   @Path("/{testid}")
   void update(@PathParam("testid") Integer testId, Watch watch);

   @POST
   @Path("/{testid}/add")
   @Consumes({ MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
   List<String> addUserOrTeam(@PathParam("testid") Integer testId, String userOrTeam);

   @POST
   @Path("/{testid}/remove")
   @Consumes({ MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
   List<String> removeUserOrTeam(@PathParam("testid") Integer testId, String userOrTeam);
}