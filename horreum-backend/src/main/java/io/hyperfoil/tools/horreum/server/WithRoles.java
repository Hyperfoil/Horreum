package io.hyperfoil.tools.horreum.server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Function;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

@Inherited
@InterceptorBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface WithRoles {
   @Nonbinding String[] extras() default {};
   @Nonbinding boolean addUsername() default false;
   @Nonbinding Class<? extends Function<Object[], String[]>> fromParams() default IgnoreParams.class;

   final class IgnoreParams implements Function<Object[], String[]> {
      @Override
      public String[] apply(Object[] objects) {
         return new String[0];
      }
   }
}
