package io.hyperfoil.tools.horreum.server;

import io.hyperfoil.tools.horreum.svc.Roles;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.processor.AnnotationsTransformer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.security.jpa.runtime.JpaIdentityProvider;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * Enhance the security-jpa {@link JpaIdentityProvider} to work with row level security.
 * Creates a build step that adds an annotation to a target method on the JpaIdentityProvider class.
 * The procedure is done at build time since the identity provider is invoked so early in the request processing pipeline.
 * That annotation is the binding for an interceptor that is invoked around that method, that fetches the username/password from the database.
 * The interceptor sets the necessary role to comply with row level security.
 */
public class JpaIdentityProviderRolesExtension {

    private static final DotName IDENTITY_PROVIDER_DOT_NAME = DotName.createSimple(JpaIdentityProvider.class);

    private static boolean isTargetMethod(MethodInfo method) {
        // could use one of the authenticate() methods instead, but we hook into getSingleUser() method as it is unique to JPAIdentityProvider
        return IDENTITY_PROVIDER_DOT_NAME.equals(method.declaringClass().name()) && "getSingleUser".equals(method.name());
    }

    @BuildStep AnnotationsTransformerBuildItem transform() {
        return new AnnotationsTransformerBuildItem(
                AnnotationsTransformer.appliedToMethod()
                                      .whenMethod(JpaIdentityProviderRolesExtension::isTargetMethod)
                                      .thenTransform(t -> t.add(WithJpaIdentityProviderRole.class))
        );
    }

    @Inherited
    @InterceptorBinding
    @Target({ ElementType.METHOD, ElementType.TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    private @interface WithJpaIdentityProviderRole {
    }

    @Interceptor @Priority(LIBRARY_BEFORE) @WithJpaIdentityProviderRole public static class JpaIdentityProviderInterceptor {

        @Inject RoleManager roleManager;

        @AroundInvoke public Object intercept(InvocationContext ctx) throws Exception {
            String previous = roleManager.setRoles(Roles.HORREUM_SYSTEM);
            try {
                return ctx.proceed();
            } finally {
                roleManager.setRoles(previous);
            }
        }
    }
}
