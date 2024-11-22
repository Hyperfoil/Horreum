package io.hyperfoil.tools.horreum.infra.common.resources;

import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_AMQP_NETWORK_ALIAS;
import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_AMQP_PASSWORD;
import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_AMQP_USERNAME;
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_AMQP_ENABLED;
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_AMQP_IMAGE;
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_AMQP_NETWORK_ALIAS;
import static java.lang.Boolean.parseBoolean;
import static java.time.Duration.ofSeconds;
import static org.testcontainers.containers.wait.strategy.Wait.defaultWaitStrategy;
import static org.testcontainers.utility.MountableFile.forClasspathResource;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import io.hyperfoil.tools.horreum.infra.common.ResourceLifecycleManager;

public class ArtemisMQResource implements ResourceLifecycleManager {

    private static final String BROKER_XML_CONTAINER_PATH = "/var/lib/artemis-instance/etc-override/broker.xml";

    private GenericContainer<?> amqpContainer;
    private boolean inContainer;
    private String networkAlias;

    @Override
    public void init(Map<String, String> initArgs) {
        if (initArgs.containsKey(HORREUM_DEV_AMQP_ENABLED) && parseBoolean(initArgs.get(HORREUM_DEV_AMQP_ENABLED))) {
            if (!initArgs.containsKey(HORREUM_DEV_AMQP_IMAGE)) {
                throw new RuntimeException("Arguments did not contain AMQP image.");
            }
            inContainer = initArgs.containsKey("inContainer") && parseBoolean(initArgs.get("inContainer"));
            networkAlias = initArgs.getOrDefault(HORREUM_DEV_AMQP_NETWORK_ALIAS, DEFAULT_AMQP_NETWORK_ALIAS);

            amqpContainer = new GenericContainer<>(initArgs.get(HORREUM_DEV_AMQP_IMAGE));
            amqpContainer.waitingFor(defaultWaitStrategy().withStartupTimeout(ofSeconds(120)))
                    .withEnv("AMQ_USER", initArgs.getOrDefault("amqp-username", DEFAULT_AMQP_USERNAME))
                    .withEnv("AMQ_PASSWORD", initArgs.getOrDefault("amqp-password", DEFAULT_AMQP_PASSWORD))
                    .withEnv("AMQ_ROLE", "admin")
                    .withEnv("EXTRA_ARGS",
                            " --role admin --name broker --allow-anonymous --force --no-autotune --mapped --no-fsync  --relax-jolokia ")
                    .withExposedPorts(5672)
                    .withCopyFileToContainer(forClasspathResource("broker.xml"), BROKER_XML_CONTAINER_PATH);
        }
    }

    @Override
    public Map<String, String> start(Optional<Network> network) {
        if (amqpContainer == null) {
            return Collections.emptyMap();
        }
        if (network.isPresent()) {
            amqpContainer.withNetwork(network.get());
            amqpContainer.withNetworkAliases(networkAlias);
        }
        amqpContainer.start();
        String mappedPort = amqpContainer.getMappedPort(5672).toString();
        String host = inContainer ? networkAlias : "localhost";

        return Map.of("artemis.container.name", host, "artemis.container.port", mappedPort);
    }

    @Override
    public void stop() {
        if (amqpContainer != null) {
            amqpContainer.stop();
        }
    }
}
