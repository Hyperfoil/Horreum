package io.hyperfoil.tools.horreum.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.hyperfoil.tools.yaup.json.Json;

@Path("/api/sql")
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public interface SqlService {
   @GET
   @Path("testjsonpath")
   JsonpathValidation testJsonPath(@QueryParam("query") String jsonpath);

   @Path("roles")
   @GET
   @Produces("text/plain")
   String roles();

   class JsonpathValidation {
      public boolean valid;
      public String jsonpath;
      public int errorCode;
      public String sqlState;
      public String reason;
      public String sql;
   }
}
