package io.hyperfoil.tools.horreum.api;

import javax.annotation.security.PermitAll;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.hyperfoil.tools.yaup.json.Json;

@PermitAll
@Path("/api/config")
public class ConfigService {
   @ConfigProperty(name = "horreum.keycloak.url")
   String keycloakUrl;

   @GET
   @Path("keycloak")
   @Produces(MediaType.APPLICATION_JSON)
   public Response keycloak() {
      return Response.ok(Json.map()
            .add("realm", "horreum")
            .add("url", keycloakUrl)
            .add("clientId", "horreum-ui")
            .build().toString()).build();
   }
}
