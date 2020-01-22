package io.hyperfoil.tools.repo.api;

import io.agroal.api.AgroalDataSource;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.agroal.DataSource;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.lang.Exception;

@Path("/api/sql")
@Consumes({MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class SqlService {

   @Inject
   AgroalDataSource dataSource;
   @Inject
   @DataSource("timescale")
   AgroalDataSource timescaleDB;

   public SqlService() {
      System.out.println("created a new SQLSERVICE");
   }

   @GET
   @Path("time")
   public Json getTime(@QueryParam("q") String sql) {
      return query(timescaleDB, sql);
   }

   @GET
   public Json get(@QueryParam("q") String sql) {
      return query(dataSource, sql);
   }

   private Json query(AgroalDataSource agroalDataSource, String sql) {
      System.out.println("SqlService.sql " + sql);
      if (sql == null || sql.trim().isEmpty()) {
         return new Json();
      }
      try (Connection connection = agroalDataSource.getConnection()) {
         try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            try (ResultSet resultSet = statement.getResultSet()) {
               Json json = fromResultSet(resultSet);
               return json;
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
         return Json.fromThrowable(e);
      }
   }

   public static Json fromResultSet(ResultSet resultSet) throws SQLException {
      Json rtrn = new Json(false);
      Map<String, Integer> names = new HashMap<>();
      ResultSetMetaData rsmd = resultSet.getMetaData();
      int columnCount = rsmd.getColumnCount();
      for (int i = 1; i <= columnCount; i++) {
         String name = rsmd.getColumnName(i);
         names.put(name, rsmd.getColumnType(i));
      }

      while (resultSet.next()) {
         Json entry = new Json();
         for (String name : names.keySet()) {
            Object value = getValue(resultSet, name, names.get(name));
            entry.set(name, value );
         }
         rtrn.add(entry);
      }
      return rtrn;
   }

   public static Object getValue(ResultSet resultSet, String column, int type) throws SQLException {
      switch (type) {

         case Types.DATE:
         case Types.TIME:
         case Types.TIMESTAMP:
         case Types.TIMESTAMP_WITH_TIMEZONE:
            return resultSet.getTimestamp(column).getTime();
         case Types.JAVA_OBJECT:
            Object obj = resultSet.getObject(column);
            if (obj == null) {
               return "";
            } else {
               return Json.fromString(obj.toString());
            }

         case Types.TINYINT:
         case Types.SMALLINT:
         case Types.INTEGER:
         case Types.BIGINT:
            return resultSet.getLong(column);
         case Types.OTHER:
            String str = StringUtil.removeQuotes(resultSet.getString(column));
            if (Json.isJsonLike(str)) {
               return Json.fromString(str);
            } else {
               return str;
            }
         case Types.BIT:
         case Types.BOOLEAN:
            return resultSet.getBoolean(column);
         default:
            String def = StringUtil.removeQuotes(resultSet.getString(column));
            return def;
      }
   }
}
