package io.hyperfoil.tools.auth;

import org.apache.http.conn.HttpHostConnectException;
import org.jboss.logging.Logger;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

public class KeycloakClientRequestFilter implements ClientRequestFilter {

	private static final Logger LOG = Logger.getLogger(KeycloakClientRequestFilter.class);
	private static final String BEARER_SCHEME_WITH_SPACE = "Bearer ";

	Keycloak keycloak;

	String clientName = "horreum";

	public KeycloakClientRequestFilter(String keycloakBaseUrl,
			String keycloakRealm,
			String username,
			String password,
			String clientId,
			String clientSecret) {

		keycloak = KeycloakBuilder.builder()
				.serverUrl(keycloakBaseUrl + "/auth")
				.realm(keycloakRealm)
				.username(username)
				.password(password)
				.clientId(clientId)
				.clientSecret(clientSecret)
				.resteasyClient(new ResteasyClientBuilder().connectionPoolSize(20).build())
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
