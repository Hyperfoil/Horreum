package io.hyperfoil.tools.auth;

import java.io.IOException;

import javax.net.ssl.SSLContext;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;

import org.jboss.logging.Logger;
import org.jboss.resteasy.client.jaxrs.internal.BasicAuthentication;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.jboss.resteasy.plugins.providers.FormUrlEncodedProvider;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

import io.hyperfoil.tools.CustomResteasyJackson2Provider;

public class KeycloakClientRequestFilter implements ClientRequestFilter {

    private static final Logger LOG = Logger.getLogger(KeycloakClientRequestFilter.class);
    private static final String BEARER_SCHEME_WITH_SPACE = "Bearer ";

    private final Keycloak keycloak;

    private final BasicAuthentication basicAuthentication;

    private boolean showAuthMethod = true;

    public KeycloakClientRequestFilter(String keycloakBaseUrl,
            String keycloakRealm,
            String username,
            String password,
            String clientId,
            //String clientSecret,
            SSLContext sslContext) {

        basicAuthentication = new BasicAuthentication(username, password);

        ResteasyClientBuilderImpl clientBuilder = new ResteasyClientBuilderImpl().connectionPoolSize(20);
        clientBuilder.connectionPoolSize(20);
        clientBuilder.sslContext(sslContext);

        // We need to register the necessary providers manually in case this is used in Jenkins
        // where the hierarchical classloader structure prevents provider lookup via ServiceLoader
        clientBuilder.register(new FormUrlEncodedProvider());
        clientBuilder.register(new CustomResteasyJackson2Provider());

        keycloak = KeycloakBuilder.builder()
                .serverUrl(keycloakBaseUrl)
                .realm(keycloakRealm)
                .username(username)
                .password(password)
                .clientId(clientId)
                //				.clientSecret(clientSecret)
                .resteasyClient(clientBuilder.build())
                .build();
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        try {
            requestContext.getHeaders().add(HttpHeaders.AUTHORIZATION, BEARER_SCHEME_WITH_SPACE.concat(getAccessToken()));
            if (showAuthMethod) {
                LOG.infov("Authentication with OIDC token");
                showAuthMethod = false;
            }
        } catch (Exception ex) {
            if (showAuthMethod) {
                LOG.infov("Using Basic authentication as OIDC server replied with {0}", ex.getMessage());
                showAuthMethod = false;
            }
            basicAuthentication.filter(requestContext);
        }
    }

    private String getAccessToken() {
        return keycloak.tokenManager().getAccessTokenString();
    }
}
