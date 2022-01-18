package io.hyperfoil.tools.horreum.api;

import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.entity.alerting.CalculationLog;

@Produces(MediaType.APPLICATION_JSON)
@Path("/api/log")
public interface LogService {
   @GET
   @Path("{source}/{testId}")
   List<CalculationLog> getCalculationLog(@PathParam("source") String source,
                                          @PathParam("testId") Integer testId,
                                          @QueryParam("page") Integer page,
                                          @QueryParam("limit") Integer limit);

   @GET
   @Path("{source}/{testId}/count")
   long getLogCount(@PathParam("source") String source, @PathParam("testId") Integer testId);

   @DELETE
   @Path("{source}/{testId}")
   void deleteLogs(@PathParam("source") String source,
                   @PathParam("testId") Integer testId,
                   @QueryParam("from") Long from,
                   @QueryParam("to") Long to);

}
