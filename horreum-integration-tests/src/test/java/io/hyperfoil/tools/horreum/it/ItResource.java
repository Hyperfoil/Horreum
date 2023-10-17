package io.hyperfoil.tools.horreum.it;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.jboss.logging.Logger;

import java.util.Map;

import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_KC_ADMIN_PASSWORD;
import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_KC_ADMIN_USERNAME;
import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_KC_DB_PASSWORD;
import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_KC_DB_USERNAME;
import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_KEYCLOAK_NETWORK_ALIAS;
import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_POSTGRES_NETWORK_ALIAS;
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_KEYCLOAK_ADMIN_PASSWORD;
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_KEYCLOAK_ADMIN_USERNAME;
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_KEYCLOAK_DB_PASSWORD;
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_KEYCLOAK_DB_USERNAME;
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_KEYCLOAK_IMAGE;
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_KEYCLOAK_NETWORK_ALIAS;
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_POSTGRES_IMAGE;
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_POSTGRES_NETWORK_ALIAS;
import static io.hyperfoil.tools.horreum.infra.common.HorreumResources.startContainers;
import static io.hyperfoil.tools.horreum.infra.common.HorreumResources.stopContainers;
import static java.lang.System.getProperty;

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

                    String keycloakImage = getProperty(HORREUM_DEV_KEYCLOAK_IMAGE);
                    String postgresImage = getProperty(HORREUM_DEV_POSTGRES_IMAGE);

                    if ( keycloakImage == null || postgresImage == null ){
                        throw new RuntimeException("Test container images are not defined");
                    }

                    //todo: pick up from configuration
                    Map<String, String> containerArgs = Map.of(
                            HORREUM_DEV_KEYCLOAK_IMAGE, keycloakImage,
                            HORREUM_DEV_KEYCLOAK_NETWORK_ALIAS, DEFAULT_KEYCLOAK_NETWORK_ALIAS,
                            HORREUM_DEV_POSTGRES_IMAGE, postgresImage,
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
