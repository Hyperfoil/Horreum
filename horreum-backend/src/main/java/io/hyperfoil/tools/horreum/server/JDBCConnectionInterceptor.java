package io.hyperfoil.tools.horreum.server;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.enterprise.context.ApplicationScoped;

import io.agroal.api.AgroalPoolInterceptor;
import io.hyperfoil.tools.horreum.svc.Roles;
import io.quarkus.logging.Log;

@ApplicationScoped
public class JDBCConnectionInterceptor implements AgroalPoolInterceptor {

    @Override
    public void onConnectionCreate(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT set_config('horreum.userroles', '" + Roles.HORREUM_SYSTEM + "', false)");
        } catch (SQLException e) {
            Log.warnf(e, "Unable to set default role '%s' on the JDBC connection", Roles.HORREUM_SYSTEM);
        }
    }
}
