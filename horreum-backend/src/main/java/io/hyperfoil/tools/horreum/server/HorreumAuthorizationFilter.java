package io.hyperfoil.tools.horreum.server;

import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.svc.Util;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.spec.HttpServletRequestImpl;

@WebFilter(filterName = "HorreumAuthorizationFilter", asyncSupported = true)
@ApplicationScoped
public class HorreumAuthorizationFilter extends HttpFilter {
   @ConfigProperty(name = "quarkus.oidc.auth-server-url")
   String authServerUrl;

   @ConfigProperty(name = "quarkus.oidc.token.issuer")
   Optional<String> issuer;

   @Override
   protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
      String authorization = req.getHeader(HttpHeaders.AUTHORIZATION);
      if (authorization != null && authorization.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
         int payloadStart = authorization.indexOf('.', 7);
         int payloadEnd = authorization.indexOf('.', payloadStart + 1);
         if (payloadStart > 0 && payloadEnd > 0 && payloadStart < payloadEnd) {
            // okay, looks like JWT token
            String payload = authorization.substring(payloadStart + 1, payloadEnd);
            JsonNode payloadObject = Util.toJsonNode(Base64.getDecoder().decode(payload));
            if (payloadObject == null) {
               res.setStatus(403);
               res.getWriter().println("Invalid authorization token");
               return;
            }
            String iss = payloadObject.path("iss").asText();
            if (iss == null || iss.isBlank()) {
               res.setStatus(403);
               res.getWriter().println("Authorization token does not contain issuer ('iss') claim.");
               return;
            }
            if (issuer.isPresent()) {
               if (issuer.get().equals("any")) {
                  // any issuer matches
               } else if (!issuer.get().equals(iss)) {
                  replyWrongIss(res, iss);
                  return;
               }
            } else if (!authServerUrl.equals(iss)) {
               replyWrongIss(res, iss);
               return;
            }
            chain.doFilter(req, res);
            return;
         }
         HttpServerExchange exchange = ((HttpServletRequestImpl) req).getExchange();
         exchange.removeRequestHeader(HttpHeaders.AUTHORIZATION);
         exchange.addRequestHeader(TokenInterceptor.TOKEN_HEADER, authorization.substring(7));
      }
      chain.doFilter(req, res);
   }

   private void replyWrongIss(HttpServletResponse res, String iss) throws IOException {
      res.setStatus(403);
      res.getWriter().println("Authorization token has issuer '" + iss + "' but this is not the expected issuer; you have probably received the token from a wrong URL. Please login into Horreum Web UI and check the login URL used.");
   }
}
