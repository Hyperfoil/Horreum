package io.hyperfoil.tools.horreum.api;

import javax.annotation.security.PermitAll;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.config.ConfigProvider;

import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.runtime.Startup;

@Startup
@PermitAll
@Path("/api/config")
@Produces(MediaType.APPLICATION_JSON)
public class ConfigService {
   long startTimestamp = System.currentTimeMillis();

   @GET
   @Path("keycloak")
   public Response keycloak() {
      return Response.ok(Json.map()
            .add("realm", "horreum")
            .add("url", getString("horreum.keycloak.url"))
            .add("clientId", "horreum-ui")
            .build().toString()).build();
   }

   @GET
   @Path("version")
   public Json version() {
      return new Json.MapBuilder()
            .add("version", getString("quarkus.application.version"))
            .add("commit", getString("horreum.build.commit"))
            .add("buildTimestamp", Long.parseLong(getString("horreum.build.timestamp")))
            .add("startTimestamp", startTimestamp)
            .build();
   }

   private String getString(String propertyName) {
      return ConfigProvider.getConfig().getValue(propertyName, String.class);
   }
}
