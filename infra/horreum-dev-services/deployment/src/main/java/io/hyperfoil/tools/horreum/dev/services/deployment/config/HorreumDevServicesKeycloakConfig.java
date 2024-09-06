package io.hyperfoil.tools.horreum.dev.services.deployment.config;

import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_KC_ADMIN_PASSWORD;
import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_KC_ADMIN_USERNAME;
import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_KC_DB_PASSWORD;
import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_KC_DB_USERNAME;
import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_KEYCLOAK_NETWORK_ALIAS;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for Horreum dev services.
 */
@ConfigGroup
@ConfigMapping(prefix = "keycloak")
public interface HorreumDevServicesKeycloakConfig {

    /**
     * Are Horreum dev Keycloak services enabled.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Setup and use the HTTPS listener.
     */
    @WithDefault("false")
    boolean httpsEnabled();

    /**
     * Container image name for keycloak service
     */
    String image();

    /**
     * Container name for keycloak container
     */
    @WithDefault(DEFAULT_KEYCLOAK_NETWORK_ALIAS)
    String networkAlias();

    /**
     * Databse username for keycloak instance
     */
    @WithDefault(DEFAULT_KC_DB_USERNAME)
    String dbUsername();

    /**
     * Databse password for keycloak instance
     */
    @WithDefault(DEFAULT_KC_DB_PASSWORD)
    String dbPassword();

    /**
     * Admin Username password for keycloak
     */
    @WithDefault(DEFAULT_KC_ADMIN_USERNAME)
    public String adminUsername();

    /**
     * Admin Username password for keycloak
     */
    @WithDefault(DEFAULT_KC_ADMIN_PASSWORD)
    public String adminPassword();

    /**
     * Define host port to expose Keycloak service.
     * Usage of this property is discouraged and should only be enabled in scenarios where the keycloak port can not be dynamic
     */
    Optional<String> containerPort();
}
