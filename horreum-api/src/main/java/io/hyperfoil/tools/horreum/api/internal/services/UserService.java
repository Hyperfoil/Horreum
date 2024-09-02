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

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.smallrye.common.annotation.Blocking;

@Path("api/user")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "user", description = "Manage users")
public interface UserService {

    @GET
    @Path("roles")
    @Blocking
    List<String> getRoles();

    @GET
    @Path("search")
    @Blocking
    @Operation(description = "Search for user(s) with an optional query condition.")
    List<UserData> searchUsers(
            @Parameter(required = true, name = "query", description = "filter users by username (case insensitive)", example = "user") @QueryParam("query") String query);

    @POST
    @Path("info")
    @Blocking
    List<UserData> info(@RequestBody(required = true) List<String> usernames);

    @POST
    @Path("createUser")
    @Blocking
    void createUser(@RequestBody(required = true) NewUser user);

    @DELETE
    @Path("{username}")
    @Blocking
    void removeUser(@PathParam("username") String username);

    @GET
    @Path("teams")
    @Blocking
    List<String> getTeams();

    @GET
    @Path("defaultTeam")
    @Produces("text/plain")
    @Blocking
    String defaultTeam();

    @POST
    @Path("defaultTeam")
    @Consumes("text/plain")
    @Blocking
    void setDefaultTeam(@RequestBody(required = true) String team);

    @GET
    @Path("team/{team}/members")
    @Blocking
    Map<String, List<String>> teamMembers(@PathParam("team") String team);

    @POST
    @Path("team/{team}/members")
    @Blocking
    void updateTeamMembers(@PathParam("team") String team, @RequestBody(required = true) Map<String, List<String>> roles);

    @GET
    @Path("allTeams")
    @Blocking
    List<String> getAllTeams();

    @Path("team/{team}")
    @POST
    @Blocking
    void addTeam(@PathParam("team") String team);

    @Path("team/{team}")
    @DELETE
    @Blocking
    void deleteTeam(@PathParam("team") String team);

    @GET
    @Path("administrators")
    @Blocking
    List<UserData> administrators();

    @POST
    @Path("administrators")
    @Blocking
    void updateAdministrators(@RequestBody(required = true) List<String> administrators);

    @GET
    @Path("team/{team}/machine")
    @Blocking
    List<UserData> machineAccounts(@PathParam("team") String team);

    @POST
    @Path("/team/{team}/reset")
    @Consumes("text/plain")
    @Produces("text/plain")
    @Blocking
    String resetPassword(@PathParam("team") String team, @RequestBody(required = true) String username);

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
