package io.hyperfoil.tools.horreum.api.services;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.Startup;

import javax.annotation.security.PermitAll;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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
