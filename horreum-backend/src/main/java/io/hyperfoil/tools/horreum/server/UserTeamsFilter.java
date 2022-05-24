package io.hyperfoil.tools.horreum.server;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.svc.UserServiceImpl;
import io.quarkus.security.identity.SecurityIdentity;

@WebFilter(filterName = "UserTeamsFilter", asyncSupported = true)
@ApplicationScoped
public class UserTeamsFilter extends HttpFilter {
   private static final Logger log = Logger.getLogger(GrafanaUserFilter.class);
   private static final String TEAMS = "horreum.teams";

   @Inject
   SecurityIdentity identity;

   @Inject
   UserServiceImpl userService;

   @Override
   protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
      if (identity.isAnonymous()) {
         // ignore anonymous access
         chain.doFilter(req, res);
         return;
      }
      Set<String> teams = identity.getRoles().stream().filter(r -> r.endsWith("-team")).collect(Collectors.toSet());
      String username = identity.getPrincipal().getName();
      if (req.getCookies() != null) {
         OUTER: for (Cookie cookie : req.getCookies()) {
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
                  // teams in cookie match identity
                  chain.doFilter(req, res);
                  return;
               } else {
                  break; // OUTER
               }
            }
         }
      }
      userService.cacheUserTeams(username, teams);
      // Cookie API does not allow to set SameSite attribute
      res.setHeader("Set-Cookie", TEAMS + "=" + username + "!" + String.join("+", teams) + ";path=/;SameSite=Lax");
      chain.doFilter(req, res);
   }
}
