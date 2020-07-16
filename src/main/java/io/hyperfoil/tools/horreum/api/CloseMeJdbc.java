package io.hyperfoil.tools.horreum.api;

import java.sql.SQLException;

public interface CloseMeJdbc extends AutoCloseable {
   @Override
   void close() throws SQLException;
}
