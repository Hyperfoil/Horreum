package io.hyperfoil.tools.horreum.dev.services.deployment.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

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
    @ConfigItem(defaultValue = "quay.io/keycloak/keycloak:21.1")
    public String image;

    /**
     * Container name for keycloak container
     */
    @ConfigItem(defaultValue = "horreum-dev-keycloak")
    public String networkAlias;

}
