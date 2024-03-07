package io.hyperfoil.tools.horreum.api.services;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hyperfoil.tools.horreum.api.data.datastore.Datastore;
import io.quarkus.runtime.Startup;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.List;

@Startup
@PermitAll
@Path("/api/config")
@Produces(APPLICATION_JSON)
@Tag(name = "Config", description = "Endpoint providing configuration for the Horreum System")
@Extension(name = "x-smallrye-profile-external", value = "")
public interface ConfigService {
   long startTimestamp = System.currentTimeMillis();
   String KEYCLOAK_BOOTSTRAP_URL = "/api/config/keycloak";

   @GET
   @Path("keycloak")
   @Operation(description="Obtain configuration information about keycloak server securing Horreum instance")
   KeycloakConfig keycloak();

   @GET
   @Path("version")
   @Operation(description="Obtain version of the running Horreum instance")
   VersionInfo version();


   @GET
   @Path("datastore/{team}")
   @Operation(description="Obtain list of configured datastores for particular team")
   @Parameters(value = {
           @Parameter(name = "team", description = "name of the team to search for defined datastores", example = "perf-team")
   })
   List<Datastore> datastores(@PathParam("team") String team);

   @POST
   @Path("datastore")
   @Operation(description="Create a new Datastore")
   @Consumes(APPLICATION_JSON)
   @APIResponseSchema(value = Integer.class,
           responseDescription = "The ID for the new Datastore",
           responseCode = "200")
   Integer newDatastore(@NotNull Datastore datastore);

   @PUT
   @Path("datastore")
   @Operation(description="Update an existing Datastore definition")
   @Consumes(APPLICATION_JSON)
   @APIResponseSchema(value = Integer.class,
           responseDescription = "The ID of the updated Datastore",
           responseCode = "200")
   Integer updateDatastore(Datastore backend);

   @GET
   @Path("datastore/{id}/test")
   @Operation(description="Test a Datastore connection")
   DatastoreTestResponse testDatastore(@PathParam("id") String datastoreId);

   @DELETE
   @Path("datastore/{id}")
   @Operation(description="Test a Datastore")
   void deleteDatastore(@PathParam("id") String datastoreId);



   class VersionInfo {
      @Schema(description="Version of Horreum", example="0.9.4")
      @NotNull
      public String version;
      @JsonProperty(required = true)
      @Schema(description="Timestamp of server startup", example = "2023-10-18 18:00:57")
      public long startTimestamp;
      @Schema(description="Privacy statement", example="link/to/external/privacy/statement")
      public String privacyStatement;
   }

   class KeycloakConfig {
      @Schema(description="Keycloak realm securing Horreum instance", example = "horreum")
      public String realm;
      @Schema(description="URL of Keycloak instance securing Horreum", example = "https://horreum-keycloak.example.com")
      public String url;
      @Schema(description="Keycloak client ID in Horreum realm for User Interface", example = "horreum-ui")
      public String clientId;
   }

   class DatastoreTestResponse {
      public String msg;
      public Boolean success;
   }
}
