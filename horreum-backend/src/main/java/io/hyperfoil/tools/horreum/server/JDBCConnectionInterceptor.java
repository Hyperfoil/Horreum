package io.hyperfoil.tools.horreum.server;

import io.agroal.api.AgroalPoolInterceptor;
import io.hyperfoil.tools.horreum.svc.Roles;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class JDBCConnectionInterceptor implements AgroalPoolInterceptor {

   @Override public void onConnectionCreate(Connection connection) {
       try (Statement statement = connection.createStatement()) {
           statement.execute("SELECT set_config('horreum.userroles', '" + Roles.HORREUM_SYSTEM + "', false)");
       } catch (SQLException e) {
          Log.warnv(e, "Unable to set default role " + Roles.HORREUM_SYSTEM + " on the JDBC connection.");
       }
   }
}
