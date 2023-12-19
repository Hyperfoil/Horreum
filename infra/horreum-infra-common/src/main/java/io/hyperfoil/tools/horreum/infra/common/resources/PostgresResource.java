package io.hyperfoil.tools.horreum.infra.common.resources;

import io.hyperfoil.tools.horreum.infra.common.ResourceLifecycleManager;
import org.jboss.logging.Logger;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.SelinuxContext;

import java.io.File;
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
    private static final Logger log = Logger.getLogger(PostgresResource.class);
    public static final String POSTGRES_CONFIG_PROPERTIES = "postgres.config.properties";
    private HorreumPostgreSQLContainer<?> postgresContainer;

    private Boolean inContainer = false;
    private Boolean prodBackup = false;
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

            postgresContainer = new HorreumPostgreSQLContainer<>(POSTGRES_IMAGE, initArgs.containsKey(HORREUM_DEV_POSTGRES_BACKUP) ? 1 : 2)
                    .withDatabaseName(initArgs.get(HORREUM_DEV_DB_DATABASE))
                    .withUsername(initArgs.get(HORREUM_DEV_DB_USERNAME))
                    .withPassword(initArgs.get(HORREUM_DEV_DB_PASSWORD))
            ;

            if ( initArgs.containsKey(HORREUM_DEV_POSTGRES_BACKUP) ) {
                checkIfDirectoryIsEmtpy(initArgs.get(HORREUM_DEV_POSTGRES_BACKUP));

                postgresContainer.addFileSystemBind(initArgs.get(HORREUM_DEV_POSTGRES_BACKUP), "/var/lib/postgresql/data", BindMode.READ_WRITE, SelinuxContext.SHARED);
                prodBackup = true;
            }

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

    private void checkIfDirectoryIsEmtpy(String directoryPath) {
        File dir = new File(directoryPath);
        if (!dir.exists()) {
            throw new RuntimeException("Directory " + directoryPath + " does not exist!");
        }
        if(!dir.isDirectory()) {
            throw new RuntimeException("Directory " + directoryPath + " is not a directory!");
        }
        try {
            if (dir.canRead() && dir.list() == null) {
                throw new RuntimeException("Directory " + directoryPath + " does not contain any files!");
            }
        } catch (SecurityException se){
            log.warnf("Directory %s does not have correct permissions to verify!", directoryPath);
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
        if ( !prodBackup ) {
            try (Connection conn = DriverManager.getConnection(postgresContainer.getJdbcUrl(), "dbadmin", "secret")) {
                conn.createStatement().executeUpdate("CREATE ROLE appuser noinherit login password 'secret';");
                conn.createStatement().executeUpdate("CREATE ROLE keycloak noinherit login password 'secret';");
                conn.createStatement().executeUpdate("CREATE DATABASE keycloak WITH OWNER = 'keycloak';");
                conn.createStatement().executeUpdate("GRANT ALL ON SCHEMA public TO keycloak;");
            } catch (SQLException t) {
                throw new RuntimeException(t);
            }
        }
        String postgresContainerName = postgresContainer.getContainerName().replaceAll("/", "");
        Integer port = postgresContainer.getMappedPort(5432);
        String jdbcUrl = inContainer ? postgresContainer.getJdbcUrl()
                .replaceAll("localhost", networkAlias)
                .replaceAll(port.toString(), "5432") : postgresContainer.getJdbcUrl();

        return Map.of(
                "postgres.container.name", postgresContainerName,
                "postgres.container.port", port.toString(),
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
