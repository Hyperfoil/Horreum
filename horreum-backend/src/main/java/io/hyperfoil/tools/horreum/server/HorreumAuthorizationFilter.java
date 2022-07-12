package io.hyperfoil.tools.horreum.server;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.svc.Util;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import javax.inject.Singleton;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

@Singleton
public class HorreumAuthorizationFilter {

   private final String authServerUrl;
   private final Optional<String> issuer;

   public HorreumAuthorizationFilter(@ConfigProperty(name = "quarkus.oidc.auth-server-url") String authServerUrl,
                                     @ConfigProperty(name = "quarkus.oidc.token.issuer") Optional<String> issuer) {
      this.authServerUrl = authServerUrl;
      this.issuer = issuer;
   }

   @ServerRequestFilter(priority = Priorities.HEADER_DECORATOR + 10)
   public Response filter(ContainerRequestContext containerRequestContext) {
      String authorization = containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
      if (authorization != null && authorization.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
         int payloadStart = authorization.indexOf('.', 7);
         int payloadEnd = authorization.indexOf('.', payloadStart + 1);
         if (payloadStart > 0 && payloadEnd > 0 && payloadStart < payloadEnd) {
            // okay, looks like JWT token
            String payload = authorization.substring(payloadStart + 1, payloadEnd);
            JsonNode payloadObject = Util.toJsonNode(Base64.getDecoder().decode(payload));
            if (payloadObject == null) {
               return Response.status(Response.Status.FORBIDDEN).entity("Invalid authorization token").build();
            }
            String iss = payloadObject.path("iss").asText();
            if (iss == null || iss.isBlank()) {
               return Response.status(Response.Status.FORBIDDEN).entity("Authorization token does not contain issuer ('iss') claim.").build();
            }
            if (issuer.isPresent()) {
               if (issuer.get().equals("any")) {
                  // any issuer matches
               } else if (!issuer.get().equals(iss)) {
                  return replyWrongIss(iss);
               }
            } else if (!authServerUrl.equals(iss)) {
               return replyWrongIss(iss);
            }
            return null;
         }
         MultivaluedMap<String, String> headers = containerRequestContext.getHeaders();
         headers.remove(HttpHeaders.AUTHORIZATION);
         headers.addFirst(TokenInterceptor.TOKEN_HEADER, authorization.substring(7));
      }
      return null;
   }

   private Response replyWrongIss(String iss) {
      return Response.status(Response.Status.FORBIDDEN).entity("Authorization token has issuer '" + iss + "' but this is not the expected issuer; you have probably received the token from a wrong URL. Please login into Horreum Web UI and check the login URL used.").build();
   }
}
