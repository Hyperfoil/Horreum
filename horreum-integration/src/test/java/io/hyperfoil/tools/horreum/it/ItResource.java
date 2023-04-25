package io.hyperfoil.tools.horreum.it;

import io.hyperfoil.tools.horreum.it.resources.KeycloakResource;
import io.hyperfoil.tools.horreum.it.resources.PostgresResource;
import io.hyperfoil.tools.horreum.it.utils.RoleBuilder;
import io.hyperfoil.tools.horreum.it.utils.UserBuilder;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.*;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.utility.LogUtils;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.fail;

public class ItResource implements QuarkusTestResourceLifecycleManager {


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


    @Override
    public Map<String, String> start() {
        synchronized (ItResource.class) {
            if (!started) {
                started = true;
                return startContainers();
            }
        }
        return null;
    }

    @Override
    public void stop() {
        synchronized (ItResource.class) {
            try {
                stopContainers();
                started = false;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    public static Properties configProperties;

    public static final String HORREUM_USERNAME;
    public static final String HORREUM_PASSWORD;

    private static final Integer ContainerStartTimeout = 60;
    private static final TimeUnit ContainerStartTimeoutUnit = TimeUnit.SECONDS;
    private static final Integer ContainerStartRetries = 1;

    private static final Logger log = Logger.getLogger(ItResource.class);

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

    private static boolean started = false;

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


    public static Map<String, String> startContainers() {


        Map<String, String> envVariables = new HashMap<>();


        String QUARKUS_DATASOURCE_PASSWORD = getProperty("quarkus.datasource.password");

        envVariables.put("QUARKUS_DATASOURCE_PASSWORD", QUARKUS_DATASOURCE_PASSWORD);
        envVariables.put("QUARKUS_DATASOURCE_MIGRATION_PASSWORD", QUARKUS_DATASOURCE_PASSWORD);

        envVariables.put("STOP_SIGNAL", "SIGKILL");

        postgreSQLResource.init(Map.of("inContainer", "true"));
        Map<String, String> postgresEnv = postgreSQLResource.start();

        waitForContainerReady(postgreSQLResource.getContainer(), " database system is ready to accept connections");

        envVariables.putAll(postgresEnv);

        keycloakResource.init(postgresEnv);
        Map<String, String> keycloakEnv = keycloakResource.start();

        log.info("Waiting for test infrastructure to start");
        waitForContainerReady(keycloakResource.keycloakContainer, "started in");

        keycloak = KeycloakBuilder.builder()
                .serverUrl(keycloakEnv.get("keycloak.host"))
                .realm(KEYCLOAK_REALM)
                .username("admin")
                .password("secret")
                .clientId("admin-cli")
                .build();

        envVariables.put("keycloak.host", keycloakEnv.get("keycloak.host"));
        envVariables.put("horreum.keycloak.url", keycloakEnv.get("keycloak.host"));
        envVariables.put("quarkus.oidc.auth.server.url", keycloakEnv.get("keycloak.host").concat("/realms/").concat(HORREUM_REALM));
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

        keycloak.realm(HORREUM_REALM).users().get(dummyUser.getId()).roles().clientLevel(accountClient.getId()).add(Arrays.asList(viewProfileRole));

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
                Assertions.fail("Timed out waiting for " + container.getContainerName() + " container to start");
            }
        } else {
            Assertions.fail("Could not find container: " + container.getContainerName());
        }
    }


    public static void stopContainers() {
        postgreSQLResource.stop();
        keycloakResource.stop();
    }

}
