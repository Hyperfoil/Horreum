package io.hyperfoil.tools.horreum.api.internal.services;

import java.util.Collection;
import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.hyperfoil.tools.horreum.api.alerting.NotificationSettings;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/api/notifications")
@Tag(name = "notifications", description = "Manage reports")
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
