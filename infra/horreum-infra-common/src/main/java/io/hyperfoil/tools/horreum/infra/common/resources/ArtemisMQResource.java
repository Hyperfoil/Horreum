package io.hyperfoil.tools.horreum.infra.common.resources;

import static io.hyperfoil.tools.horreum.infra.common.Const.*;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.MountableFile;

import io.hyperfoil.tools.horreum.infra.common.Const;
import io.hyperfoil.tools.horreum.infra.common.ResourceLifecycleManager;

public class ArtemisMQResource implements ResourceLifecycleManager {
    private GenericContainer<?> amqpContainer;
    private boolean inContainer = false;
    private String networkAlias = "";

    @Override
    public void init(Map<String, String> initArgs) {
        if (initArgs.containsKey(HORREUM_DEV_AMQP_ENABLED) && initArgs.get(HORREUM_DEV_AMQP_ENABLED).equals("true")) {
            if (!initArgs.containsKey(HORREUM_DEV_AMQP_IMAGE)) {
                throw new RuntimeException("Arguments did not contain AMQP image.");
            }
            final String AMQP_IMAGE = initArgs.get(Const.HORREUM_DEV_AMQP_IMAGE);
            inContainer = initArgs.containsKey("inContainer") && initArgs.get("inContainer").equals("true");
            networkAlias = initArgs.get(HORREUM_DEV_AMQP_NETWORK_ALIAS);

            amqpContainer = new GenericContainer<>(AMQP_IMAGE);
            amqpContainer.withEnv("ARTEMIS_USER", initArgs.get("amqp-username"))
                    .withEnv("ARTEMIS_PASSWORD", initArgs.get("amqp-password"))
                    .withEnv("AMQ_ROLE", "admin")
                    .withEnv("EXTRA_ARGS",
                            " --role admin --name broker --allow-anonymous --force --no-autotune --mapped --no-fsync  --relax-jolokia ")
                    .withExposedPorts(5672)
                    .withCopyFileToContainer(MountableFile.forClasspathResource("broker.xml"),
                            "/var/lib/artemis-instance/etc-override/broker.xml");
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

        return Map.of(HORREUM_DEV_AMQP_MAPPED_PORT, mappedPort,
                HORREUM_DEV_AMQP_MAPPED_HOST, host);
    }

    @Override
    public void stop() {
        if (amqpContainer != null) {
            amqpContainer.stop();
        }
    }
}
