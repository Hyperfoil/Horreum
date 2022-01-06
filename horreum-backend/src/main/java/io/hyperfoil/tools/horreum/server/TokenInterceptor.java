package io.hyperfoil.tools.horreum.server;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletRequest;

import io.hyperfoil.tools.horreum.svc.Util;

@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 101)
@WithToken
public class TokenInterceptor {
   @Inject
   HttpServletRequest request;

   @Inject
   RoleManager roleManager;

   @Inject
   EntityManager em;

   @AroundInvoke
   public Object wrap(InvocationContext ctx) throws Exception {
      String queryParam = Util.getAnnotation(ctx.getMethod(), WithToken.class).queryParam();
      String[] tokens = request.getParameterValues(queryParam);
      // TODO: fetch tokens from cookie
      if (tokens != null && tokens.length > 0) {
         // TODO: support multiple tokens
         roleManager.setToken(em, tokens[0]);
      }
      // TODO: store query tokens in a cookie
      try {
         return ctx.proceed();
      } finally {
         roleManager.setToken(em, "");
      }
   }
}
