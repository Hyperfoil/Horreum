package io.hyperfoil.tools.horreum.dev.services.deployment.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
@ConfigMapping(prefix = "horreum.dev-services")
public interface DevServicesConfig {

    /**
     * Horreum dev services
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Configuration for Keycloak dev services
     */
    HorreumDevServicesKeycloakConfig keycloak();

    /**
     * Configuration for Postrges dev services
     */
    HorreumDevServicesPostgresConfig postgres();

}
