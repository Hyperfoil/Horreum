package io.hyperfoil.tools.horreum.dev.services.deployment;

import io.hyperfoil.tools.horreum.dev.services.deployment.config.DevServicesConfig;
import io.hyperfoil.tools.horreum.infra.common.HorreumResources;
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
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

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

                        String backupFilename = horreumBuildTimeConfig.postgres.databaseBackup.isPresent() ?
                                horreumBuildTimeConfig.postgres.databaseBackup.get().getAbsolutePath() :
                                null;

                        Map<String, String> containerArgs = new HashMap<>();
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
                        String keyCloakPort = horreumBuildTimeConfig.keycloak.containerPort.orElse(null);
                        if ( keyCloakPort != null )
                            containerArgs.put(HORREUM_DEV_KEYCLOAK_CONTAINER_PORT, keyCloakPort);

                        if (backupFilename != null) {
                            containerArgs = ImmutableMap.<String, String>builder()
                                    .putAll(containerArgs)
                                    .putAll(Map.of(HORREUM_DEV_POSTGRES_BACKUP, backupFilename))
                                    .build();
                        }
                        Map<String, String> envvars = HorreumResources.startContainers(containerArgs);

                        Map<String, String> postrgesConfig = new HashMap<>();
                        String jdbcUrl = HorreumResources.postgreSQLResource.getJdbcUrl();

                        postrgesConfig.put("quarkus.datasource.jdbc.url", jdbcUrl);
                        postrgesConfig.put("quarkus.datasource.migration.jdbc.url", jdbcUrl);

                        horreumPostgresDevService = new DevServicesResultBuildItem.RunningDevService(
                                HorreumResources.postgreSQLResource.getContainer().getContainerName(),
                                HorreumResources.postgreSQLResource.getContainer().getContainerId(),
                                HorreumResources.postgreSQLResource.getContainer()::close,
                                postrgesConfig);

                        Map<String, String> keycloakConfig = new HashMap<>();
                        Integer keycloakPort = HorreumResources.keycloakResource.getContainer().getMappedPort(8080);

                        keycloakConfig.put("quarkus.oidc.auth-server-url", "http://localhost:" + keycloakPort + "/realms/horreum");
                        keycloakConfig.put("horreum.keycloak.url", "http://localhost:" + keycloakPort);
                        keycloakConfig.put("quarkus.oidc.credentials.secret", envvars.get("quarkus.oidc.credentials.secret"));

                        horreumKeycloakDevService = new DevServicesResultBuildItem.RunningDevService(
                                HorreumResources.keycloakResource.getContainer().getContainerName(),
                                HorreumResources.keycloakResource.getContainer().getContainerId(),
                                HorreumResources.keycloakResource.getContainer()::close,
                                keycloakConfig);
                    }

                }

                if (horreumKeycloakDevService == null || horreumPostgresDevService == null) {
                    if (!errors) {
                        compressor.close();
                    } else {
                        compressor.closeAndDumpCaptured();
                    }
                    return;
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

            devServicesResultBuildItemBuildProducer.produce(horreumKeycloakDevService.toBuildItem());
            devServicesResultBuildItemBuildProducer.produce(horreumPostgresDevService.toBuildItem());
        }
    }

    public static class IsEnabled implements BooleanSupplier {
        DevServicesConfig config;

        public boolean getAsBoolean() {
            return config.enabled;
        }
    }

}
