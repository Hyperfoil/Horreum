package io.hyperfoil.tools.horreum.svc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;
import javax.transaction.Transactional;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hibernate.exception.ConstraintViolationException;
import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.api.UserService;
import io.hyperfoil.tools.horreum.entity.UserInfo;
import io.hyperfoil.tools.horreum.keycloak.KeycloakClient;
import io.quarkus.security.identity.SecurityIdentity;

@PermitAll
public class UserServiceImpl implements UserService {
   private static final Logger log = Logger.getLogger(UserServiceImpl.class);

   @Inject @RestClient
   KeycloakClient keycloak;

   @Inject
   SecurityIdentity identity;

   @Inject
   SqlServiceImpl sqlService;

   @Inject
   EntityManager em;

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

   public void cacheUserTeams(String username, Set<String> teams) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         // Running this without pessimistic lock leads to duplicate inserts at the same time
         UserInfo userInfo = UserInfo.findById(username, LockModeType.PESSIMISTIC_WRITE);
         if (userInfo == null) {
            userInfo = new UserInfo();
            userInfo.username = username;
         } else if (!teams.equals(userInfo.teams)) {
            userInfo.teams = teams;
         }
         userInfo.persistAndFlush();
      } catch (PersistenceException e) {
         if (e.getCause() instanceof ConstraintViolationException) {
            // silently ignore
            // note: alternative would be to define @SQLInsert with INSERT ... ON CONFLICT DO NOTHING
            log.tracef(e, "Concurrent insertion of %s", username);
         } else {
            throw e;
         }
      }
   }

   @Override
   public String defaultTeam() {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         UserInfo userInfo = UserInfo.findById(identity.getPrincipal().getName());
         return userInfo != null ? userInfo.defaultTeam : null;
      }
   }

   @Override
   @Transactional
   public void setDefaultTeam(String team) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         UserInfo userInfo = UserInfo.findById(identity.getPrincipal().getName());
         userInfo.defaultTeam = Util.destringify(team);
         userInfo.persistAndFlush();
      }
   }
}
