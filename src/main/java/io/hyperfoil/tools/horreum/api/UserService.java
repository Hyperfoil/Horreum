package io.hyperfoil.tools.horreum.api;

import java.util.List;
import java.util.concurrent.CompletionStage;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.keycloak.KeycloakClient;

@Path("api/user")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface UserService {
   @GET
   @Path("search")
   CompletionStage<List<KeycloakClient.User>> searchUsers(@QueryParam("query") String query);

   @POST
   @Path("info")
   CompletionStage<List<KeycloakClient.User>> info(List<String> usernames);

   @GET
   @Path("teams")
   CompletionStage<List<String>> getTeams();
}
