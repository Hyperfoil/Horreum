package io.hyperfoil.tools;

import static org.junit.jupiter.api.Assertions.fail;

import io.restassured.specification.RequestSpecification;
import org.jboss.logging.Logger;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.utility.LogUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import io.restassured.RestAssured;

public class HorreumTestExtension implements BeforeAllCallback, AfterAllCallback, ExtensionContext.Store.CloseableResource  {

    public static Properties configProperties;
    public static final String HORREUM_KEYCLOAK_BASE_URL;
    public static final String HORREUM_BASE_URL;

    public static final String HORREUM_USERNAME;
    public static final String HORREUM_PASSWORD;
    private static final Integer HORREUM_TEST_PORT_OFFSET;

    private static final String CONTAINER_HOST_IP;
    private static final String CONTAINER_JAVA_OPTIONS;

    private static final Integer HORREUM_HTTP_PORT = 8080;
    private static final Integer HORREUM_HTTPS_PORT = 8443;
    private static final Integer GRAFANA_HTTP_PORT = 4040;
    private static final Integer POSTGRES_PORT = 5432;
    private static final Integer KEYCLOAK_PORT = 8180;

    protected static boolean START_HORREUM_INFRA;
    protected static boolean STOP_HORREUM_INFRA;
    protected static boolean HORREUM_DUMP_LOGS;

    private static final Integer ContainerStartTimeout = 60;
    private static final TimeUnit ContainerStartTimeoutUnit = TimeUnit.SECONDS;
    private static final Integer ContainerStartRetries = 1;

    private static final Logger log = Logger.getLogger(HorreumTestExtension.class);

    private static Keycloak keycloak;
    private static final String keycloakRealm = "horreum";
    private static final String clientId = "horreum-ui";

