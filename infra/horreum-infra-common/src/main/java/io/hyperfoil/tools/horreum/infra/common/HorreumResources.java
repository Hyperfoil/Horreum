package io.hyperfoil.tools.horreum.infra.common;

import io.hyperfoil.tools.horreum.infra.common.resources.KeycloakResource;
import io.hyperfoil.tools.horreum.infra.common.resources.PostgresResource;
import io.hyperfoil.tools.horreum.infra.common.utils.RoleBuilder;
import io.hyperfoil.tools.horreum.infra.common.utils.UserBuilder;
import org.jboss.logging.Logger;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.*;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.utility.LogUtils;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.hyperfoil.tools.horreum.infra.common.Const.*;


public class HorreumResources {

    private static Keycloak keycloak;
    private static final String HORREUM_REALM = System.getProperty("horreum.realm", "horreum");
    private static final String KEYCLOAK_REALM = System.getProperty("keycloak.realm", "master");

    static Function<String, ClientRepresentation> findClient = clientName -> keycloak.realm(HORREUM_REALM).clients().findByClientId(clientName).get(0);
    static Function<String, String> generateClientSecret = clientName -> keycloak.realm(HORREUM_REALM).clients().get(findClient.apply(clientName).getId()).generateNewSecret().getValue();
    static Function<String, RoleRepresentation> getRoleID = role -> keycloak.realm(HORREUM_REALM).roles().get(role).toRepresentation();
    static BiFunction<String, String, RoleRepresentation> getClientRoleID = (clientId, role) -> keycloak.realm(HORREUM_REALM).clients().get(clientId).roles().get(role).toRepresentation();

    static Function<Supplier<RoleRepresentation>, RoleRepresentation> createRole = roleSupplier -> {
        RoleRepresentation roleRepresentation = roleSupplier.get();
        keycloak.realm(HORREUM_REALM).roles().create(roleRepresentation);
        return keycloak.realm(HORREUM_REALM).roles().get(roleRepresentation.getName()).toRepresentation();
    };

    static Function<Supplier<UserRepresentation>, UserRepresentation> createUser = userSupplier -> {
        UserRepresentation userRepresentation = userSupplier.get();
        keycloak.realm(HORREUM_REALM).users().create(userRepresentation);
        return keycloak.realm(HORREUM_REALM).users().searchByUsername(userRepresentation.getUsername(), true).stream().findFirst().get();
    };


    public static Properties configProperties;

    public static final String HORREUM_USERNAME;
    public static final String HORREUM_PASSWORD;

    private static final Integer ContainerStartTimeout = 60;
    private static final TimeUnit ContainerStartTimeoutUnit = TimeUnit.SECONDS;
    private static final Integer ContainerStartRetries = 1;

    private static final Logger log = Logger.getLogger(HorreumResources.class);

    static {
        configProperties = new Properties();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try (InputStream propertyStream = classLoader.getResourceAsStream("env.properties");
             InputStream applicationStream = classLoader.getResourceAsStream("application.properties")
        ) {
            if (propertyStream != null) {
                configProperties.load(propertyStream);
            } else {
                throw new RuntimeException("Could not load test configuration");
            }
            if (applicationStream != null) {
                Properties horreumProps = new Properties();
                horreumProps.load(applicationStream);
                configProperties.putAll(horreumProps);
            } else {
                throw new RuntimeException("Could not load application properties");
            }

            HORREUM_USERNAME = getProperty("horreum.username");
            HORREUM_PASSWORD = getProperty("horreum.password");

        } catch (IOException ioException) {
            throw new RuntimeException("Failed to load configuration properties");
        }
    }

    private static final boolean started = false;

    private static final Network network = Network.newNetwork();
    public static PostgresResource postgreSQLResource = new PostgresResource();
    public static KeycloakResource keycloakResource = new KeycloakResource();
    protected static String getProperty(String propertyName) {
        String override = System.getProperty(propertyName);
        if (override != null) {
            override = override.trim();
            if (!override.isEmpty()) {
                return override;
            }
        }
        String value = configProperties.getProperty(propertyName);
        if (value == null) {
            throw new IllegalStateException("Missing property value for " + propertyName);
        }
        return value.trim();
    }


