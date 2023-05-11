package io.hyperfoil.tools.horreum.api.services;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import com.fasterxml.jackson.annotation.JsonProperty;

@Path("/api/sql")
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public interface SqlService {
   @GET
   @Path("testjsonpath")
   JsonpathValidation testJsonPath(@Parameter(required = true) @QueryParam("query") String jsonpath);

   @Path("roles")
   @GET
   @Produces("text/plain")
   String roles(@QueryParam("system") @DefaultValue("false") boolean system);

   class JsonpathValidation {
      @JsonProperty(required = true)
      public boolean valid;
      public String jsonpath;
      public int errorCode;
      public String sqlState;
      public String reason;
      public String sql;
   }
}
