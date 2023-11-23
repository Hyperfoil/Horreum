package io.hyperfoil.tools.horreum.dev.services.deployment.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

import java.io.File;
import java.util.Optional;

import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_POSTGRES_NETWORK_ALIAS;

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
    @ConfigItem
    public String image;

    /**
     * Setup SSL on the postgres service
     */
    @ConfigItem(defaultValue = "false")
    public boolean sslEnabled;

    /**
     * Container name for postgres container
     */
    @ConfigItem(defaultValue = DEFAULT_POSTGRES_NETWORK_ALIAS)
    public String networkAlias;

    /**
     * File path to production backup of database
     */
    @ConfigItem
    public Optional<File> databaseBackup;

}
