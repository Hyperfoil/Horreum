package io.hyperfoil.tools.horreum.api.services;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.Startup;

import jakarta.annotation.security.PermitAll;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Startup
@PermitAll
@Path("/api/config")
@Produces(MediaType.APPLICATION_JSON)
public interface ConfigService {
   long startTimestamp = System.currentTimeMillis();
   String KEYCLOAK_BOOTSTRAP_URL = "/api/config/keycloak";

   @GET
   @Path("keycloak")
   KeycloakConfig keycloak();

   @GET
   @Path("version")
   VersionInfo version();


   class VersionInfo {
      @NotNull
      public String version;
      @JsonProperty(required = true)
      public long startTimestamp;
   }

   class KeycloakConfig {
      public String realm;
      public String url;
      public String clientId;
   }
}
