package io.hyperfoil.tools.horreum.server;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.hyperfoil.tools.horreum.svc.Util;

@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 101)
@WithToken
public class TokenInterceptor {
   public final static String TOKEN_HEADER = "x-horreum-token";

   @Inject
   HttpServletRequest request;

   @Inject
   HttpServletResponse response;

   @Inject
   RoleManager roleManager;

   @Inject
   EntityManager em;

   @AroundInvoke
   public Object wrap(InvocationContext ctx) throws Exception {
      String queryParam = Util.getAnnotation(ctx.getMethod(), WithToken.class).queryParam();
      String[] tokens = request.getParameterValues(queryParam);
      // TODO: fetch tokens from cookie
      String token = tokens != null && tokens.length > 0 ? tokens[0] : request.getHeader(TOKEN_HEADER);
      if (token != null && !token.isBlank()) {
         if (looksLikeJWT(token)) {
            response.setStatus(400);
            response.getWriter().println("It seems that you are trying to pass JWT token as Horreum token. Please use HTTP header 'Authorization: Bearer <token>' instead.");
            response.flushBuffer();
            return null;
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
