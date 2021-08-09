package io.hyperfoil.tools.horreum.server;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.grafana.GrafanaClient;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.jwt.auth.principal.JWTCallerPrincipal;

/**
 * Make sure that matching Grafana user exists. We cache the fact in cookie to avoid querying Grafana all the time.
 */
@WebFilter(value = "/*", asyncSupported = true)
@ApplicationScoped
public class GrafanaUserFilter extends HttpFilter {
   private static final Logger log = Logger.getLogger(GrafanaUserFilter.class);
   private static final String GRAFANA_USER = "grafana_user";

   @Inject
   SecurityIdentity identity;

   @Inject @RestClient
   GrafanaClient grafana;

   @Override
   protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
      if (!(identity.getPrincipal() instanceof JWTCallerPrincipal)) {
         // ignore anonymous access
         log.debug("Anonymouse access, ignoring.");
         chain.doFilter(req, res);
         return;
      }
      JWTCallerPrincipal principal = (JWTCallerPrincipal) identity.getPrincipal();
      String email = principal.getClaim("email");
      if (email == null || email.isEmpty()) {
         String username = principal.getName();
         if (username == null) {
            log.debug("Missing email and username, ignoring.");
            chain.doFilter(req, res);
            return;
         } else {
            email = username + "@horreum";
         }
      }
      if (req.getCookies() != null) {
         for (Cookie cookie : req.getCookies()) {
            if (cookie.getName().equals(GRAFANA_USER) && email.equals(cookie.getValue())) {
               log.debugf("%s already has cookie, ignoring.", email);
               chain.doFilter(req, res);
               return;
            }
         }
      }
      GrafanaClient.UserInfo userInfo = null;
      try {
          userInfo = grafana.lookupUser(email);
          log.debugf("User %s exists!", email);
      } catch (WebApplicationException e) {
         if (e.getResponse().getStatus() == 404) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            String password = String.format("_%X%X%X!", random.nextLong(), random.nextLong(), random.nextLong());
            userInfo = new GrafanaClient.UserInfo(email, email, email, password, 1);
            try {
               grafana.createUser(userInfo);
               log.infof("Created Grafana user %s (%s)", userInfo.login, userInfo.email);
            } catch (WebApplicationException e2) {
               if (e2.getResponse().getStatus() == 412) {
                  log.infof("This request did not create user %s due to a mid-air collision.", userInfo.login);
               } else {
                  log.errorf(e2, "Failed to create user %s", email);
                  userInfo = null;
               }
            }
         } else {
            log.errorf(e, "Failed to fetch user %s", email);
         }
      } catch (ProcessingException e) {
         log.debug("Grafana client failed with exception, ignoring.", e);
         chain.doFilter(req, res);
         return;
      }
      if (userInfo != null) {
         // Cookie API does not allow to set SameSite attribute
         // res.addCookie(new Cookie(GRAFANA_USER, email));
         // The cookie is to expire in 1 minute to handle Grafana restarts
         res.addHeader("Set-Cookie", GRAFANA_USER + "=" + email + ";max-age=60;path=/;SameSite=Lax");
      }
      chain.doFilter(req, res);
   }
}
