package io.hyperfoil.tools.horreum.infra.common.resources;

import io.hyperfoil.tools.horreum.infra.common.ResourceLifecycleManager;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static io.hyperfoil.tools.horreum.infra.common.Const.*;

public class PostgresResource implements ResourceLifecycleManager {
    public static final String POSTGRES_CONFIG_PROPERTIES = "postgres.config.properties";
    private HorreumPostgreSQLContainer<?> postgresContainer;

    private Boolean inContainer = false;
    private String networkAlias = "";

    @Override
    public void init(Map<String, String> initArgs) {
        boolean startDevService = !initArgs.containsKey(HORREUM_DEV_POSTGRES_ENABLED) || (initArgs.containsKey(HORREUM_DEV_POSTGRES_ENABLED) && Boolean.parseBoolean(initArgs.get(HORREUM_DEV_POSTGRES_ENABLED)));
        if ( startDevService ) {
            if ( !initArgs.containsKey(HORREUM_DEV_POSTGRES_IMAGE) ) {
                throw new RuntimeException("Arguments did not contain Postgres image");
            }

            final String POSTGRES_IMAGE = initArgs.get(HORREUM_DEV_POSTGRES_IMAGE);

            networkAlias = initArgs.get(HORREUM_DEV_POSTGRES_NETWORK_ALIAS);

            //TODO: do not hard code these values
            postgresContainer = new HorreumPostgreSQLContainer<>(POSTGRES_IMAGE)
                    .withDatabaseName("horreum")
                    .withUsername("dbadmin")
                    .withPassword("secret")
                    ;

            String resourceName = POSTGRES_CONFIG_PROPERTIES; // could also be a constant
            Properties props = new Properties();
            try(InputStream resourceStream =  Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
                props.load(resourceStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            props.forEach( (key, val) -> postgresContainer.withParameter(key.toString().concat("=").concat(val.toString())));
        }


        if (initArgs.containsKey("inContainer") ) {
            inContainer = Boolean.parseBoolean(initArgs.get("inContainer"));
        }
    }

    @Override
    public Map<String, String> start(Optional<Network> network) {
        if (postgresContainer == null) {
            return Collections.emptyMap();
        }

        if ( network.isPresent() ){
            postgresContainer.withNetwork(network.get());
            postgresContainer.withNetworkAliases(networkAlias);
        }

        postgresContainer.start();
        try (Connection conn = DriverManager.getConnection(postgresContainer.getJdbcUrl(), "dbadmin", "secret")) {
            conn.createStatement().executeUpdate("CREATE ROLE appuser noinherit login password 'secret';");
            conn.createStatement().executeUpdate("CREATE ROLE keycloak noinherit login password 'secret';");
            conn.createStatement().executeUpdate("CREATE DATABASE keycloak WITH OWNER = 'keycloak';");
        } catch (SQLException t) {
            throw new RuntimeException(t);
        }
        String postgresContainerName = postgresContainer.getContainerName().replaceAll("/", "");
        Integer port = postgresContainer.getMappedPort(5432);
        String jdbcUrl = inContainer ? postgresContainer.getJdbcUrl()
                .replaceAll("localhost", networkAlias)
                .replaceAll(port.toString(), "5432") : postgresContainer.getJdbcUrl();

        return Map.of(
                "postgres.container.name", postgresContainerName,
                "quarkus.datasource.jdbc.url", postgresContainer.getJdbcUrl(),
                "quarkus.datasource.migration.jdbc.url", postgresContainer.getJdbcUrl(),
                "quarkus.datasource.jdbc.url.internal", jdbcUrl
        );
    }

    @Override
    public void stop() {
        if (postgresContainer != null) {
            postgresContainer.stop();
        }
    }

    public String getJdbcUrl(){
        return postgresContainer.getJdbcUrl();
    }

    public PostgreSQLContainer getContainer(){
        return  this.postgresContainer;
    }

}
