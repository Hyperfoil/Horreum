package io.hyperfoil.tools.horreum.it;

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
                            HORREUM_DEV_KEYCLOAK_IMAGE, DEFAULT_KEYCLOAK_IMAGE,
                            HORREUM_DEV_KEYCLOAK_NETWORK_ALIAS, DEFAULT_KEYCLOAK_NETWORK_ALIAS,
                            HORREUM_DEV_POSTGRES_IMAGE, DEFAULT_POSTGRES_IMAGE,
                            HORREUM_DEV_POSTGRES_NETWORK_ALIAS, DEFAULT_POSTGRES_NETWORK_ALIAS,
                            HORREUM_DEV_KEYCLOAK_DB_USERNAME, DEFAULT_KC_DB_USERNAME,
                            HORREUM_DEV_KEYCLOAK_DB_PASSWORD, DEFAULT_KC_DB_PASSWORD,
                            HORREUM_DEV_KEYCLOAK_ADMIN_USERNAME, DEFAULT_KC_ADMIN_USERNAME,
                            HORREUM_DEV_KEYCLOAK_ADMIN_PASSWORD, DEFAULT_KC_ADMIN_PASSWORD

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
