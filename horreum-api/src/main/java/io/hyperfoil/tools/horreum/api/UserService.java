package io.hyperfoil.tools.horreum.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Path("api/user")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface UserService {
   @GET
   @Path("search")
   List<UserData> searchUsers(@QueryParam("query") String query);

   @POST
   @Path("info")
   CompletionStage<List<UserData>> info(List<String> usernames);

   @POST
   @Path("createUser")
   void createUser(NewUser user);

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
   void setDefaultTeam(String team);

   @GET
   @Path("team/{team}/members")
   CompletionStage<Map<String, List<String>>> teamMembers(@PathParam("team") String team);

   @POST
   @Path("team/{team}/members")
   CompletionStage<Void> updateTeamMembers(@PathParam("team") String team, Map<String, List<String>> roles);

   // this is a simplified copy of org.keycloak.representations.idm.UserRepresentation
   class UserData {
      public String id;
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
