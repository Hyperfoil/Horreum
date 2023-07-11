package io.hyperfoil.tools.horreum.it;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.jboss.logging.Logger;

import java.util.Map;

import static io.hyperfoil.tools.horreum.infra.common.Const.*;
import static io.hyperfoil.tools.horreum.infra.common.HorreumResources.startContainers;
import static io.hyperfoil.tools.horreum.infra.common.HorreumResources.stopContainers;

public class ItResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger log = Logger.getLogger(ItResource.class);
    private static boolean started = false;


    @Override
    public Map<String, String> start() {
        synchronized (ItResource.class) {
            if (!started) {
                log.info("Starting Horreum IT resources");
                started = true;
                try {

                    //todo: pick up from configuration
                    Map<String, String> containerArgs = Map.of(
                            HORREUM_DEV_KEYCLOAK_IMAGE, "quay.io/keycloak/keycloak:21.1",
                            HORREUM_DEV_KEYCLOAK_NETWORK_ALIAS, "horreum-dev-postgres",
                            HORREUM_DEV_POSTGRES_IMAGE, "postgres:13",
                            HORREUM_DEV_POSTGRES_NETWORK_ALIAS, "horreum-dev-keycloak"
                    );
                    return startContainers(containerArgs);
                } catch (Exception e){
                    log.fatal("Could not start Horreum services", e);
                    stopContainers();
                    throw e;
                }
            }
        }
        return null;
    }

    @Override
    public void stop() {
        synchronized (ItResource.class) {
            try {
                log.info("Stopping Horreum IT resources");
                stopContainers();
                started = false;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }



}
