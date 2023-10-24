package io.hyperfoil.tools.horreum.api.internal.services;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("api/user")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "user", description = "Manage users")
public interface UserService {
   @GET
   @Path("search")
   List<UserData> searchUsers(@Parameter(required = true) @QueryParam("query") String query);

   @POST
   @Path("info")
   List<UserData> info(@RequestBody(required = true) List<String> usernames);

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
   Map<String, List<String>> teamMembers(@PathParam("team") String team);

   @POST
   @Path("team/{team}/members")
   void updateTeamMembers(@PathParam("team") String team,
                                           @RequestBody(required = true) Map<String, List<String>> roles);

   @GET
   @Path("allTeams")
   List<String> getAllTeams();

   @Path("team/{team}")
   @POST
   void addTeam(@PathParam("team") String team);

   @Path("team/{team}")
   @DELETE
   void deleteTeam(@PathParam("team") String team);

   @GET
   @Path("administrators")
   List<UserData> administrators();

   @POST
   @Path("administrators")
   void updateAdministrators(@RequestBody(required = true) List<String> administrators);

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
