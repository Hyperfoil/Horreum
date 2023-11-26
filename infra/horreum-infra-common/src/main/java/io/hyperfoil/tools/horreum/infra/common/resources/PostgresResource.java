package io.hyperfoil.tools.horreum.infra.common.resources;

import io.hyperfoil.tools.horreum.infra.common.ResourceLifecycleManager;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.SelinuxContext;
import org.testcontainers.images.builder.Transferable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.zip.Checksum;

import static io.hyperfoil.tools.horreum.infra.common.Const.*;

public class PostgresResource implements ResourceLifecycleManager {
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

            if (initArgs.containsKey(HORREUM_DEV_POSTGRES_BACKUP)) {
                checkIfDirectoryIsEmtpy(initArgs.get(HORREUM_DEV_POSTGRES_BACKUP));

                postgresContainer.addFileSystemBind(initArgs.get(HORREUM_DEV_POSTGRES_BACKUP), "/var/lib/postgresql/data", BindMode.READ_WRITE, SelinuxContext.SHARED);
                prodBackup = true;
            }

            Properties props = new Properties();
            try(InputStream resourceStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(POSTGRES_CONFIG_PROPERTIES)) {
                props.load(resourceStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            props.forEach((key, val) -> postgresContainer.withParameter(key.toString().concat("=").concat(val.toString())));

            // SSL configuration from https://www.postgresql.org/docs/current/ssl-tcp.html
            if (initArgs.containsKey(HORREUM_DEV_POSTGRES_SSL_CERTIFICATE) && initArgs.containsKey(HORREUM_DEV_POSTGRES_SSL_CERTIFICATE_KEY)) {
                String certFile = "/var/lib/postgresql/server.crt", keyFile = "/var/lib/postgresql/server.key";
                postgresContainer.withParameter("ssl=on");
                postgresContainer.withParameter("ssl_cert_file=" + certFile);
                postgresContainer.withParameter("ssl_key_file=" + keyFile);
                postgresContainer.withCopyToContainer(postgresTransferable(initArgs.get(HORREUM_DEV_POSTGRES_SSL_CERTIFICATE)), certFile);
                postgresContainer.withCopyToContainer(postgresTransferable(initArgs.get(HORREUM_DEV_POSTGRES_SSL_CERTIFICATE_KEY), 0_600), keyFile);
            }
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
        if (dir.list().length == 0) {
            throw new RuntimeException("Directory " + directoryPath + " does not contain any files!");
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

    // --- //

    private static Transferable postgresTransferable(String string) {
        return postgresTransferable(string, Transferable.DEFAULT_FILE_MODE);
    }

    // specialization of org.testcontainers.images.builder.Transferable.of(byte[], int) that sets the right UID and GID
    // this is necessary as postgres verifies the permissions and ownership of the certificate and key files on boot
    private static Transferable postgresTransferable(String string, int fileMode) {
        byte[] content = string.getBytes(StandardCharsets.UTF_8);
        return new Transferable() {
            @Override public long getSize() {
                return content.length;
            }

            @Override public byte[] getBytes() {
                return content;
            }

            @Override public int getFileMode() {
                return fileMode;
            }

            @Override
            public void updateChecksum(Checksum checksum) {
                checksum.update(content, 0, content.length);
            }

            @Override public void transferTo(TarArchiveOutputStream tarArchiveOutputStream, String destination) {
                try {
                    tarArchiveOutputStream.putArchiveEntry(createTarArchiveEntry(destination));
                    tarArchiveOutputStream.write(getBytes());
                    tarArchiveOutputStream.closeArchiveEntry();
                } catch (IOException e) {
                    throw new RuntimeException("Can't transfer " + getDescription(), e);
                }
            }

            private TarArchiveEntry createTarArchiveEntry(String destination) {
                TarArchiveEntry tarEntry = new TarArchiveEntry(destination);
                tarEntry.setSize(getSize());
                tarEntry.setMode(getFileMode());

                // from the dockerfiles at https://github.com/docker-library/postgres (and see also https://hub.docker.com/_/postgres)
                // 999 are the defaults for Debian based images (the standard ones), while Alpine images use 70
                tarEntry.setIds(999, 999);
                tarEntry.setNames("postgres", "postgres");
                return tarEntry;
            }
        };
    }

}
