package io.hyperfoil.tools.horreum.server;

import static io.quarkus.vertx.http.runtime.security.HttpCredentialTransport.Type.OTHER_HEADER;

import java.util.Collections;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.BaseAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

/**
 * Look for a special HTTP header to provide authentication of HTTP requests
 */
@ApplicationScoped
public class ApiKeyAuthenticationMechanism implements HttpAuthenticationMechanism {

    public static final String HORREUM_API_KEY_HEADER = "X-Horreum-API-Key";

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        String headerValue = context.request().headers().get(HORREUM_API_KEY_HEADER);
        return headerValue == null ? Uni.createFrom().nullItem()
                : identityProviderManager.authenticate(new ApiKeyAuthenticationMechanism.Request(headerValue));
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(new ChallengeData(HttpResponseStatus.UNAUTHORIZED.code(), null, null));
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Collections.singleton(ApiKeyAuthenticationMechanism.Request.class);
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return Uni.createFrom().item(new HttpCredentialTransport(OTHER_HEADER, HORREUM_API_KEY_HEADER));
    }

    public static class Request extends BaseAuthenticationRequest implements AuthenticationRequest {

        private final String key;

        public Request(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }
}
