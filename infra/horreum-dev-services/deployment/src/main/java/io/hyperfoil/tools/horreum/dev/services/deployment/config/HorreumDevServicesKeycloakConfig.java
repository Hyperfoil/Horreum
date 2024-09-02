package io.hyperfoil.tools.horreum.dev.services.deployment.config;

import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_KC_ADMIN_PASSWORD;
import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_KC_ADMIN_USERNAME;
import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_KC_DB_PASSWORD;
import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_KC_DB_USERNAME;
import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_KEYCLOAK_NETWORK_ALIAS;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

/**
 * Configuration for Horreum dev services.
 */
@ConfigGroup
public class HorreumDevServicesKeycloakConfig {

    /**
     * Are Horreum dev Keycloak services enabled.
     */
    @ConfigItem(defaultValue = "true")
    public boolean enabled;

    /**
     * Setup and use the HTTPS listener.
     */
    @ConfigItem(defaultValue = "false")
    public boolean httpsEnabled;

    /**
     * Container image name for keycloak service
     */
    @ConfigItem
    public String image;

    /**
     * Container name for keycloak container
     */
    @ConfigItem(defaultValue = DEFAULT_KEYCLOAK_NETWORK_ALIAS)
    public String networkAlias;

    /**
     * Databse username for keycloak instance
     */
    @ConfigItem(defaultValue = DEFAULT_KC_DB_USERNAME)
    public String dbUsername;

    /**
     * Databse password for keycloak instance
     */
    @ConfigItem(defaultValue = DEFAULT_KC_DB_PASSWORD)
    public String dbPassword;

    /**
     * Admin Username password for keycloak
     */
    @ConfigItem(defaultValue = DEFAULT_KC_ADMIN_USERNAME)
    public String adminUsername;
    /**
     * Admin Username password for keycloak
     */
    @ConfigItem(defaultValue = DEFAULT_KC_ADMIN_PASSWORD)
    public String adminPassword;

    /**
     * Define host port to expose Keycloak service.
     * Usage of this property is discouraged and should only be enabled in scenarios where the keycloak port can not be dynamic
     */
    @ConfigItem()
    public Optional<String> containerPort;
}
