package io.hyperfoil.tools.horreum.infra.common.resources;

import io.hyperfoil.tools.horreum.infra.common.ResourceLifecycleManager;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static io.hyperfoil.tools.horreum.infra.common.Const.*;

public class KeycloakResource implements ResourceLifecycleManager {
    private GenericContainer<?> keycloakContainer;

    private String networkAlias = "";
    private Integer containerPort;

    @Override
    public void init(Map<String, String> initArgs) {

        boolean startDevService = !initArgs.containsKey(HORREUM_DEV_KEYCLOAK_ENABLED) || (initArgs.containsKey(HORREUM_DEV_KEYCLOAK_ENABLED) && Boolean.parseBoolean(initArgs.get(HORREUM_DEV_KEYCLOAK_ENABLED)));
        if (startDevService) {

            if (!initArgs.containsKey("quarkus.datasource.jdbc.url")) {
                throw new RuntimeException("Arguments did not contain jdbc URL");
            }

            if (!initArgs.containsKey(HORREUM_DEV_KEYCLOAK_IMAGE)) {
                throw new RuntimeException("Arguments did not contain Keycloak image");
            }

            final String JDBC_URL = initArgs.get("quarkus.datasource.jdbc.url.internal");
            final String DB_PORT = initArgs.get("postgres.container.port");

            final String KEYCLOAK_IMAGE = initArgs.get(HORREUM_DEV_KEYCLOAK_IMAGE);

            networkAlias = initArgs.get(HORREUM_DEV_KEYCLOAK_NETWORK_ALIAS);
            String port = initArgs.get(HORREUM_DEV_KEYCLOAK_CONTAINER_PORT);
            containerPort = port != null ? Integer.parseInt(port) : null;

            //TODO: better way of detecting we are using a backup
            if (!initArgs.containsKey(HORREUM_DEV_POSTGRES_BACKUP)) {

                File tempKeycloakRealmFile = null;

                try (InputStream is = KeycloakResource.class.getClassLoader().getResourceAsStream("keycloak-horreum.json")) {
                    tempKeycloakRealmFile = File.createTempFile("horreum-dev-keycloak-", ".json");
                    tempKeycloakRealmFile.deleteOnExit();
                    Files.copy(is, tempKeycloakRealmFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException("Could not extract Horreum Keycloak realm definition", e);
                }

                if (tempKeycloakRealmFile == null) {
                    throw new RuntimeException("Failed to load keycloak realm configuration");
                }

                ImageFromDockerfile imageFromDockerfile = new ImageFromDockerfile().withDockerfileFromBuilder(builder ->
                        builder.from(KEYCLOAK_IMAGE)
                                .copy("/tmp/keycloak-horreum.json", "/opt/keycloak/data/import/")
                                .env("KEYCLOAK_ADMIN", "admin")
                                .env("KEYCLOAK_ADMIN_PASSWORD", "secret")
                                .env("KC_CACHE", "local")
                                .env("KC_DB", "postgres")
                                .env("KC_DB_USERNAME", "keycloak")
                                .env("KC_DB_PASSWORD", initArgs.get(HORREUM_DEV_KEYCLOAK_DB_PASSWORD))
                                .env("KC_HTTP_ENABLED", "true")
                                .env("KC_HOSTNAME_STRICT", "false")
                                .env("DB_DATABASE", "keycloak")
                                .env("KC_DB_URL", JDBC_URL)
                                .run("/opt/keycloak/bin/kc.sh build")
                                .entryPoint("/opt/keycloak/bin/kc.sh ${KEYCLOAK_COMMAND:-start-dev} --import-realm $EXTRA_OPTIONS")
                                .build()).withFileFromFile("/tmp/keycloak-horreum.json", tempKeycloakRealmFile);

                keycloakContainer = (containerPort == null ) ?
                        new GenericContainer<>().withExposedPorts(8080) :
                        new FixedHostPortGenericContainer<>("were-going-to-override-this").withFixedExposedPort(containerPort, 8080);

                keycloakContainer.setImage(imageFromDockerfile);

            } else {

                keycloakContainer = (containerPort == null ) ?
                        new GenericContainer<>(KEYCLOAK_IMAGE).withExposedPorts(8080) :
                        new FixedHostPortGenericContainer<>(KEYCLOAK_IMAGE).withFixedExposedPort(containerPort, 8080);

                keycloakContainer.withEnv("KC_HTTP_PORT", "8080")
                        .withEnv("JAVA_OPTS", "-Xms1024m -Xmx1024m -XX:MetaspaceSize=96M -XX:MaxMetaspaceSize=256m -Djava.net.preferIPv4Stack=true -Djava.awt.headless=true")
                        .withEnv("KC_LOG_LEVEL", "info")
                        .withEnv("KC_DB", "postgres")
                        .withEnv("KC_HTTP_ENABLED", "true")
                        .withEnv("KC_DB_USERNAME", "keycloak")
                        .withEnv("KC_DB_PASSWORD", initArgs.get(HORREUM_DEV_KEYCLOAK_DB_PASSWORD))
                        .withEnv("KC_DB_URL_HOST", "")
                        .withEnv("KC_HOSTNAME_STRICT", "false")
                        .withEnv("KC_HOSTNAME", "localhost")
                        .withEnv("KC_DB_URL", "jdbc:postgresql://172.17.0.1:" + DB_PORT + "/keycloak")
                        .withCommand("-Dquarkus.http.http2=false", "start-dev");
            }

            keycloakContainer.waitingFor(Wait.defaultWaitStrategy().withStartupTimeout(Duration.ofSeconds(120)));

        }

    }

    @Override
    public Map<String, String> start(Optional<Network> network) {
        if (keycloakContainer == null) {
            return Collections.emptyMap();
        }

        if (network.isPresent()) {
            keycloakContainer.withNetwork(network.get());
            keycloakContainer.withNetworkAliases(networkAlias);
        }
        keycloakContainer.start();

        String mappedPort = keycloakContainer.getMappedPort(8080).toString();
        String ipAddr = "localhost";
        String keycloakHost = String.format("http://%s:%s", ipAddr, mappedPort);
        String keycloakBase = String.format("%s/auth", keycloakHost);
        String oidcAuthServerUrl = keycloakBase.concat("/realms/horreum");

        return Map.of(
                "keycloak.host", keycloakHost,
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

    public GenericContainer getContainer() {
        return this.keycloakContainer;
    }

}
