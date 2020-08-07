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
import javax.ws.rs.WebApplicationException;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.grafana.GrafanaClient;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.jwt.auth.principal.JWTCallerPrincipal;

/**
 * Make sure that matching Grafana user exists. We cache the fact in cookie to avoid querying Grafana all the time.
 */
@WebFilter("/*")
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
         chain.doFilter(req, res);
         return;
      }
      String email = ((JWTCallerPrincipal) identity.getPrincipal()).getClaim("email");
      if (email == null || email.isEmpty()) {
         chain.doFilter(req, res);
         return;
      }
      if (req.getCookies() != null) {
         for (Cookie cookie : req.getCookies()) {
            if (cookie.getName().equals(GRAFANA_USER) && email.equals(cookie.getValue())) {
               chain.doFilter(req, res);
               return;
            }
         }
      }
      GrafanaClient.UserInfo userInfo = null;
      try {
          userInfo = grafana.lookupUser(email);
      } catch (WebApplicationException e) {
         if (e.getResponse().getStatus() == 404) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            String password = String.format("_%X%X%X!", random.nextLong(), random.nextLong(), random.nextLong());
            userInfo = new GrafanaClient.UserInfo(email, email, email, password, 1);
            try {
               grafana.createUser(userInfo);
            } catch (WebApplicationException e2) {
               log.errorf(e2, "Failed to create user %s", email);
               userInfo = null;
            }
         } else {
            log.errorf(e, "Failed to fetch user %s", email);
         }
      }
      if (userInfo != null) {
         res.addCookie(new Cookie(GRAFANA_USER, email));
      }
      chain.doFilter(req, res);
   }
}
