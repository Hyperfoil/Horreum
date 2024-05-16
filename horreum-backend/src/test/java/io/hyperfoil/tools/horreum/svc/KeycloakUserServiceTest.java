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
}
