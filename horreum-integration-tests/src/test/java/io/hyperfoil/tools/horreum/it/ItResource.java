package io.hyperfoil.tools.horreum.it;

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
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_KEYCLOAK_ENABLED;
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_KEYCLOAK_IMAGE;
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_KEYCLOAK_NETWORK_ALIAS;
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_POSTGRES_ENABLED;
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_POSTGRES_IMAGE;
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_POSTGRES_NETWORK_ALIAS;
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_POSTGRES_SSL_CERTIFICATE;
import static io.hyperfoil.tools.horreum.infra.common.Const.HORREUM_DEV_POSTGRES_SSL_CERTIFICATE_KEY;
import static io.hyperfoil.tools.horreum.infra.common.HorreumResources.startContainers;
import static io.hyperfoil.tools.horreum.infra.common.HorreumResources.stopContainers;
import static java.lang.System.getProperty;

import java.util.Map;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import io.hyperfoil.tools.horreum.infra.common.SelfSignedCert;
import io.quarkus.logging.Log;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class ItResource implements QuarkusTestResourceLifecycleManager {

    private static boolean started;

    public static String HORREUM_BOOTSTRAP_PASSWORD = "horreum.secret";

    @Override
    public Map<String, String> start() {
        synchronized (ItResource.class) {
            if (!started) {
                Log.info("Starting Horreum IT resources");
                started = true;
                try {
                    String keycloakImage = getProperty(HORREUM_DEV_KEYCLOAK_IMAGE);
                    String postgresImage = getProperty(HORREUM_DEV_POSTGRES_IMAGE);

                    if (keycloakImage == null || postgresImage == null) {
                        throw new RuntimeException("Test container images are not defined");
                    }

                    SelfSignedCert postgresSelfSignedCert = new SelfSignedCert("RSA", "SHA256withRSA", "localhost", 123);

                    Config config = ConfigProvider.getConfig();

                    //todo: pick up from configuration
                    Map<String, String> containerArgs = Map.ofEntries(
                            Map.entry(HORREUM_DEV_KEYCLOAK_ENABLED, "true"),
                            Map.entry(HORREUM_DEV_KEYCLOAK_IMAGE, keycloakImage),
                            Map.entry(HORREUM_DEV_KEYCLOAK_NETWORK_ALIAS, DEFAULT_KEYCLOAK_NETWORK_ALIAS),
                            Map.entry(HORREUM_DEV_POSTGRES_ENABLED, "true"),
                            Map.entry(HORREUM_DEV_POSTGRES_IMAGE, postgresImage),
                            Map.entry(HORREUM_DEV_POSTGRES_NETWORK_ALIAS, DEFAULT_POSTGRES_NETWORK_ALIAS),
                            Map.entry(HORREUM_DEV_POSTGRES_SSL_CERTIFICATE, postgresSelfSignedCert.getCertString()),
                            Map.entry(HORREUM_DEV_POSTGRES_SSL_CERTIFICATE_KEY, postgresSelfSignedCert.getKeyString()),
                            Map.entry(HORREUM_DEV_KEYCLOAK_DB_USERNAME, DEFAULT_KC_DB_USERNAME),
                            Map.entry(HORREUM_DEV_KEYCLOAK_DB_PASSWORD, DEFAULT_KC_DB_PASSWORD),
                            Map.entry(HORREUM_DEV_KEYCLOAK_ADMIN_USERNAME, DEFAULT_KC_ADMIN_USERNAME),
                            Map.entry(HORREUM_DEV_KEYCLOAK_ADMIN_PASSWORD, DEFAULT_KC_ADMIN_PASSWORD),
                            Map.entry("horreum.bootstrap.password", HORREUM_BOOTSTRAP_PASSWORD), // well known bootstrap password instead of a random one
                            Map.entry("quarkus.http.port",
                                    config.getOptionalValue("quarkus.http.port", String.class).orElse("8080")),
                            Map.entry("quarkus.http.host",
                                    config.getOptionalValue("quarkus.http.host", String.class).orElse("localhost")));
                    return startContainers(containerArgs);
                } catch (Exception e) {
                    Log.fatal("Could not start Horreum services", e);
                    stopContainers();
                    throw new RuntimeException("Could not start Horreum services", e);
                }
            }
        }
        return null;
    }

    @Override
    public void stop() {
        synchronized (ItResource.class) {
            try {
                Log.info("Stopping Horreum IT resources");
                stopContainers();
                started = false;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

}
