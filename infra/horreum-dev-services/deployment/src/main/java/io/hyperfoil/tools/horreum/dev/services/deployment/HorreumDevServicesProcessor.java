package io.hyperfoil.tools.horreum.dev.services.deployment;

import io.hyperfoil.tools.horreum.dev.services.deployment.config.DevServicesConfig;
import io.hyperfoil.tools.horreum.infra.common.HorreumResources;
import io.hyperfoil.tools.horreum.infra.common.SelfSignedCert;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.*;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.dev.devservices.GlobalDevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import org.jboss.logging.Logger;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static io.hyperfoil.tools.horreum.infra.common.Const.*;

@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = {HorreumDevServicesProcessor.IsEnabled.class, GlobalDevServicesConfig.Enabled.class})
public class HorreumDevServicesProcessor {

    private static final Logger LOG = Logger.getLogger(HorreumDevServicesProcessor.class);

    private static volatile DevServicesResultBuildItem.RunningDevService horreumKeycloakDevService;
    private static volatile DevServicesResultBuildItem.RunningDevService horreumPostgresDevService;

    @BuildStep(onlyIf = {IsDevelopment.class})
    public void startHorreumContainers(
            BuildProducer<DevServicesResultBuildItem> devServicesResultBuildItemBuildProducer,
            DockerStatusBuildItem dockerStatusBuildItem,
            BuildProducer<HorreumDevServicesConfigBuildItem> horreumBuildItemBuildProducer,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            DevServicesConfig horreumBuildTimeConfig,
            CuratedApplicationShutdownBuildItem closeBuildItem,
            LaunchModeBuildItem launchMode,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            LoggingSetupBuildItem loggingSetupBuildItem,
            GlobalDevServicesConfig devServicesConfig
    ) {


        StartupLogCompressor compressor = new StartupLogCompressor(
                (launchMode.isTest() ? "(test) " : "") + "Horreum Dev Services Starting:",
                consoleInstalledBuildItem, loggingSetupBuildItem);

        boolean errors = false;

        LOG.infof("Horreum dev services (enabled: ".concat(Boolean.toString(horreumBuildTimeConfig.enabled)).concat(")"));

        if (horreumBuildTimeConfig.enabled) {
            try {

                if (errors = !dockerStatusBuildItem.isDockerAvailable()) {
                    LOG.warn("Docker dev service instance not found");
                }

                if (!errors) {

                    //TODO:: check to see if devServicesConfiguration has changed
                    if (horreumKeycloakDevService == null || horreumPostgresDevService == null) {

                        LOG.infof("Starting Horreum containers");

                        final Map<String, String> containerArgs = new HashMap<>();
                        containerArgs.put(HORREUM_DEV_KEYCLOAK_ENABLED, Boolean.toString(horreumBuildTimeConfig.keycloak.enabled));
                        containerArgs.put(HORREUM_DEV_KEYCLOAK_IMAGE, horreumBuildTimeConfig.keycloak.image);
                        containerArgs.put(HORREUM_DEV_KEYCLOAK_NETWORK_ALIAS, horreumBuildTimeConfig.keycloak.networkAlias);
                        containerArgs.put(HORREUM_DEV_POSTGRES_ENABLED, Boolean.toString(horreumBuildTimeConfig.postgres.enabled));
                        containerArgs.put(HORREUM_DEV_POSTGRES_IMAGE, horreumBuildTimeConfig.postgres.image);
                        containerArgs.put(HORREUM_DEV_POSTGRES_NETWORK_ALIAS, horreumBuildTimeConfig.postgres.networkAlias);
                        containerArgs.put(HORREUM_DEV_KEYCLOAK_DB_USERNAME, horreumBuildTimeConfig.keycloak.dbUsername);
                        containerArgs.put(HORREUM_DEV_KEYCLOAK_DB_PASSWORD, horreumBuildTimeConfig.keycloak.dbPassword);
                        containerArgs.put(HORREUM_DEV_KEYCLOAK_ADMIN_USERNAME, horreumBuildTimeConfig.keycloak.adminUsername);
                        containerArgs.put(HORREUM_DEV_KEYCLOAK_ADMIN_PASSWORD, horreumBuildTimeConfig.keycloak.adminPassword);

                        horreumBuildTimeConfig.keycloak.containerPort.ifPresent(keycloakPort -> containerArgs.put(HORREUM_DEV_KEYCLOAK_CONTAINER_PORT, keycloakPort));
                        horreumBuildTimeConfig.postgres.databaseBackup.map(File::getAbsolutePath).ifPresent(backupFilename -> containerArgs.put(HORREUM_DEV_POSTGRES_BACKUP, backupFilename));

                        // generate self-signed certificate(s) and return it as args to be processed by KeycloakResource / PostgresResource
                        if (horreumBuildTimeConfig.keycloak.httpsEnabled) {
                            SelfSignedCert keycloakSelfSignedCert = new SelfSignedCert("RSA", "SHA256withRSA", "localhost", 123);
                            containerArgs.put(HORREUM_DEV_KEYCLOAK_HTTPS_CERTIFICATE, keycloakSelfSignedCert.getCertString());
                            containerArgs.put(HORREUM_DEV_KEYCLOAK_HTTPS_CERTIFICATE_KEY, keycloakSelfSignedCert.getKeyString());
                        }
                        if (horreumBuildTimeConfig.postgres.sslEnabled) {
                            SelfSignedCert postgresSelfSignedCert = new SelfSignedCert("RSA", "SHA256withRSA", "localhost", 123);
                            containerArgs.put(HORREUM_DEV_POSTGRES_SSL_CERTIFICATE, postgresSelfSignedCert.getCertString());
                            containerArgs.put(HORREUM_DEV_POSTGRES_SSL_CERTIFICATE_KEY, postgresSelfSignedCert.getKeyString());
                        }

                        Map<String, String> envvars = HorreumResources.startContainers(Collections.unmodifiableMap(containerArgs));

                        if (horreumBuildTimeConfig.postgres.enabled) {
                            Map<String, String> postgresConfig = new HashMap<>();
                            String jdbcUrl = HorreumResources.postgreSQLResource.getJdbcUrl();

                            postgresConfig.put("quarkus.datasource.jdbc.url", jdbcUrl);
                            postgresConfig.put("quarkus.datasource.migration.jdbc.url", jdbcUrl);
                            if (horreumBuildTimeConfig.postgres.sslEnabled) {
                                // see https://jdbc.postgresql.org/documentation/ssl/ for details
                                postgresConfig.put("quarkus.datasource.jdbc.additional-jdbc-properties.ssl", "true");
                                postgresConfig.put("quarkus.datasource.jdbc.additional-jdbc-properties.sslmode", "verify-full");
                                postgresConfig.put("quarkus.datasource.jdbc.additional-jdbc-properties.sslrootcert", envvars.get("quarkus.datasource.jdbc.sslrootcert"));
                            }

                            horreumPostgresDevService = new DevServicesResultBuildItem.RunningDevService(
                                    HorreumResources.postgreSQLResource.getContainer().getContainerName(),
                                    HorreumResources.postgreSQLResource.getContainer().getContainerId(),
                                    HorreumResources.postgreSQLResource.getContainer()::close,
                                    postgresConfig);
                        }
                        if (horreumBuildTimeConfig.keycloak.enabled) {
                            Map<String, String> keycloakConfig = new HashMap<>();
                            Integer keycloakPort = HorreumResources.keycloakResource.getContainer().getMappedPort(horreumBuildTimeConfig.keycloak.httpsEnabled ? 8443 : 8080);
                            String keycloakURL = (horreumBuildTimeConfig.keycloak.httpsEnabled ? "https" : "http") + "://localhost:" + keycloakPort;

                            keycloakConfig.put("horreum.keycloak.url", keycloakURL);
                            keycloakConfig.put("quarkus.oidc.auth-server-url", keycloakURL + "/realms/horreum");
                            keycloakConfig.put("quarkus.oidc.credentials.secret", envvars.get("quarkus.oidc.credentials.secret"));
                            keycloakConfig.put("quarkus.keycloak.admin-client.username", horreumBuildTimeConfig.keycloak.adminUsername);
                            keycloakConfig.put("quarkus.keycloak.admin-client.password", horreumBuildTimeConfig.keycloak.adminPassword);
                            if (envvars.containsKey("quarkus.oidc.tls.trust-store-file")) {
                                keycloakConfig.put("quarkus.oidc.tls.trust-store-file", envvars.get("quarkus.oidc.tls.trust-store-file"));
                                keycloakConfig.put("quarkus.oidc.tls.verification", "required"); // "certificate-validation" validates the certificate chain, but not the hostname. could also be "none" and disable TLS verification altogether
                            }

                            horreumKeycloakDevService = new DevServicesResultBuildItem.RunningDevService(
                                    HorreumResources.keycloakResource.getContainer().getContainerName(),
                                    HorreumResources.keycloakResource.getContainer().getContainerId(),
                                    HorreumResources.keycloakResource.getContainer()::close,
                                    keycloakConfig);
                        }
                    }
                }

                Runnable closeTask = () -> {
                    if (horreumKeycloakDevService != null) {
                        try {
                            horreumKeycloakDevService.close();
                        } catch (Throwable t) {
                            LOG.error("Failed to stop Keycloak container", t);
                        }
                    }
                    if (horreumPostgresDevService != null) {
                        try {
                            horreumPostgresDevService.close();
                        } catch (Throwable t) {
                            LOG.error("Failed to stop Postgres container", t);
                        }
                    }
                    horreumKeycloakDevService = null;
                    horreumPostgresDevService = null;
                };
                closeBuildItem.addCloseTask(closeTask, true);

                if ((horreumKeycloakDevService != null || horreumPostgresDevService != null) && !errors) {
                    compressor.close();
                } else {
                    compressor.closeAndDumpCaptured();
                }
            } catch (Throwable t) {
                compressor.closeAndDumpCaptured();
                throw new RuntimeException(t);
            }

            if (horreumPostgresDevService != null) {
                devServicesResultBuildItemBuildProducer.produce(horreumPostgresDevService.toBuildItem());
            }
            if (horreumKeycloakDevService != null) {
                devServicesResultBuildItemBuildProducer.produce(horreumKeycloakDevService.toBuildItem());
            }
        }
    }

    public static class IsEnabled implements BooleanSupplier {
        DevServicesConfig config;

        public boolean getAsBoolean() {
            return config.enabled;
        }
    }
}
