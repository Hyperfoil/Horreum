package io.hyperfoil.tools.horreum.dev.services.deployment;

import io.hyperfoil.tools.horreum.dev.services.deployment.config.HorreumDevConfig;
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

    static volatile HorreumDevConfig devServicesConfiguration;

    @BuildStep(onlyIf = {IsDevelopment.class})
    public void startHorreumContainers(
            BuildProducer<DevServicesResultBuildItem> devServicesResultBuildItemBuildProducer,
            DockerStatusBuildItem dockerStatusBuildItem,
            BuildProducer<HorreumDevServicesConfigBuildItem> horreumBuildItemBuildProducer,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            HorreumDevConfig horreumBuildTimeConfig,
            CuratedApplicationShutdownBuildItem closeBuildItem,
            LaunchModeBuildItem launchMode,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            LoggingSetupBuildItem loggingSetupBuildItem,
            GlobalDevServicesConfig devServicesConfig
    ) {


        devServicesConfiguration = horreumBuildTimeConfig;

        StartupLogCompressor compressor = new StartupLogCompressor(
                (launchMode.isTest() ? "(test) " : "") + "Horreum Dev Services Starting:",
                consoleInstalledBuildItem, loggingSetupBuildItem);

        boolean errors = false;

        try {
            LOG.infof("Starting Horreum dev services");

            if (errors = !dockerStatusBuildItem.isDockerAvailable()) {
                LOG.warn("Docker dev service instance not found");
            }

            if (!errors) {

                //TODO:: check to see if devServicesConfiguration has changed
                if (horreumKeycloakDevService == null || horreumPostgresDevService == null) {


                    LOG.infof("Starting Horreum containers");

                    Map<String, String> containerArgs = Map.of(
                            HORREUM_DEV_KEYCLOAK_ENABLED, Boolean.toString(devServicesConfiguration.keycloak.enabled),
                            HORREUM_DEV_KEYCLOAK_IMAGE, devServicesConfiguration.keycloak.image,
                            HORREUM_DEV_KEYCLOAK_NETWORK_ALIAS, devServicesConfiguration.keycloak.networkAlias,
                            HORREUM_DEV_POSTGRES_ENABLED, Boolean.toString(devServicesConfiguration.postgres.enabled),
                            HORREUM_DEV_POSTGRES_IMAGE, devServicesConfiguration.postgres.image,
                            HORREUM_DEV_POSTGRES_NETWORK_ALIAS, devServicesConfiguration.postgres.networkAlias
                    );

                    HorreumResources.startContainers(containerArgs);

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
                devServicesConfiguration = null;
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

    public static class IsEnabled implements BooleanSupplier {
        HorreumDevConfig config;

        public boolean getAsBoolean() {
            return config.enabled;
        }
    }

}
