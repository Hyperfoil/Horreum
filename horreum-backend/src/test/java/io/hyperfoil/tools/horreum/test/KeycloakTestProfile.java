package io.hyperfoil.tools.horreum.test;

import static java.lang.System.getProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.hyperfoil.tools.horreum.svc.Roles;
import io.quarkus.keycloak.admin.client.common.KeycloakAdminClientConfig;

public class KeycloakTestProfile extends HorreumTestProfile {

    public static final String CLIENT = "horreum-client", REALM = "horreum";

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> configOverrides = new HashMap<>(super.getConfigOverrides());
        configOverrides.put("horreum.roles.provider", "keycloak");

        configOverrides.put("quarkus.oidc.auth-server-url", "${keycloak.url}/realms/" + REALM);
        configOverrides.put("quarkus.keycloak.admin-client.server-url", "${keycloak.url}");
        configOverrides.put("quarkus.keycloak.admin-client.client-id", CLIENT);
        configOverrides.put("quarkus.keycloak.admin-client.realm", REALM);
        configOverrides.put("quarkus.keycloak.admin-client.client-secret", "secret");
        configOverrides.put("quarkus.keycloak.admin-client.grant-type",
                KeycloakAdminClientConfig.GrantType.CLIENT_CREDENTIALS.asString());

        configOverrides.put("keycloak.docker.image", getProperty("horreum.dev-services.keycloak.image"));
        configOverrides.put("keycloak.use.https", "false");
        configOverrides.put("keycloak.service.client", CLIENT);
        configOverrides.put("keycloak.realm", REALM);

        // create the base roles used to compose team roles
        configOverrides.put("keycloak.token.admin-roles",
                String.join(",", Roles.MANAGER, Roles.TESTER, Roles.VIEWER, Roles.UPLOADER, Roles.MACHINE));
        return configOverrides;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(PostgresResource.class),
                new TestResourceEntry(HorreumKeycloakTestResourceLifecycleManager.class));
    }
}
