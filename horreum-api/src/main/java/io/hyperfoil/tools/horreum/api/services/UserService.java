package io.hyperfoil.tools.horreum.api.services;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

@Path("api/user")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface UserService {
   @GET
   @Path("search")
   List<UserData> searchUsers(@Parameter(required = true) @QueryParam("query") String query);

   @POST
   @Path("info")
   CompletionStage<List<UserData>> info(@RequestBody(required = true) List<String> usernames);

   @POST
   @Path("createUser")
   void createUser(@RequestBody(required = true) NewUser user);

   @GET
   @Path("teams")
   List<String> getTeams();

   @GET
   @Path("defaultTeam")
   @Produces("text/plain")
   String defaultTeam();

   @POST
   @Path("defaultTeam")
   @Consumes("text/plain")
   void setDefaultTeam(@RequestBody(required = true) String team);

   @GET
   @Path("team/{team}/members")
   CompletionStage<Map<String, List<String>>> teamMembers(@PathParam("team") String team);

   @POST
   @Path("team/{team}/members")
   CompletionStage<Void> updateTeamMembers(@PathParam("team") String team,
                                           @RequestBody(required = true) Map<String, List<String>> roles);

   @GET
   @Path("allTeams")
   CompletionStage<List<String>> getAllTeams();

   @Path("team/{team}")
   @POST
   CompletionStage<Void> addTeam(@PathParam("team") String team);

   @Path("team/{team}")
   @DELETE
   CompletionStage<Void> deleteTeam(@PathParam("team") String team);

   @GET
   @Path("administrators")
   CompletionStage<List<UserData>> administrators();

   @POST
   @Path("administrators")
   CompletionStage<Void> updateAdministrators(@RequestBody(required = true) List<String> administrators);

   // this is a simplified copy of org.keycloak.representations.idm.UserRepresentation
   class UserData {
      @NotNull
      public String id;
      @NotNull
      public String username;
      public String firstName;
      public String lastName;
      public String email;

      public UserData() {
      }

      public UserData(String id, String username, String firstName, String lastName, String email) {
         this.id = id;
         this.username = username;
         this.firstName = firstName;
         this.lastName = lastName;
         this.email = email;
      }
   }

   class NewUser {
      public UserData user;
      public String password;
      public String team;
      public List<String> roles;
   }
}
