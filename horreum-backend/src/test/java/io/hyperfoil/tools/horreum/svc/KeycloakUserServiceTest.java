package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.test.KeycloakTestProfile;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.ws.rs.client.ClientBuilder;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleScopeResource;

@TestProfile(KeycloakTestProfile.class)
@QuarkusTest public class KeycloakUserServiceTest extends UserServiceAbstractTest {

    @BeforeAll static void setupClient() {
        try (Keycloak keycloak = KeycloakBuilder.builder()
                                                .serverUrl(ConfigProvider.getConfig().getValue("quarkus.keycloak.admin-client.server-url", String.class))
                                                .realm("master")
                                                .username("admin")
                                                .password("admin")
                                                .clientId("admin-cli")
                                                .resteasyClient(((ResteasyClientBuilder) ClientBuilder.newBuilder()).disableTrustManager().build())
                                                .build()) {

            // the client created in KeycloakTestResourceLifecycleManager (the keycloak dev service) lacks the "realm-admin" role on its service account
            // without it, it has no permission to add / remove users and roles from the realm
            // (on production instances these permission may be fine-tuned, as "realm-admin" is a composite role)

            RealmResource realmResource = keycloak.realm(KeycloakTestProfile.REALM);
            String clientId = realmResource.clients().findByClientId(KeycloakTestProfile.CLIENT).get(0).getId();
            String managementId = realmResource.clients().findByClientId("realm-management").get(0).getId();
            String serviceAccountId = realmResource.clients().get(clientId).getServiceAccountUser().getId();

            RoleScopeResource managementRolesResource = realmResource.users().get(serviceAccountId).roles().clientLevel(managementId);
            managementRolesResource.add(managementRolesResource.listAvailable().stream().filter(r -> "realm-admin".equals(r.getName())).toList());

            Log.infov("realm-admin role added to {0} client", KeycloakTestProfile.CLIENT);
        }
    }
}
