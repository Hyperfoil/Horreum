package io.hyperfoil.tools;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

import io.hyperfoil.tools.horreum.entity.json.Test;

public class HorreumTestBase {

    public static Properties configProperties;
    public static final String HORREUM_KEYCLOAK_BASE_URL;
    public static final String HORREUM_BASE_URL;

    public static final String HORREUM_USERNAME;
    public static final String HORREUM_PASSWORD;
    private static final Integer HORREUM_TEST_PORT_OFFSET;

    private static final String CONTAINER_HOST_IP;

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

    protected static HorreumClient horreumClient;
    protected static Test dummyTest;

    private static Logger log = Logger.getLogger(HorreumTestBase.class);

    static {
        configProperties = new Properties();
        InputStream propertyStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("env.properties"); //TODO: make configurable
        try {
            if (propertyStream != null) {
                configProperties.load(propertyStream);

                HORREUM_TEST_PORT_OFFSET = Integer.parseInt(getProperty("test.port.offset"));
                CONTAINER_HOST_IP=getProperty("container.host.ip");

                HORREUM_KEYCLOAK_BASE_URL = "http://" + CONTAINER_HOST_IP + ":" + getOffsetPort(KEYCLOAK_PORT);
                HORREUM_BASE_URL = "http://127.0.0.1:" + getOffsetPort(HORREUM_HTTP_PORT);

                HORREUM_USERNAME = getProperty("horreum.username");
                HORREUM_PASSWORD = getProperty("horreum.password");

                START_HORREUM_INFRA = Boolean.parseBoolean(getProperty("horreum.start-infra"));
                STOP_HORREUM_INFRA = Boolean.parseBoolean(getProperty("horreum.stop-infra"));
                HORREUM_DUMP_LOGS = Boolean.parseBoolean(getProperty("horreum.dump-logs"));
            } else {
                throw new RuntimeException("Could not load test configuration");
            }
        } catch (IOException ioException) {
            throw new RuntimeException("Failed to load configuration properties");
        }
    }

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
        return configProperties.getProperty(propertyName).trim();
    }

    @BeforeAll
    public static void startContainers() throws URISyntaxException, IOException {
        log.info("Starting Infra: " + START_HORREUM_INFRA);
        if (START_HORREUM_INFRA) {

            String PORT_OFFSET = getOffsetPort(0);

            Map<String, String> envVariables = new HashMap<>();

            String CONTAINER_HOST_HTTP_ROOT = "http://" + CONTAINER_HOST_IP + ":";
            String KEYCLOAK_OFFSET_PORT = getOffsetPort(KEYCLOAK_PORT);
            String KEYCLOAK_URL_ROOT = "http://172.17.0.1:" + KEYCLOAK_OFFSET_PORT;

            envVariables.put("CONTAINER_HOST_IP", CONTAINER_HOST_IP);
            envVariables.put("PORT_OFFSET", PORT_OFFSET);
            envVariables.put("RESOURCES_PATH", ".");
            envVariables.put("HORREUM_HTTPS_PORT", getOffsetPort(HORREUM_HTTPS_PORT));
            envVariables.put("HORREUM_HTTP_PORT", getOffsetPort(HORREUM_HTTP_PORT ));
            envVariables.put("GRAFANA_HTTP_PORT", getOffsetPort(GRAFANA_HTTP_PORT ));
            envVariables.put("KEYCLOAK_HTTP_PORT", KEYCLOAK_OFFSET_PORT);
            envVariables.put("POSTGRES_PORT", getOffsetPort(POSTGRES_PORT));
            envVariables.put("HORREUM_HORREUM_INTERNAL_URL", CONTAINER_HOST_HTTP_ROOT + getOffsetPort(HORREUM_HTTPS_PORT));
            envVariables.put("HORREUM_HORREUM_KEYCLOAK_URL",  KEYCLOAK_URL_ROOT + "/auth");
            envVariables.put("HORREUM_HORREUM_URL", CONTAINER_HOST_HTTP_ROOT + getOffsetPort(HORREUM_HTTP_PORT));
            envVariables.put("HORREUM_QUARKUS_OIDC_AUTH_SERVER_URL", KEYCLOAK_URL_ROOT + "/auth/realms/horreum");
            envVariables.put("HORREUM_QUARKUS_DATASOURCE_JDBC_URL", "jdbc:postgresql://" + CONTAINER_HOST_IP + ":" + getOffsetPort(POSTGRES_PORT) + "/horreum");

            envVariables.put("GRAFANA_GF_SERVER_ROOT_URL", CONTAINER_HOST_HTTP_ROOT + getOffsetPort(GRAFANA_HTTP_PORT ) + "/");

            String GF_AUTH_URL_ROOT = KEYCLOAK_URL_ROOT + "/auth/realms/horreum/protocol/openid-connect";
            envVariables.put("GF_AUTH_GENERIC_OAUTH_AUTH_URL", GF_AUTH_URL_ROOT + "/auth");
            envVariables.put("GF_AUTH_GENERIC_OAUTH_TOKEN_URL", GF_AUTH_URL_ROOT + "/token");
            envVariables.put("GF_AUTH_GENERIC_OAUTH_API_URL", GF_AUTH_URL_ROOT + "/userinfo");

            prepareDockerCompose();

            infrastructureContainer = new TestContainer("target/docker-compose/docker-compose.yml", envVariables);
            horreumContainer = new TestContainer("target/docker-compose/horreum-compose.yml", envVariables);

            log.info("Waiting for Horreum infrastructure to start");

            infrastructureContainer.start();

            waitForContainerReady(infrastructureContainer, "keycloak_1", "started in");
            waitForContainerReady(infrastructureContainer, "app-init_1", "Horreum initialization complete");

            horreumContainer.start();
            waitForContainerReady(horreumContainer, "horreum_1", "started in");
        }
    }

    private static void prepareDockerCompose() throws URISyntaxException, IOException {
        // this is where .env will be written
        Path.of("target/docker-compose/horreum-backend").toFile().mkdirs();
        URI root = HorreumTestBase.class.getClassLoader().getResource("docker-compose").toURI();
        Path source;
        if ("file".equals(root.getScheme())) {
            source = Path.of(root);
        } else {
            FileSystem fileSystem = FileSystems.newFileSystem(root, Collections.emptyMap());
            source = fileSystem.getPath(".");
        }
        Files.walkFileTree(source, Collections.emptySet(), 1, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, Path.of("target/docker-compose/", file.getFileName().toString()),
                      StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
        //noinspection ConstantConditions
        Files.copy(HorreumTestBase.class.getClassLoader().getResourceAsStream("testcontainers/horreum-compose.yml"),
              Path.of("target/docker-compose/horreum-compose.yml"), StandardCopyOption.REPLACE_EXISTING);
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

    @AfterAll
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
    }

    private static void stopContainerEnv(TestContainer composeContainer){
        if (composeContainer != null) {
            composeContainer.stop();
        }
    }

    @BeforeAll
    private static void initialiseRestClients() {
       horreumClient = new HorreumClient.Builder()
             .horreumUrl(HORREUM_BASE_URL + "/")
             .keycloakUrl(HORREUM_KEYCLOAK_BASE_URL)
             .horreumUser(HORREUM_USERNAME)
             .horreumPassword(HORREUM_PASSWORD)
             .build();

       assertNotNull(horreumClient);
    }

    protected static void createOrLookupTest() {
       boolean createTest = Boolean.parseBoolean(getProperty("horreum.create-test"));
       if (createTest) {
          Test test = new Test();
          test.name = "Dummy5";
          test.owner = "dev-team";
          test.description = "This is a dummy test";
          dummyTest = horreumClient.testService.add(test);
       } else {
          // TODO: id from configuration?
          dummyTest = horreumClient.testService.get(10, null);
       }
       assertNotNull(dummyTest);
    }

    protected static String resourceToString(String resourcePath) {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);) {
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
}
