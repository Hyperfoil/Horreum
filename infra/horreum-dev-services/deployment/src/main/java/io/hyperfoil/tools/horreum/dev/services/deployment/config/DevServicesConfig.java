package io.hyperfoil.tools.horreum.dev.services.deployment.config;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(prefix = "horreum", phase = ConfigPhase.BUILD_TIME)
public class DevServicesConfig {

    /**
     * Horreum dev services
     */
    @ConfigItem(defaultValue = "true")
    public boolean enabled;
    /**
     * Configuration for Keycloak dev services
     */
    public HorreumDevServicesKeycloakConfig keycloak;
    /**
     * Configuration for Postrges dev services
     */
    public HorreumDevServicesPostgresConfig postgres;

}
