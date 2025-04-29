package io.hyperfoil.tools.horreum.api.services;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.eclipse.microprofile.openapi.annotations.enums.ParameterIn.PATH;
import static org.eclipse.microprofile.openapi.annotations.enums.SchemaType.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("api/user")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Tag(name = "User", description = "Manage user accounts")
@Extension(name = "x-smallrye-profile-external", value = "")
public interface UserService {

    @GET
    @Path("roles")
    @Operation(description = "Get roles for the authenticated user.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = ARRAY, implementation = String.class)))
    List<String> getRoles();

    @GET
    @Path("search")
    @Operation(description = "Search for user(s) with an optional query condition.")
    @Parameter(required = true, name = "query", in = PATH, description = "Filter users by username (case insensitive)", schema = @Schema(type = STRING))
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = ARRAY, implementation = UserData.class)))
    List<UserData> searchUsers(@QueryParam("query") String query);

    @POST
    @Path("info")
    @Operation(description = "Fetch user data for a group of users.")
    @RequestBody(name = "usernames", required = true, content = @Content(schema = @Schema(type = ARRAY, implementation = String.class)))
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = ARRAY, implementation = UserData.class)))
    List<UserData> info(List<String> usernames);

    @POST
    @Path("createUser")
    @Operation(description = "Create new user.")
    @RequestBody(name = "user", required = true, content = @Content(schema = @Schema(type = OBJECT, implementation = NewUser.class)))
    void createUser(NewUser user);

    @DELETE
    @Path("{username}")
    @Operation(description = "Remove existing user.")
    @Parameter(name = "username", in = PATH, description = "Username to remove", schema = @Schema(type = STRING))
    void removeUser(@PathParam("username") String username);

    @GET
    @Path("teams")
    @Operation(description = "Get list of all teams.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = ARRAY, implementation = String.class)))
    List<String> getTeams();

    @GET
    @Path("defaultTeam")
    @Produces(TEXT_PLAIN)
    @Operation(description = "Get the default team of the current user.")
    @APIResponse(responseCode = "200", content = @Content(mediaType = TEXT_PLAIN, schema = @Schema(type = STRING)))
    String defaultTeam();

    @POST
    @Path("defaultTeam")
    @Consumes(TEXT_PLAIN)
    @Operation(description = "Set the default team of the current user.")
    @RequestBody(name = "team", required = true, content = @Content(mediaType = TEXT_PLAIN, schema = @Schema(type = STRING)))
    void setDefaultTeam(String team);

    @GET
    @Path("team/{team}/members")
    @Operation(description = "Get the membership of a given team.")
    @Parameter(name = "team", in = PATH, description = "Name of the team")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = OBJECT, implementation = Map.class, additionalProperties = List.class)))
    Map<String, List<String>> teamMembers(@PathParam("team") String team);

    @POST
    @Path("team/{team}/members")
    @Operation(description = "Set the membership of a given team.")
    @Parameter(name = "team", in = PATH, description = "Name of the team", schema = @Schema(type = STRING))
    @RequestBody(name = "roles", required = true, content = @Content(schema = @Schema(type = OBJECT, implementation = Map.class, additionalProperties = List.class)))
    void updateTeamMembers(@PathParam("team") String team, Map<String, List<String>> roles);

    @GET
    @Path("allTeams")
    @Operation(description = "Get list of all teams.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = ARRAY, implementation = String.class)))
    List<String> getAllTeams();

    @Path("team/{team}")
    @POST
    @Operation(description = "Create new team.")
    @Parameter(name = "team", in = PATH, description = "Name of the team to be created")
    void addTeam(@PathParam("team") String team);

    @Path("team/{team}")
    @DELETE
    @Operation(description = "Remove existing team.")
    @Parameter(name = "team", in = PATH, description = "Name of the team to be removed")
    void deleteTeam(@PathParam("team") String team);

    @GET
    @Path("administrators")
    @Operation(description = "Get the list of administrator users.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = ARRAY, implementation = UserData.class)))
    List<UserData> administrators();

    @POST
    @Path("administrators")
    @Operation(description = "Set the list of administrator users.")
    @RequestBody(name = "administrators", required = true, content = @Content(schema = @Schema(type = ARRAY, implementation = String.class)))
    void updateAdministrators(List<String> administrators);

    @POST
    @Path("/apikey")
    @Produces(TEXT_PLAIN)
    @Operation(description = "Create a new API key.")
    @RequestBody(name = "request", required = true, content = @Content(schema = @Schema(type = OBJECT, implementation = ApiKeyRequest.class)))
    @APIResponse(responseCode = "200", content = @Content(mediaType = TEXT_PLAIN, schema = @Schema(type = STRING)))
    String newApiKey(ApiKeyRequest request);

    @GET
    @Path("/apikey")
    @Operation(description = "List API keys.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = ARRAY, implementation = ApiKeyResponse.class)))
    List<ApiKeyResponse> apiKeys();

    @PUT
    @Path("/apikey/{id}/rename")
    @Consumes(TEXT_PLAIN)
    @Operation(description = "Rename API key.")
    @Parameter(name = "id", in = PATH, schema = @Schema(type = INTEGER), description = "id of the key to be renamed")
    @RequestBody(name = "newName", content = @Content(mediaType = TEXT_PLAIN, schema = @Schema(type = STRING)))
    void renameApiKey(@PathParam("id") long keyId, String newName);

    @PUT
    @Path("/apikey/{id}/revoke")
    @Operation(description = "Revoke API key.")
    @Parameter(name = "id", in = PATH, schema = @Schema(type = INTEGER), description = "id of the key to be revoked")
    void revokeApiKey(@PathParam("id") long keyId);

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

    /**
     * Key type allows to scope what the key gives access to
     */
    enum KeyType {
        USER
    }

    class ApiKeyRequest {
        public String name;
        public KeyType type;

        public ApiKeyRequest() {
        }

        public ApiKeyRequest(String name, KeyType type) {
            this.name = name;
            this.type = type;
        }
    }

    class ApiKeyResponse {
        public long id;
        public String name;
        public KeyType type;
        public Instant creation;
        public Instant access;
        public boolean isRevoked;
        public long toExpiration;
    }

}
