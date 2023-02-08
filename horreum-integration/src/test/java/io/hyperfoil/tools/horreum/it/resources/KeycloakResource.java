package io.hyperfoil.tools.horreum.it.resources;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.fail;

public class KeycloakResource implements QuarkusTestResourceLifecycleManager {
    public GenericContainer<?> keycloakContainer;

    public static final String KEYCLOAK_VERSION = "quay.io/keycloak/keycloak:20.0.1";

    @Override
    public void init(Map<String, String> initArgs) {

        URL keycloakConfig = KeycloakResource.class.getClassLoader().getResource("keycloak/keycloak-horreum.json");

        if ( keycloakConfig == null ){
            fail("Failed to load keycloak realm configuration");
        }

        if ( !initArgs.containsKey("quarkus.datasource.jdbc.url") ) {
            fail("Arguments did not contain jdbc URL");
        }
        String JDBC_URL = initArgs.get("quarkus.datasource.jdbc.url");

        keycloakContainer = new GenericContainer<>(
                new ImageFromDockerfile().withDockerfileFromBuilder(builder ->
                                builder
                                        .from(KEYCLOAK_VERSION)
                                        .copy("/tmp/keycloak-horreum.json","/opt/keycloak/data/import/")
                                        .env("KEYCLOAK_ADMIN", "admin")
                                        .env("KEYCLOAK_ADMIN_PASSWORD", "secret")
                                        .env("KC_CACHE", "local")
                                        .env("KC_DB", "postgres")
                                        .env("KC_DB_USERNAME", "keycloak")
                                        .env("KC_DB_PASSWORD", "secret")
                                        .env("KC_HTTP_ENABLED", "true")
                                        .env("KC_HOSTNAME_STRICT", "false")
//                                 .env("DB_PORT", "5432")
                                        .env("DB_DATABASE", "keycloak")
                                        .env("KC_DB_URL",  JDBC_URL)
                                        .run("/opt/keycloak/bin/kc.sh build")
                                        .entryPoint("/opt/keycloak/bin/kc.sh ${KEYCLOAK_COMMAND:-start-dev} --import-realm $EXTRA_OPTIONS")
                                        .build())
                        .withFileFromFile("/tmp/keycloak-horreum.json", new File(keycloakConfig.getPath()))
        ).withExposedPorts(8080);


//               .withDatabaseName("horreum").withUsername("dbadmin").withPassword("secret");
    }

    @Override
    public Map<String, String> start() {
        if (keycloakContainer == null) {
            return Collections.emptyMap();
        }
        keycloakContainer.start();

        String mappedPort = keycloakContainer.getMappedPort(8080).toString();
        //TODO:: correctly configure networking
        String keycloakHost=String.format("http://%s:%s", "172.17.0.1", mappedPort);
        String keycloakBase=String.format("%s/auth", keycloakHost);
        String oidcAuthServerUrl = keycloakBase.concat("/realms/horreum");

        return Map.of(
                "keycloak.host", keycloakHost,
                "quarkus.oidc.auth.server.url", oidcAuthServerUrl,
                "keycloak.admin.url", keycloakHost.concat("/realms/master/protocol/openid-connect/token"),
                "quarkus.oidc.auth-server-url", oidcAuthServerUrl
        );
    }

    @Override
    public void stop() {
        if (keycloakContainer != null) {
            keycloakContainer.stop();
        }
    }
}
