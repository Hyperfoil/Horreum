package io.hyperfoil.tools.horreum.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import org.testcontainers.containers.PostgreSQLContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class PostgresResource implements QuarkusTestResourceLifecycleManager {
   public PostgreSQLContainer postgresContainer = new PostgreSQLContainer("postgres:12")
         .withDatabaseName("horreum").withUsername("dbadmin").withPassword("secret");

   @Override
   public Map<String, String> start() {
      postgresContainer.start();
      try (Connection conn = DriverManager.getConnection(postgresContainer.getJdbcUrl(), "dbadmin", "secret")) {
         conn.createStatement().executeUpdate("CREATE ROLE appuser noinherit login password 'secret';");
         conn.createStatement().executeUpdate("CREATE ROLE keycloak noinherit login password 'secret';");
         conn.createStatement().executeUpdate("CREATE DATABASE keycloak WITH OWNER = 'keycloak';");
      } catch (SQLException t) {
         throw new RuntimeException(t);
      }
      return Map.of(
         "quarkus.datasource.jdbc.url", postgresContainer.getJdbcUrl(),
         "quarkus.datasource.migration.jdbc.url", postgresContainer.getJdbcUrl()
      );
   }

   @Override
   public void stop() {
      postgresContainer.stop();
   }
}
