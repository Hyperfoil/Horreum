package io.hyperfoil.tools.horreum.test;

import io.quarkus.logging.Log;
import io.quarkus.test.keycloak.server.KeycloakTestResourceLifecycleManager;
import jakarta.ws.rs.client.ClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleScopeResource;

import java.util.Map;

/**
 * Extends {@link KeycloakTestResourceLifecycleManager} so that the service client has <pre>realm-admin</pre> role to be able to manage users in the realm
 */
public class HorreumKeycloakTestResourceLifecycleManager extends KeycloakTestResourceLifecycleManager {

    private static final String KEYCLOAK_REALM = System.getProperty("keycloak.realm", "quarkus");
    private static final String KEYCLOAK_SERVICE_CLIENT = System.getProperty("keycloak.service.client", "quarkus-service-app");

    @Override public Map<String, String> start() {
        Map<String, String> properties = super.start();

        try (Keycloak keycloak = KeycloakBuilder.builder()
                                                .serverUrl(properties.get("keycloak.url"))
                                                .realm("master")
                                                .username("admin")
                                                .password("admin")
                                                .clientId("admin-cli")
                                                .resteasyClient(((ResteasyClientBuilder) ClientBuilder.newBuilder()).disableTrustManager().build())
                                                .build()) {

            // the client created in KeycloakTestResourceLifecycleManager (the keycloak dev service) lacks the "realm-admin" role on its service account
            // without it, it has no permission to add / remove users and roles from the realm
            // (on production instances these permission may be fine-tuned, as "realm-admin" is a composite role)

            RealmResource realmResource = keycloak.realm(KEYCLOAK_REALM);
            String clientId = realmResource.clients().findByClientId(KEYCLOAK_SERVICE_CLIENT).get(0).getId();
            String managementId = realmResource.clients().findByClientId("realm-management").get(0).getId();
            String serviceAccountId = realmResource.clients().get(clientId).getServiceAccountUser().getId();

            RoleScopeResource managementRolesResource = realmResource.users().get(serviceAccountId).roles().clientLevel(managementId);
            managementRolesResource.add(managementRolesResource.listAvailable().stream().filter(r -> "realm-admin".equals(r.getName())).toList());

            Log.infov("realm-admin role added to {0} client", KEYCLOAK_SERVICE_CLIENT);
        }

        return properties;
    }
}
