package io.hyperfoil.tools.horreum.api.services;

import java.util.Collection;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.api.alerting.NotificationSettings;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/api/notifications")
public interface NotificationService {
   @GET
   @Path("/methods")
   Collection<String> methods();

   @GET
   @Path("/settings")
   List<NotificationSettings> settings(@Parameter(required = true) @QueryParam("name") String name,
                                       @Parameter(required = true) @QueryParam("team") boolean team);

   @POST
   @Path("/settings")
   void updateSettings(@Parameter(required = true) @QueryParam("name") String name,
                       @Parameter(required = true) @QueryParam("team") boolean team,
                       @RequestBody(required = true) NotificationSettings[] settings);

   @POST
   @Path("test")
   void testNotifications(@QueryParam("method") String method,
                          @Parameter(required = true) @QueryParam("data") String data);
}
