package io.hyperfoil.tools.horreum.test;

import static java.lang.System.getProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

import org.eclipse.microprofile.config.ConfigProvider;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class PostgresResource implements QuarkusTestResourceLifecycleManager {
    private PostgreSQLContainer<?> postgresContainer;

    private Boolean inContainer = false;

    @Override
    public void init(Map<String, String> initArgs) {
        if (ConfigProvider.getConfig().getOptionalValue("horreum.test.postgres.enabled", boolean.class).orElse(true)) {
            postgresContainer = new PostgreSQLContainer<>(DockerImageName
                    .parse(getProperty("horreum.dev-services.postgres.image")).asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("horreum")
                    .withUsername("dbadmin")
                    .withPassword("secret");
        }
        if (initArgs.containsKey("inContainer")) {
            inContainer = Boolean.parseBoolean(initArgs.get("inContainer"));
        }
    }

    @Override
    public Map<String, String> start() {
        if (postgresContainer == null) {
            return Collections.emptyMap();
        }
        postgresContainer.start();
        try (Connection conn = DriverManager.getConnection(postgresContainer.getJdbcUrl(), "dbadmin", "secret")) {
            conn.createStatement().executeUpdate("CREATE ROLE appuser noinherit login password 'secret';");
            conn.createStatement().executeUpdate("CREATE ROLE keycloak noinherit login password 'secret';");
            conn.createStatement().executeUpdate("CREATE DATABASE keycloak WITH OWNER = 'keycloak';");
        } catch (SQLException t) {
            throw new RuntimeException(t);
        }

        String jdbcUrl = inContainer ? postgresContainer.getJdbcUrl().replaceAll("localhost", "172.17.0.1")
                : postgresContainer.getJdbcUrl();

        return Map.of(
                "postgres.container.name", postgresContainer.getContainerName(),
                "quarkus.datasource.jdbc.url", jdbcUrl,
                "quarkus.datasource.migration.jdbc.url", jdbcUrl);
    }

    @Override
    public void stop() {
        if (postgresContainer != null) {
            postgresContainer.stop();
        }
    }

}
