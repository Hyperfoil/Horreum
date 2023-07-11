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
public class HorreumDevServicesPostgresConfig {

    /**
     * Are Horreum dev Postgres services enabled.
     */
    @ConfigItem(defaultValue = "true")
    public boolean enabled;

    /**
     * Container image name for postgres service
     */
    @ConfigItem(defaultValue = "postgres:13")
    public String image;

    /**
     * File path to production backup of database
     */
    @ConfigItem()
    public Optional<File> databaseBackup;

    /**
     * Container name for postgres container
     */
    @ConfigItem(defaultValue = "horreum-dev-postgres")
    public String networkAlias;

}
