package io.hyperfoil.tools.horreum.server;

import io.quarkus.logging.Log;
import io.quarkus.security.AuthenticationFailedException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.hyperfoil.tools.horreum.entity.user.UserApiKey;
import io.hyperfoil.tools.horreum.svc.TimeService;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;

/**
 * Retrieve and validate the key got from {@link ApiKeyAuthenticationMechanism} and create a SecurityIdentity from it.
 */
@ApplicationScoped
public class ApiKeyIdentityProvider implements IdentityProvider<ApiKeyAuthenticationMechanism.Request> {

    @Inject
    TimeService timeService;

    @Override
    public Class<ApiKeyAuthenticationMechanism.Request> getRequestType() {
        return ApiKeyAuthenticationMechanism.Request.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(ApiKeyAuthenticationMechanism.Request request,
            AuthenticationRequestContext context) {
        return context.runBlocking(() -> identityFromKey(request.getKey()));
    }

    @Transactional
    SecurityIdentity identityFromKey(String key) {
        return UserApiKey.findOptional(key)
                .filter(k -> !k.revoked)
                .map((userKey) -> {
                    Log.debugv("Authentication of user {0} with key \"{1}\" {2}", userKey.user.username, userKey.name, key);

                    // update last access
                    userKey.access = timeService.today();

                    // create identity with just the principal, roles will be populated in RolesAugmentor
                    return QuarkusSecurityIdentity.builder().setPrincipal(new QuarkusPrincipal(userKey.user.username)).build();
                })
                .orElse(null);
    }
}