    public static Map<String, String> startContainers(Map<String, String> initArgs) {



        Map<String, String> envVariables = new HashMap<>();


        String QUARKUS_DATASOURCE_PASSWORD = getProperty("quarkus.datasource.password");

        envVariables.put("QUARKUS_DATASOURCE_PASSWORD", QUARKUS_DATASOURCE_PASSWORD);
        envVariables.put("QUARKUS_DATASOURCE_MIGRATION_PASSWORD", QUARKUS_DATASOURCE_PASSWORD);

        envVariables.put("STOP_SIGNAL", "SIGKILL");

        envVariables.putAll(initArgs);
        envVariables.put("inContainer", "true");

        envVariables.put(HORREUM_DEV_DB_DATABASE, DEFAULT_DBDATABASE);
        envVariables.put(HORREUM_DEV_DB_USERNAME, DEFAULT_DB_USERNAME);
        envVariables.put(HORREUM_DEV_DB_PASSWORD, DEFAULT_DB_PASSWORD);

        postgreSQLResource.init(envVariables);

        Optional<Network> optionalNetwork = Optional.of(network);

        Map<String, String> postgresEnv = postgreSQLResource.start(optionalNetwork);

        waitForContainerReady(postgreSQLResource.getContainer(), " database system is ready to accept connections");

        envVariables.putAll(postgresEnv);

        keycloakResource.init(envVariables);
        Map<String, String> keycloakEnv = keycloakResource.start(optionalNetwork);

        waitForContainerReady(keycloakResource.getContainer(), "started in");

        envVariables.put("keycloak.host", keycloakEnv.get("keycloak.host"));
        envVariables.put("horreum.keycloak.url", keycloakEnv.get("keycloak.host"));
        envVariables.put("quarkus.oidc.auth-server-url", keycloakEnv.get("keycloak.host").concat("/realms/").concat(HORREUM_REALM));

        String keycloakAdminUser = initArgs.get(HORREUM_DEV_KEYCLOAK_ADMIN_USERNAME);
        String keycloakAdminPassword = initArgs.get(HORREUM_DEV_KEYCLOAK_ADMIN_PASSWORD);

        keycloak = KeycloakBuilder.builder()
                .serverUrl(keycloakEnv.get("keycloak.host"))
                .realm(KEYCLOAK_REALM)
                .username(keycloakAdminUser)
                .password(keycloakAdminPassword)
                .clientId("admin-cli")
                .build();

        if ( ! initArgs.containsKey(HORREUM_DEV_POSTGRES_BACKUP) ) {
            // Not using a backup db, so need to create the dummy roles


            // Obtain client secrets for Horreum
            envVariables.put("quarkus.oidc.credentials.secret", generateClientSecret.apply("horreum"));

            // Create roles and example user in Keycloak
            RoleRepresentation uploaderRole = getRoleID.apply("uploader");
            RoleRepresentation testerRole = getRoleID.apply("tester");
            RoleRepresentation viewerRole = getRoleID.apply("viewer");
            RoleRepresentation adminRole = getRoleID.apply("admin");

            RoleRepresentation devTeamRole = createRole.apply(() -> RoleBuilder.create().name("dev-team").build());
            RoleRepresentation teamViewerRole = createRole.apply(() -> RoleBuilder.create().name("dev-viewer").composite().realmComposite(devTeamRole).realmComposite(viewerRole).build());
            RoleRepresentation teamUploaderRole = createRole.apply(() -> RoleBuilder.create().name("dev-uploader").composite().realmComposite(devTeamRole).realmComposite(uploaderRole).build());
            RoleRepresentation teamTesterRole = createRole.apply(() -> RoleBuilder.create().name("dev-tester").composite().realmComposite(devTeamRole).realmComposite(testerRole).build());
            RoleRepresentation teamManagerRole = createRole.apply(() -> RoleBuilder.create().name("dev-manager").composite().realmComposite(devTeamRole).build());

            UserRepresentation dummyUser = createUser.apply(() ->
                    UserBuilder.create()
                            .username("user")
                            .firstName("Dummy")
                            .lastName("User")
                            .password("secret")
                            .email("user@example.com")
                            .enabled(true)
                            .build()
            );

            keycloak.realm(HORREUM_REALM).users().get(dummyUser.getId()).roles().realmLevel().add(Arrays.asList(teamUploaderRole, teamTesterRole, teamViewerRole, teamManagerRole, adminRole));

            ClientRepresentation accountClient = findClient.apply("account");

            RoleRepresentation viewProfileRole = getClientRoleID.apply(accountClient.getId(), "view-profile");

            keycloak.realm(HORREUM_REALM).users().get(dummyUser.getId()).roles().clientLevel(accountClient.getId()).add(Collections.singletonList(viewProfileRole));
        } else {
            try {
                //TODO: resolve the host and quarkus port

                ClientRepresentation clientRepresentation = keycloak.realm(HORREUM_REALM).clients().findByClientId("horreum-ui").get(0);
                clientRepresentation.getWebOrigins().add("http://localhost:8080");
                clientRepresentation.getRedirectUris().add("http://localhost:8080/*");

                keycloak.realm(HORREUM_REALM).clients().get(clientRepresentation.getId()).update(clientRepresentation);
            } catch (Exception e){
                log.warn("Unable to re-configure keycloak instance: ".concat(e.getLocalizedMessage()));
            }
        }
        log.info("Waiting for test infrastructure to start");

        return envVariables;
    }

    private static void waitForContainerReady(GenericContainer container, String pattern) {

        if (container != null) {
            WaitingConsumer waitingConsumer = new WaitingConsumer();

            LogUtils.followOutput(DockerClientFactory.instance().client(), container.getContainerId(), waitingConsumer);
            try {
                waitingConsumer.waitUntil(
                        outputFrame -> outputFrame.getUtf8String().contains(pattern)
                        , ContainerStartTimeout
                        , ContainerStartTimeoutUnit
                        , ContainerStartRetries
                );
            } catch (TimeoutException e) {
                throw new RuntimeException("Timed out waiting for " + container.getContainerName() + " container to start");
            }
        } else {
            throw new RuntimeException("Could not find container: " + container.getContainerName());
        }
    }


    public static void stopContainers() {
        postgreSQLResource.stop();
        keycloakResource.stop();
    }

}
