package io.hyperfoil.tools.auth;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

import org.jboss.logging.Logger;

public class HorreumApiKeyAuthentication implements ClientRequestFilter {

    /** sync with io.hyperfoil.tools.horreum.server.ApiKeyAuthenticationMechanism */
    public static final String HORREUM_AUTHENTICATION_HEADER = "X-Horreum-API-Key";

    private static final Logger LOG = Logger.getLogger(HorreumApiKeyAuthentication.class);

    private final String authenticationToken;

    private boolean showAuthMethod = true;

    public HorreumApiKeyAuthentication(String token) {
        authenticationToken = token;
    }

    public void filter(ClientRequestContext requestContext) {
        if (showAuthMethod) {
            LOG.infov("Authentication with Horreum API key");
            showAuthMethod = false;
        }
        requestContext.getHeaders().putSingle(HORREUM_AUTHENTICATION_HEADER, authenticationToken);
    }
}
