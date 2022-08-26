package io.hyperfoil.tools.horreum.server;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.Priorities;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.grafana.GrafanaClient;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.jwt.auth.principal.JWTCallerPrincipal;
import io.smallrye.mutiny.Uni;

import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Make sure that matching Grafana user exists. We cache the fact in cookie to avoid querying Grafana all the time.
 */
@Singleton
public class GrafanaUserFilter {
   private static final Logger log = Logger.getLogger(GrafanaUserFilter.class);
   private static final String GRAFANA_USER = "grafana_user";

   @Inject
   CurrentIdentityAssociation identityAssociation;

   @Inject @RestClient
   Provider<GrafanaClient> grafana;

   @ConfigProperty(name = "horreum.grafana.url")
   Optional<String> grafanaBaseUrl;

   @ServerRequestFilter(priority = Priorities.HEADER_DECORATOR + 30)
   public Uni<Void> filter(ContainerRequestContext containerRequestContext) {
      if (grafanaBaseUrl.orElse("").isEmpty()) {
         // ignore in tests if Grafana is disabled
         return Uni.createFrom().nullItem();
      }

      return identityAssociation.getDeferredIdentity()
            .onItem().transformToUni(identity -> filter(containerRequestContext, identity));
   }

   public Uni<Void> filter(ContainerRequestContext containerRequestContext, SecurityIdentity identity) {
      if (!(identity.getPrincipal() instanceof JWTCallerPrincipal)) {
         // ignore anonymous access
         log.debug("Anonymouse access, ignoring.");
         return Uni.createFrom().nullItem();
      }
      JWTCallerPrincipal principal = (JWTCallerPrincipal) identity.getPrincipal();
      String email = principal.getClaim("email");
      if (email == null || email.isEmpty()) {
         String username = principal.getName();
         if (username == null) {
            log.debug("Missing email and username, ignoring.");
            return Uni.createFrom().nullItem();
         } else {
            email = username + "@horreum";
         }
      }
      for (Cookie cookie : containerRequestContext.getCookies().values()) {
         if (cookie.getName().equals(GRAFANA_USER) && email.equals(cookie.getValue())) {
            log.debugf("%s already has cookie, ignoring.", email);
            return Uni.createFrom().nullItem();
         }
      }
      String userEmail = email;
      return grafana.get().lookupUser(email)
            .onItem().invoke(() -> log.debugf("User %s exists!", userEmail))
            .onFailure().recoverWithUni((Throwable t) -> {
         if (t instanceof WebApplicationException) {
            WebApplicationException e = (WebApplicationException) t;
            if (e.getResponse().getStatus() == 404) {
               ThreadLocalRandom random = ThreadLocalRandom.current();
               String password = String.format("_%X%X%X!", random.nextLong(), random.nextLong(), random.nextLong());
               GrafanaClient.UserInfo userInfo = new GrafanaClient.UserInfo(userEmail, userEmail, userEmail, password, 1);
               return grafana.get().createUser(userInfo)
                     .onItem().transform(nil -> {
                        log.infof("Created Grafana user %s (%s)", userInfo.login, userInfo.email);
                        return userInfo;
                     })
                     .onFailure().transform(t2 -> {
                  if (t2 instanceof WebApplicationException && ((WebApplicationException) t2).getResponse().getStatus() == 412) {
                     log.infof("This request did not create user %s due to a mid-air collision.", userInfo.login);
                  } else {
                     log.errorf(t, "Failed to create user %s", userEmail);
                  }
                  return null;
               });
            } else {
               log.errorf(e, "Failed to fetch user %s", userEmail);
            }
         } else if (t instanceof ProcessingException) {
            log.debug("Grafana client failed with exception, ignoring.", t);
         }
         return null;
      }).onItem().transform(userInfo -> {
         if (userInfo != null) {
            // Cookie API does not allow to set SameSite attribute
            // res.addCookie(new Cookie(GRAFANA_USER, email));
            // The cookie is to expire in 1 minute to handle Grafana restarts
            containerRequestContext.getHeaders().add("Set-Cookie", GRAFANA_USER + "=" + userEmail + ";max-age=60;path=/;SameSite=Lax");
         }
         return null;
      });
   }
}
