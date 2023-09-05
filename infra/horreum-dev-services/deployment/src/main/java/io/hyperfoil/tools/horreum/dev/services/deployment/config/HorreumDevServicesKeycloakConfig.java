package io.hyperfoil.tools.horreum.dev.services.deployment.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

import static io.hyperfoil.tools.horreum.infra.common.Const.*;

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
     * Container image name for keycloak service
     */
    @ConfigItem(defaultValue = DEFAULT_KEYCLOAK_IMAGE)
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
}
