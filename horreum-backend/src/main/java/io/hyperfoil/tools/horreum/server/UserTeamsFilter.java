package io.hyperfoil.tools.horreum.server;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;

import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.svc.CachedSecurityIdentity;
import io.hyperfoil.tools.horreum.svc.UserServiceImpl;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;

import org.jboss.resteasy.reactive.server.ServerRequestFilter;

@Singleton
public class UserTeamsFilter {
   private static final Logger log = Logger.getLogger(UserTeamsFilter.class);
   private static final String TEAMS = "horreum.teams";

   @Inject
   CurrentIdentityAssociation identityAssociation;

   @Inject
   UserServiceImpl userService;

   // Injecting Vertx here directly seems to cause a dependency cycle
   // https://github.com/quarkusio/quarkus/issues/27752
   @Inject
   Instance<Vertx> vertxInstance;

   @ServerRequestFilter(priority = Priorities.HEADER_DECORATOR + 20)
   public Uni<Void> filter(ContainerRequestContext containerRequestContext) {
      return identityAssociation.getDeferredIdentity()
            .onItem().transform(identity -> {
               filter(containerRequestContext, identity);
               return null;
            });
   }

   public void filter(ContainerRequestContext containerRequestContext, SecurityIdentity identity) {
      if (identity.isAnonymous()) {
         return;
      }

      Set<String> teams = identity.getRoles().stream().filter(r -> r.endsWith("-team")).collect(Collectors.toSet());
      String username = identity.getPrincipal().getName();
      OUTER: for (Cookie cookie : containerRequestContext.getCookies().values()) {
         if (cookie.getName().equals(TEAMS)) {
            int userEndIndex = cookie.getValue().indexOf('!');
            if (userEndIndex < 0 || !cookie.getValue().substring(0, userEndIndex).equals(username)) {
               // cookie belongs to another user
               break;
            }
            String[] cookieTeams = cookie.getValue().substring(userEndIndex + 1).split("\\+");
            if (cookieTeams.length == teams.size()) {
               for (String team : cookieTeams) {
                  if (!teams.contains(team)) {
                     break OUTER;
                  }
               }
               return;
            } else {
               break; // OUTER
            }
         }
      }

      CachedSecurityIdentity copy = new CachedSecurityIdentity(identity);
      vertxInstance.get().executeBlocking(Uni.createFrom().item(() -> {
         RolesInterceptor.setCurrentIdentity(copy);
         try {
            userService.cacheUserTeams(username, teams);
         } finally {
            RolesInterceptor.setCurrentIdentity(null);
         }
         return null;
      })).subscribe().with(nil -> {}, t -> {
         log.warn("Failed to cache teams for user " + username, t);
      });
      // Cookie API does not allow to set SameSite attribute
      containerRequestContext.getHeaders().add("Set-Cookie", TEAMS + "=" + username + "!" + String.join("+", teams) + ";path=/;SameSite=Lax");
   }

}
