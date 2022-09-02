package io.hyperfoil.tools.auth;

import io.hyperfoil.tools.CustomResteasyJackson2Provider;
import org.apache.http.conn.HttpHostConnectException;
import org.jboss.logging.Logger;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.jboss.resteasy.plugins.providers.FormUrlEncodedProvider;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

import javax.net.ssl.SSLContext;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

public class KeycloakClientRequestFilter implements ClientRequestFilter {

	private static final Logger LOG = Logger.getLogger(KeycloakClientRequestFilter.class);
	private static final String BEARER_SCHEME_WITH_SPACE = "Bearer ";

	Keycloak keycloak;

	public KeycloakClientRequestFilter(String keycloakBaseUrl,
			String keycloakRealm,
			String username,
			String password,
			String clientId,
			String clientSecret,
			SSLContext sslContext) {

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
				.clientSecret(clientSecret)
				.resteasyClient(clientBuilder.build())
				.build();
	}

	@Override
	public void filter(ClientRequestContext requestContext) {
		try {
			final String accessToken = getAccessToken();
			requestContext.getHeaders().add(HttpHeaders.AUTHORIZATION, BEARER_SCHEME_WITH_SPACE + accessToken);
		} catch (Exception ex) {
			LOG.warnf(ex, "Access token is not available", ex);
			// TODO: 401 should be only on 401 response
			if (ex.getCause() instanceof HttpHostConnectException) {
				LOG.warnf("Aborting the request with HTTP 500 error");
				requestContext.abortWith(Response.status(500).build());
			} else {
				LOG.warnf("Aborting the request with HTTP 401 error");
				requestContext.abortWith(Response.status(401).build());
			}
		}
	}

	private String getAccessToken() {
		return keycloak.tokenManager().getAccessToken().getToken();
	}
}
