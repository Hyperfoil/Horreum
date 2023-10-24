package io.hyperfoil.tools.horreum.api.services;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.Startup;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

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


   class VersionInfo {
      @Schema(description="Version of Horreum", example="0.9.4")
      @NotNull
      public String version;
      @JsonProperty(required = true)
      @Schema(description="Timestamp of server startup", example = "2023-10-18 18:00:57")
      public long startTimestamp;
   }

   class KeycloakConfig {
      @Schema(description="Keycloak realm securing Horreum instance", example = "horreum")
      public String realm;
      @Schema(description="URL of Keycloak instance securing Horreum", example = "https://horreum-keycloak.example.com")
      public String url;
      @Schema(description="Keycloak client ID in Horreum realm for User Interface", example = "horreum-ui")
      public String clientId;
   }
}