    protected static final ResteasyClientBuilder clientBuilder;

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
                configProperties.put("quarkus.datasource.password", horreumProps.getProperty("quarkus.datasource.password"));
                configProperties.put("horreum.grafana.admin.password", horreumProps.getProperty("horreum.grafana.admin.password"));
            } else {
                throw new RuntimeException("Could not load application properties");
            }

            HORREUM_TEST_PORT_OFFSET = Integer.parseInt(getProperty("test.port.offset"));
            CONTAINER_HOST_IP = getProperty("container.host.ip");

            HORREUM_KEYCLOAK_BASE_URL = "http://" + CONTAINER_HOST_IP + ":" + getOffsetPort(KEYCLOAK_PORT);
            HORREUM_BASE_URL = "http://"+ CONTAINER_HOST_IP + ":" + getOffsetPort(HORREUM_HTTP_PORT);

            HORREUM_USERNAME = getProperty("horreum.username");
            HORREUM_PASSWORD = getProperty("horreum.password");

            CONTAINER_JAVA_OPTIONS = getProperty("container.java.options");

            START_HORREUM_INFRA = Boolean.parseBoolean(getProperty("horreum.start-infra"));
            STOP_HORREUM_INFRA = Boolean.parseBoolean(getProperty("horreum.stop-infra"));
            HORREUM_DUMP_LOGS = Boolean.parseBoolean(getProperty("horreum.dump-logs"));

            clientBuilder = new ResteasyClientBuilderImpl().connectionPoolSize(20);

            keycloak = KeycloakBuilder.builder()
                  .serverUrl(HORREUM_KEYCLOAK_BASE_URL)
                  .realm(keycloakRealm)
                  .username(HORREUM_USERNAME)
                  .password(HORREUM_PASSWORD)
                  .clientId(clientId)
                  .clientSecret(null)
                  .resteasyClient(clientBuilder.build())
                  .build();

        } catch (IOException ioException) {
            throw new RuntimeException("Failed to load configuration properties");
        }
    }

    private static boolean started = false;

    public static TestContainer infrastructureContainer = null;
    public static TestContainer horreumContainer = null;

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

    public static void startContainers() throws Exception {
        log.info("Starting Infra: " + START_HORREUM_INFRA);
        if (START_HORREUM_INFRA) {

            String PORT_OFFSET = getOffsetPort(0);

            Map<String, String> envVariables = new HashMap<>();

            String CONTAINER_HOST_HTTP_ROOT = "http://" + CONTAINER_HOST_IP + ":";
            String KEYCLOAK_OFFSET_PORT = getOffsetPort(KEYCLOAK_PORT);
            String KEYCLOAK_URL_ROOT = "http://172.17.0.1:" + KEYCLOAK_OFFSET_PORT;

            String QUARKUS_DATASOURCE_PASSWORD = getProperty("quarkus.datasource.password");
            String HORREUM_GRAFANA_ADMIN_PASSWORD = getProperty("horreum.grafana.admin.password");

            String horreumCommitId = System.getProperty("horreum.commit.id");
            if (horreumCommitId == null || horreumCommitId.isBlank()) {
                try (InputStream stream = HorreumTestExtension.class.getClassLoader().getResourceAsStream("buildinfo.properties")) {
                    if (stream == null) {
                        throw new IllegalStateException("Cannot determine Horreum commit ID this test should run against.");
                    }
                    Properties buildInfo = new Properties();
                    buildInfo.load(stream);
                    horreumCommitId = buildInfo.getProperty("horreum.build.commit");
                }
            }
            envVariables.put("HORREUM_COMMIT_ID", horreumCommitId);
            envVariables.put("CONTAINER_HOST_IP", CONTAINER_HOST_IP);
            envVariables.put("PORT_OFFSET", PORT_OFFSET);
            envVariables.put("QUARKUS_DATASOURCE_PASSWORD", QUARKUS_DATASOURCE_PASSWORD);
            envVariables.put("HORREUM_HTTPS_PORT", getOffsetPort(HORREUM_HTTPS_PORT));
            envVariables.put("HORREUM_HTTP_PORT", getOffsetPort(HORREUM_HTTP_PORT ));
            envVariables.put("GRAFANA_HTTP_PORT", getOffsetPort(GRAFANA_HTTP_PORT ));
            envVariables.put("KEYCLOAK_HTTP_PORT", KEYCLOAK_OFFSET_PORT);
            envVariables.put("POSTGRES_PORT", getOffsetPort(POSTGRES_PORT));
            envVariables.put("HORREUM_HORREUM_INTERNAL_URL", CONTAINER_HOST_HTTP_ROOT + getOffsetPort(HORREUM_HTTPS_PORT));
            envVariables.put("HORREUM_HORREUM_KEYCLOAK_URL",  KEYCLOAK_URL_ROOT);
            envVariables.put("HORREUM_HORREUM_URL", CONTAINER_HOST_HTTP_ROOT + getOffsetPort(HORREUM_HTTP_PORT));
            envVariables.put("HORREUM_QUARKUS_OIDC_AUTH_SERVER_URL", KEYCLOAK_URL_ROOT + "/realms/horreum");
            envVariables.put("HORREUM_QUARKUS_DATASOURCE_JDBC_URL", "jdbc:postgresql://" + CONTAINER_HOST_IP + ":" + getOffsetPort(POSTGRES_PORT) + "/horreum");
            envVariables.put("QUARKUS_DATASOURCE_MIGRATION_PASSWORD", QUARKUS_DATASOURCE_PASSWORD);
            envVariables.put("CONTAINER_JAVA_OPTIONS", CONTAINER_JAVA_OPTIONS);

            envVariables.put("GRAFANA_GF_SERVER_ROOT_URL", CONTAINER_HOST_HTTP_ROOT + getOffsetPort(GRAFANA_HTTP_PORT ) + "/");
            envVariables.put("HORREUM_GRAFANA_ADMIN_PASSWORD", HORREUM_GRAFANA_ADMIN_PASSWORD);

            String GF_AUTH_URL_ROOT = KEYCLOAK_URL_ROOT + "/realms/horreum/protocol/openid-connect";
            envVariables.put("GF_AUTH_GENERIC_OAUTH_AUTH_URL", GF_AUTH_URL_ROOT + "/auth");
            envVariables.put("GF_AUTH_GENERIC_OAUTH_TOKEN_URL", GF_AUTH_URL_ROOT + "/token");
            envVariables.put("GF_AUTH_GENERIC_OAUTH_API_URL", GF_AUTH_URL_ROOT + "/userinfo");
            envVariables.put("STOP_SIGNAL", "SIGKILL");

            prepareDockerCompose();

            infrastructureContainer = new TestContainer("target/docker-compose/infra/docker-compose.yml", envVariables)
                  .withRemoveImages(DockerComposeContainer.RemoveImages.LOCAL);
            horreumContainer = new TestContainer("target/docker-compose/horreum-compose.yml", envVariables);

            log.info("Waiting for Horreum infrastructure to start");

            infrastructureContainer.start();

            waitForContainerReady(infrastructureContainer, "keycloak_1", "started in");
            waitForContainerReady(infrastructureContainer, "app-init_1", "Horreum initialization complete");

            horreumContainer.start();
            waitForContainerReady(horreumContainer, "horreum_1", "started in");
            RestAssured.port = Integer.parseInt(getOffsetPort(HORREUM_HTTP_PORT));
            RestAssured.baseURI = "http://" + CONTAINER_HOST_IP;
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        synchronized (HorreumTestExtension.class) {
            if (!started) {
                started = true;
                beforeSuite(context);
            }
        }
    }

    protected void beforeSuite(ExtensionContext context) throws Exception {
        startContainers();
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        close();
    }

    @Override
    public void close() {
        synchronized (HorreumTestExtension.class) {
            try {
                stopContainers();
                started = false;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void prepareDockerCompose() throws URISyntaxException, IOException {
        // this is where .env will be written
        Path.of("target/docker-compose/horreum-backend").toFile().mkdirs();
        Path.of("target/docker-compose/infra").toFile().mkdirs();
        URI root = HorreumTestExtension.class.getClassLoader().getResource("docker-compose").toURI();
        Path source;
        FileSystem fileSystem = null;
        try {
            if ("file".equals(root.getScheme())) {
                source = Path.of(root);
            } else {
                fileSystem = FileSystems.newFileSystem(root, Collections.emptyMap());
                source = fileSystem.getPath("docker-compose");
            }
            Files.walkFileTree(source, Collections.emptySet(), 1, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path destPath = Path.of("target/docker-compose/infra/", file.getFileName().toString());
                    Files.copy(file, destPath,
                          StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    if (file.toString().endsWith(".sh")) {
                        if (!destPath.toFile().setExecutable(true, false)) {
                            log.errorf("Could not set executable permissions on %s", destPath);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            //noinspection ConstantConditions
            Files.copy(HorreumTestExtension.class.getClassLoader().getResourceAsStream("testcontainers/horreum-compose.yml"),
                  Path.of("target/docker-compose/horreum-compose.yml"), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (fileSystem != null) {
                fileSystem.close();
            }
        }
    }

    private static void waitForContainerReady(DockerComposeContainer composeContainer, String serviceName, String pattern){
        ContainerState optionalContainer = (ContainerState) composeContainer.getContainerByServiceName(serviceName).orElse(null);

        if (optionalContainer != null ){
            WaitingConsumer waitingConsumer = new WaitingConsumer();

            LogUtils.followOutput(DockerClientFactory.instance().client(), optionalContainer.getContainerId(), waitingConsumer);
            try {
                waitingConsumer.waitUntil(
                        outputFrame -> outputFrame.getUtf8String().contains(pattern)
                        , ContainerStartTimeout
                        , ContainerStartTimeoutUnit
                        , ContainerStartRetries
                );
            } catch (TimeoutException e) {
                fail("Timed out waiting for " + serviceName + " container to start");
            }
        } else {
            fail( "Could not find container: " + serviceName);
        }
    }

    private static String getOffsetPort(int originalPortNumber){
        return Integer.toString(originalPortNumber + HORREUM_TEST_PORT_OFFSET);
    }

    public static void stopContainers() throws Exception {
        if (START_HORREUM_INFRA && HORREUM_DUMP_LOGS) {
            Optional<ContainerState> containerState = horreumContainer.getContainerByServiceName("horreum_1"); //TODO: dynamic resolve
            if (containerState.isPresent()) {
                String logs = containerState.get().getLogs(OutputFrame.OutputType.STDOUT);
                File tmpFile = File.createTempFile("horreum-client", ".log");
                BufferedWriter writer = new BufferedWriter(new FileWriter(tmpFile));
                writer.write(logs);
                writer.close();
                log.info("Logs written to: " + tmpFile.getAbsolutePath());
            }
        }
        if (STOP_HORREUM_INFRA) {
            stopContainerEnv(infrastructureContainer);
            stopContainerEnv(horreumContainer);
        }
        File grafanaEnv = new File("target/docker-compose/.grafana");
        if (grafanaEnv.exists()) {
            //noinspection ResultOfMethodCallIgnored
            grafanaEnv.delete();
        }
        File horreumEnv = new File("target/docker-compose/horreum-backend/.env");
        if (horreumEnv.exists()) {
            //noinspection ResultOfMethodCallIgnored
            horreumEnv.delete();
        }
    }

    private static void stopContainerEnv(TestContainer composeContainer){
        if (composeContainer != null) {
            composeContainer.stop();
        }
    }

    protected static String resourceToString(String resourcePath) {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            return new BufferedReader(new InputStreamReader(inputStream))
                    .lines().collect(Collectors.joining(" "));
        } catch (IOException e) {
            fail("Failed to read `" + resourcePath + "`", e);
            return null;
        }
    }

    private static class TestContainer extends DockerComposeContainer<TestContainer> {
        public TestContainer(String composeFile, Map<String, String> envVariables) {
            super(new File(composeFile));
            withLocalCompose(true);
            withEnv(envVariables);
        }
    }
    public static RequestSpecification bareRequest(){
        String access_token = keycloak.tokenManager().getAccessToken().getToken();
        return RestAssured.given().auth().oauth2(access_token);
    }
}
