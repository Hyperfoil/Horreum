package io.hyperfoil.tools.horreum.dev.services.deployment.config;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot
public class HorreumDevConfig {

    /**
     * Horreum dev services
     */
    @ConfigItem(defaultValue = "true")
    public boolean enabled;
    /**
     * Configuration for Keycloak dev services
     */
    @ConfigItem
    public HorreumDevServicesKeycloakConfig keycloak;
    /**
     * Configuration for Postrges dev services
     */
    @ConfigItem
    public HorreumDevServicesPostgresConfig postgres;

}
