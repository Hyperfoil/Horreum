package io.hyperfoil.tools.horreum.server;

import java.util.List;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriInfo;

import io.hyperfoil.tools.horreum.svc.Util;
import org.jboss.resteasy.reactive.server.spi.ServerRequestContext;

@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 101)
@WithToken
public class TokenInterceptor {
   public final static String TOKEN_HEADER = "x-horreum-token";

   @Inject
   RoleManager roleManager;

   @Inject
   EntityManager em;

   @Inject
   UriInfo uriInfo;

   @Inject
   HttpHeaders httpHeaders;

   @AroundInvoke
   public Object wrap(InvocationContext ctx) throws Exception {
      String queryParam = Util.getAnnotation(ctx.getMethod(), WithToken.class).queryParam();
      List<String> tokens = uriInfo.getQueryParameters().get(queryParam);
      // TODO: fetch tokens from cookie
      String token = tokens != null && !tokens.isEmpty() ? tokens.get(0) : httpHeaders.getHeaderString(TOKEN_HEADER);
      if (token != null && !token.isBlank()) {
         if (looksLikeJWT(token)) {
            throw new JWTBadRequestException();
         }
         roleManager.setToken(em, token);
      }
      // TODO: store query tokens in a cookie
      try {
         return ctx.proceed();
      } finally {
         roleManager.setToken(em, "");
      }
   }

   private boolean looksLikeJWT(String token) {
      int dots = 0;
      for (int i = 0; i < token.length(); ++i) {
         if (token.charAt(i) == '.') {
            ++dots;
         }
      }
      return dots == 2;
   }
}
