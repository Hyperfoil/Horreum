package io.hyperfoil.tools.horreum.infra.common.resources;

import static io.hyperfoil.tools.horreum.infra.common.Const.*;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import io.hyperfoil.tools.horreum.infra.common.HorreumResources;
import io.hyperfoil.tools.horreum.infra.common.ResourceLifecycleManager;

public class HorreumResource implements ResourceLifecycleManager {

    private static GenericContainer<?> horreumContainer;
    private boolean inContainer = false;

    private String networkAlias = "";

    public void init(Map<String, String> initArgs) {
        if (!initArgs.containsKey(HORREUM_DEV_HORREUM_HORREUM_IMAGE)) {
            throw new RuntimeException("Horreum dev image argument not configured");
        }

        final String HORREUM_IMAGE = initArgs.get(HORREUM_DEV_HORREUM_HORREUM_IMAGE);

        inContainer = initArgs.containsKey("inContainer") && initArgs.get("inContainer").equals("true");
        networkAlias = initArgs.get(HORREUM_DEV_HORREUM_NETWORK_ALIAS);
        Network network = HorreumResources.getNetwork();

        horreumContainer = (initArgs.containsKey(HORREUM_DEV_HORREUM_CONTAINER_PORT))
                ? new FixedHostPortGenericContainer<>(HORREUM_IMAGE)
                        .withFixedExposedPort(Integer.parseInt(initArgs.get(HORREUM_DEV_HORREUM_CONTAINER_PORT)), 8080)
                : new GenericContainer<>(HORREUM_IMAGE).withExposedPorts(8080);
        String keycloakHostUrl = initArgs.get("keycloak.host");
        String jdbcUrl = initArgs.get("quarkus.datasource.jdbc.url");
        if (inContainer) {
            String keycloakNetworkAlias = initArgs.get(HORREUM_DEV_KEYCLOAK_NETWORK_ALIAS);
            keycloakHostUrl = "http://".concat(keycloakNetworkAlias).concat(":8080/");
            String postgresNetworkAlias = initArgs.get(HORREUM_DEV_POSTGRES_NETWORK_ALIAS);
            jdbcUrl = "jdbc:postgresql://".concat(postgresNetworkAlias).concat(":5432/horreum?loggerLevel=OFF");
        }

        String keycloakUrl = String.format("%s/realms/horreum", keycloakHostUrl);
        String horreumUrl = "http://" + networkAlias + ":8081";

        horreumContainer
                .withEnv("horreum.keycloak.url", keycloakUrl)
                .withEnv("quarkus.oidc.auth-server-url", keycloakUrl)
                .withEnv("quarkus.keycloak.admin-client.server-url", keycloakHostUrl)
                .withEnv("quarkus.keycloak.admin-client.client-id", "horreum")
                .withEnv("quarkus.keycloak.admin-client.realm", "horreum")
                .withEnv("quarkus.keycloak.admin-client.client-secret", "**********")
                .withEnv("quarkus.keycloak.admin-client.grant-type", "CLIENT_CREDENTIALS")
                .withEnv("quarkus.oidc.token.issuer", "https://server.example.com ")
                .withEnv("smallrye.jwt.sign.key.location", "/privateKey.jwk")
                .withEnv("horreum.url", horreumUrl)
                .withEnv("horreum.test-mode", "true")
                .withEnv("horreum.privacy", "/path/to/privacy/statement/link")
                .withEnv("quarkus.datasource.migration.devservices.enabled", "false")
                .withEnv("quarkus.datasource.jdbc.url", jdbcUrl)
                .withEnv("quarkus.datasource.migration.jdbc.url", jdbcUrl)
                .withEnv("quarkus.datasource.jdbc.additional-jdbc-properties.sslmode", "require")
                .withEnv("amqp-host", initArgs.get("amqp.host"))
                .withEnv("amqp-port", initArgs.get("amqp.mapped.port"))
                .withEnv("amqp-username", initArgs.get("amqp-username"))
                .withEnv("amqp-password", initArgs.get("amqp-password"))
                .withEnv("amqp-reconnect-attempts", "100")
                .withEnv("amqp-reconnect-interval", "1000")
                .withEnv("quarkus.profile", "test")
                .withEnv("quarkus.test.profile", "test")
                .withEnv("horreum.bootstrap.password", "secret")
                .withEnv("horreum.roles.provider", "database")
                .withNetwork(network)
                .withNetworkAliases(networkAlias)
                .withCommand("/deployments/horreum.sh ");
    }

    @Override
    public Map<String, String> start(Optional<Network> network) {
        if (horreumContainer == null) {
            return Collections.emptyMap();
        }
        if (!network.isPresent()) {
            horreumContainer.withNetwork(network.get());
            horreumContainer.withNetworkAliases(networkAlias);
        }

        horreumContainer.start();
        String horreumContainerName = horreumContainer.getContainerName().replaceAll("/", "");
        horreumContainerName = inContainer ? networkAlias : horreumContainerName;
        Integer port = horreumContainer.getMappedPort(8080);

        return Map.of("horreum.container.name", horreumContainerName,
                "horreum.container.port", port.toString());
    }

    @Override
    public void stop() {
        if (horreumContainer != null) {
            horreumContainer.stop();
        }
    }
}
