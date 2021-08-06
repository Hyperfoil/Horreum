package io.hyperfoil.tools.horreum.svc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.api.UserService;
import io.hyperfoil.tools.horreum.keycloak.KeycloakClient;
import io.quarkus.security.identity.SecurityIdentity;

@PermitAll
public class UserServiceImpl implements UserService {
   private static final Logger log = Logger.getLogger(UserServiceImpl.class);

   @Inject @RestClient
   KeycloakClient keycloak;

   @Inject
   SecurityIdentity identity;

   @Override
   public CompletionStage<List<KeycloakClient.User>> searchUsers(String query) {
      if (identity.isAnonymous()) {
         throw new WebApplicationException(Response.Status.FORBIDDEN);
      }
      return KeycloakClient.getUsers(keycloak, query).thenApply(
            users -> users.stream().filter(u -> !u.username.startsWith("__")).collect(Collectors.toList()));
   }

   @Override
   public CompletionStage<List<KeycloakClient.User>> info(List<String> usernames) {
      if (identity.isAnonymous()) {
         throw new WebApplicationException(Response.Status.FORBIDDEN);
      }
      return KeycloakClient.getToken(keycloak).thenCompose(token -> {
         String auth = "Bearer " + token;
         CompletableFuture<List<KeycloakClient.User>> future = new CompletableFuture<>();
         AtomicInteger countDown = new AtomicInteger(usernames.size());
         List<KeycloakClient.User> users = new ArrayList<>();
         for (String username: usernames) {
            keycloak.getUsers(auth, null, username).whenComplete((res, t) -> {
               if (t == null) {
                  synchronized (users) {
                     res.forEach(u -> {
                        if (username.equals(u.username)) {
                           users.add(u);
                        }
                     });
                     if (countDown.decrementAndGet() == 0) {
                        future.complete((users));
                     }
                  }
               } else {
                  log.errorf(t, "Failed to fetch info for user %s", username);
                  future.completeExceptionally(new WebApplicationException());
               }
            });
         }
         return future;
      });
   }

   @Override
   public CompletionStage<List<String>> getTeams() {
      if (identity.isAnonymous()) {
         throw new WebApplicationException(Response.Status.FORBIDDEN);
      }
      return KeycloakClient.getRoles(keycloak)
            .thenApply(roles -> roles.stream().map(r -> r.name).filter(n -> n.endsWith("-team")).collect(Collectors.toList()));
   }
}
