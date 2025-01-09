package io.hyperfoil.tools.horreum.exp.valid;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;

import io.hyperfoil.tools.horreum.liquibase.ComposableMigration;

/**
 * WARNING :: DO NOT merge into master
 *
 * This is NOT for master. this is to test the migration on production data backups.
 * A better interface is needed before merging to master.
 * T
 */
public class RunMigrationValidator {

    public static void main(String... args) throws SQLException, ClassNotFoundException {
        System.out.println("args: " + Arrays.asList(args));
        if (args.length < 3) {
            System.out.println("required args: jdbcUrl username password");
            System.exit(1);
        }
        String jdbcUrl = args[0];
        String username = args[1];
        String password = args[2];

        Connection conn = getConnection(jdbcUrl, username, password);
        System.out.println("purging");
        purge(conn);
        System.out.println("purged");
        ComposableMigration.migrate(conn);

    }

    /**
     * get a JDBC connection to the postgres database
     * @param jdbcUrl
     * @param username
     * @param password
     * @return
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    public static Connection getConnection(String jdbcUrl, String username, String password)
            throws SQLException, ClassNotFoundException {
        Class.forName("org.postgresql.Driver");
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    /**
     * This removes all the "exp" content so that it can be reloaded from the existing Horreum model without conflicting ids.
     * @param conn
     * @return
     */
    public static boolean purge(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(
                "delete from exp_label_values; delete from exp_run; delete from exp_extractor; delete from exp_label; delete from exp_labelgroup; delete from exp_label_reducers;delete from exp_temp_map_tests; delete from exp_temp_map_schema; delete from exp_temp_map_transform; ")) {
            return ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
