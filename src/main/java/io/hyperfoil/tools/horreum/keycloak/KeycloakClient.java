package io.hyperfoil.tools.horreum.keycloak;

import java.util.List;
import java.util.concurrent.CompletionStage;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.hyperfoil.tools.yaup.json.Json;

@RegisterRestClient(configKey = "horreum.keycloak")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface KeycloakClient {

   @POST
   @Path("auth/realms/horreum/protocol/openid-connect/token")
   @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
   CompletionStage<String> getToken(String body);

   @GET
   @Path("auth/admin/realms/horreum/users")
   CompletionStage<List<User>> getUsers(@HeaderParam("Authorization") String authorization, @QueryParam("search") String search, @QueryParam("username") String username);

   @GET
   @Path("auth/admin/realms/horreum/roles")
   CompletionStage<List<Role>> getRoles(@HeaderParam("Authorization") String authorization);

   static CompletionStage<String> getToken(KeycloakClient client) {
      String offlineToken = ConfigProvider.getConfig().getValue("horreum.keycloak.user.reader.token", String.class);
      // TODO: maybe cache this token?
      return client.getToken("grant_type=refresh_token&client_id=horreum-ui&refresh_token=" + offlineToken)
            .thenApply(jwt -> Json.fromString(jwt).getString("access_token"));
   }

   static CompletionStage<List<User>> getUsers(KeycloakClient client, String search) {
      return getToken(client).thenCompose(token -> client.getUsers("Bearer " + token, search, null));
   }

   static CompletionStage<List<Role>> getRoles(KeycloakClient client) {
      return getToken(client).thenCompose(token -> client.getRoles("Bearer " + token));
   }

   class User {
      public String id;
      public String username;
      public String firstName;
      public String lastName;
      public String email;
      // ignore other details
   }

   class Role {
      public String id;
      public String name;
   }
}
