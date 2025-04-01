package io.hyperfoil.tools.horreum.dev.services.deployment.config;

import static io.hyperfoil.tools.horreum.infra.common.Const.DEFAULT_POSTGRES_NETWORK_ALIAS;

import java.io.File;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for Horreum dev services.
 */
@ConfigGroup
@ConfigMapping(prefix = "postgres")
public interface HorreumDevServicesPostgresConfig {

    /**
     * Are Horreum dev Postgres services enabled.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Container image name for postgres service
     */
    String image();

    /**
     * Setup SSL on the postgres service
     */
    @WithDefault("false")
    boolean sslEnabled();

    /**
     * SSL certificate path
     */
    Optional<String> sslCertificate();

    /**
     * SSL certificate key path
     */
    Optional<String> sslCertificateKey();

    /**
     * Container name for postgres container
     */
    @WithDefault(DEFAULT_POSTGRES_NETWORK_ALIAS)
    String networkAlias();

    /**
     * File path to production backup of database
     */
    Optional<File> databaseBackup();

}
