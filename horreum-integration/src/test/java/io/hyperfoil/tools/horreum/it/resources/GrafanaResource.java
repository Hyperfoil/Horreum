package io.hyperfoil.tools.horreum.it.resources;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

public class GrafanaResource implements QuarkusTestResourceLifecycleManager {
    public GenericContainer<?> grafanaContainer;

    public static final String GRAFANA_IMAGE = "docker.io/grafana/grafana";

    @Override
    public void init(Map<String, String> initArgs) {
        URL grafanaScript = Thread.currentThread().getContextClassLoader().getResource("grafana/grafana.sh");

        grafanaContainer = new GenericContainer<>(
                new ImageFromDockerfile().withDockerfileFromBuilder(builder ->
                                builder
                                        .from(GRAFANA_IMAGE)
                                        .env("GF_INSTALL_PLUGINS", "simpod-json-datasource")
                                        .env("GF_LOG_LEVEL", "debug")
                                        .env("GF_SERVER_ROOT_URL", "http://localhost:4040/")
                                        .env("GF_SERVER_HTTP_PORT", "4040")
                                        .env("GF_SERVER_HTTP_ADDR", "127.0.0.1")
                                        .env("GF_USERS_DEFAULT_THEME", "light")
                                        .env("GF_SECURITY_ALLOW_EMBEDDING", "true")
                                        .env("GF_AUTH_DISABLE_LOGIN_FORM", "true")
                                        .env("GF_AUTH_OAUTH_AUTO_LOGIN", "true")
                                        .env("GF_AUTH_GENERIC_OAUTH_ENABLED", "true")
                                        .env("GF_AUTH_GENERIC_OAUTH_CLIENT_ID", "grafana")
                                        .env("GF_AUTH_GENERIC_OAUTH_SCOPES", "openid profile email")
                                        .env("GF_AUTH_GENERIC_OAUTH_ALLOW_SIGN_UP", "false")
                                        //TODO:: pass in the correct URLS from keycloak
                                        .env("GF_AUTH_GENERIC_OAUTH_AUTH_URL", "http://localhost:8180/realms/horreum/protocol/openid-connect/auth")
                                        .env("GF_AUTH_GENERIC_OAUTH_TOKEN_URL", "http://localhost:8180/realms/horreum/protocol/openid-connect/token")
                                        .env("GF_AUTH_GENERIC_OAUTH_API_URL", "http://localhost:8180/realms/horreum/protocol/openid-connect/userinfo")
//                                        .entryPoint("/tmp/grafana.sh")
                                        .entryPoint("/run.sh")
                                        .build())
                        .withFileFromFile("/tmp/grafana.sh", new File(grafanaScript.getPath()))
        ).withExposedPorts(4040);

//               .withDatabaseName("horreum").withUsername("dbadmin").withPassword("secret");
    }

    @Override
    public Map<String, String> start() {
        if (grafanaContainer == null) {
            return Collections.emptyMap();
        }
        grafanaContainer.start();

        String mappedPort = grafanaContainer.getMappedPort(4040).toString();

        return Map.of(
                "horreum.grafana/mp-rest/url", "http://172.17.0.1:".concat(mappedPort),
                "horreum.grafana.url", "http://172.17.0.1:".concat(mappedPort),
                "grafana.port", mappedPort
        );
    }

    @Override
    public void stop() {
        if (grafanaContainer != null) {
            grafanaContainer.stop();
        }
    }
}
