package io.hyperfoil.tools.horreum.api;

import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.entity.alerting.NotificationSettings;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/api/notifications")
public interface NotificationService {
   @GET
   @Path("/methods")
   Set<String> methods();

   @GET
   @Path("/settings")
   List<NotificationSettings> settings(@QueryParam("name") String name, @QueryParam("team") boolean team);

   @POST
   @Path("/settings")
   void updateSettings(@QueryParam("name") String name, @QueryParam("team") boolean team, NotificationSettings[] settings);
}
