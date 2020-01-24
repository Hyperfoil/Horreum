package io.hyperfoil.tools.repo.api;

import io.agroal.api.AgroalDataSource;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.agroal.DataSource;

import javax.annotation.security.DenyAll;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.SQLException;

@DenyAll
@Path("/api/db")
@Consumes({MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public class DbService {

   @Inject
   @DataSource("timescale")
   AgroalDataSource timescaleDB;

   @Inject
   SqlService sqlService;

   @GET
   public void foo(){
      try {
         timescaleDB.getConnection().prepareCall("create table measurements (\n" +
            "\ttimestamp timestamptz,\n" +
            "\tvalue float8,\n" +
            "\tname varchar,\n" +
            "\tdimensions jsonb,\n" +
            "\tvalue_meta json\n" +
            ");\n" +
            "SELECT create_hypertable('measurements', 'timestamptz');").execute();
      } catch (SQLException e) {
         e.printStackTrace();
      }
   }

   public Json getTableSchema(String tableName){
      return sqlService.getTime("SELECT COLUMN_NAME,DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_NAME = '"+tableName+"'");
   }

   @POST
   @Transactional
   public Response addTable(Json json){
      String name = json.getString("name","series_"+System.currentTimeMillis());
      String columns = json.getString("columns","");

      try {
         timescaleDB
            .getConnection()
            .createStatement()
            .execute("CREATE table "+name+" ( time timestamptz,"+columns+"); SELECT create_hypertable('"+name+"', 'time');");
         return Response.ok(name).build();
      } catch (SQLException e) {
         e.printStackTrace();
         return Response.serverError().entity("Exception creating "+name+": "+e.getMessage()).build();

      }
   }
   @POST
   @Path("{table}")
   @Transactional
   public Response addEntry(@PathParam("table") String table, Json body){

      if(body == null){
         return Response.serverError().entity("Missing post json for "+table+":"+body).build();
      }
      if(!body.has("time")){
         return Response.serverError().entity("Missing time in json:"+body.toString()).build();
      }

      StringBuilder names = new StringBuilder();
      StringBuilder values = new StringBuilder();

      for(Object key : body.keys()){
         if(names.length()> 0){
            names.append(",");
            values.append(",");
         }
         names.append(key.toString());
         Object value = body.get(key);
         if(key.equals("time") && value instanceof Number){
            values.append("TO_TIMESTAMP("+value+"::double precision / 1000)");
         }else if(value instanceof Json || value instanceof String){
            values.append("'");
            values.append(value.toString());
            values.append("'");
         }else{
            values.append(value);
         }
      }

      String insert = "INSERT INTO "+table+" ("+names.toString()+") VALUES ("+values.toString()+");";

      try {
         timescaleDB
            .getConnection()
            .createStatement()
            .execute(insert);
         return Response.ok("").build();
      } catch (SQLException e) {
         e.printStackTrace();
         return Response.serverError().entity("Exception inserting into "+table+": "+e.getMessage()+"\n"+insert+"\n"+body.toString(2)).build();
      }
   }


}
