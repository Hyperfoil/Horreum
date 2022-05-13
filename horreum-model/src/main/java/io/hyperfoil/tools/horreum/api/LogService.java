package io.hyperfoil.tools.horreum.api;

import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.entity.alerting.DatasetLog;
import io.hyperfoil.tools.horreum.entity.alerting.TransformationLog;

@Produces(MediaType.APPLICATION_JSON)
@Path("/api/log")
public interface LogService {
   @GET
   @Path("dataset/{source}/{testId}")
   List<DatasetLog> getDatasetLog(@PathParam("source") String source,
                                  @PathParam("testId") int testId,
                                  @QueryParam("page") Integer page,
                                  @QueryParam("limit") Integer limit);

   @GET
   @Path("dataset/{source}/{testId}/count")
   long getDatasetLogCount(@PathParam("source") String source, @PathParam("testId") int testId);

   @DELETE
   @Path("dataset/{source}/{testId}")
   void deleteDatasetLogs(@PathParam("source") String source,
                          @PathParam("testId") int testId,
                          @QueryParam("from") Long from,
                          @QueryParam("to") Long to);

   @GET
   @Path("transformation/{testId}")
   List<TransformationLog> getTransformationLog(@PathParam("testId") int testId,
                                                @QueryParam("runId") Integer runId,
                                                @QueryParam("page") Integer page,
                                                @QueryParam("limit") Integer limit);

   @GET
   @Path("transformation/{testId}/count")
   long getTransformationLogCount(@PathParam("testId") int testId, @QueryParam("runId") Integer runId);

   @DELETE
   @Path("transformation/{testId}")
   void deleteTransformationLogs(@PathParam("testId") int testId,
                                 @QueryParam("runId") Integer runId,
                                 @QueryParam("from") Long from,
                                 @QueryParam("to") Long to);
}
