package io.hyperfoil.tools.horreum.test;

import io.hyperfoil.tools.horreum.svc.Roles;
import io.quarkus.keycloak.admin.client.common.KeycloakAdminClientConfig;
import io.quarkus.test.keycloak.server.KeycloakTestResourceLifecycleManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.System.getProperty;

public class KeycloakTestProfile extends HorreumTestProfile {

    @Override public Map<String, String> getConfigOverrides() {
        Map<String, String> configOverrides = new HashMap<>(super.getConfigOverrides());
        configOverrides.put("horreum.roles.provider", "keycloak");

        configOverrides.put("quarkus.oidc.auth-server-url", "${keycloak.url}/realms/horreum/");
        configOverrides.put("quarkus.keycloak.admin-client.server-url", "${keycloak.url}");
        configOverrides.put("quarkus.keycloak.admin-client.client-id", "admin-cli");
        configOverrides.put("quarkus.keycloak.admin-client.realm", "master");
        configOverrides.put("quarkus.keycloak.admin-client.grant-type", KeycloakAdminClientConfig.GrantType.PASSWORD.asString());

        configOverrides.put("keycloak.docker.image", getProperty("horreum.dev-services.keycloak.image"));
        configOverrides.put("keycloak.use.https", "false");
        configOverrides.put("keycloak.realm", "horreum");

        // create the base roles used to compose team roles
        configOverrides.put("keycloak.token.admin-roles", String.join(",", Roles.ADMIN, Roles.MANAGER, Roles.TESTER, Roles.VIEWER, Roles.UPLOADER));
        return configOverrides;
    }

    @Override public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(PostgresResource.class), new TestResourceEntry(KeycloakTestResourceLifecycleManager.class));
    }
}
