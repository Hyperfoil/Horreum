package io.hyperfoil.tools.horreum.converter;

import org.hibernate.dialect.PostgreSQL95Dialect;

import java.sql.Types;

public class JsonPostgreSQLDialect extends PostgreSQL95Dialect {

   public JsonPostgreSQLDialect(){

      registerColumnType(Types.JAVA_OBJECT,"jsonb");

   }
}
