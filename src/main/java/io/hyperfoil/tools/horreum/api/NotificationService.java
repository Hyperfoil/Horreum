package io.hyperfoil.tools.horreum.api;

import java.util.List;
import java.util.Set;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.transaction.SystemException;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.entity.alerting.NotificationSettings;
import io.hyperfoil.tools.horreum.svc.Roles;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/api/notifications")
public interface NotificationService {
   @PermitAll
   @GET
   @Path("/methods")
   Set<String> methods();

   @RolesAllowed({ Roles.TESTER, Roles.ADMIN})
   @GET
   @Path("/settings")
   List<NotificationSettings> settings(@QueryParam("name") String name, @QueryParam("team") boolean team);

   @RolesAllowed({ Roles.TESTER, Roles.ADMIN})
   @POST
   @Path("/settings")
   @Transactional
   void updateSettings(@QueryParam("name") String name, @QueryParam("team") boolean team, NotificationSettings[] settings) throws SystemException;
}
