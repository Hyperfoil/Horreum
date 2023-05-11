package io.hyperfoil.tools.horreum.api.services;

import java.util.List;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.api.data.ActionLog;
import io.hyperfoil.tools.horreum.api.alerting.DatasetLog;
import io.hyperfoil.tools.horreum.api.alerting.TransformationLog;

@Produces(MediaType.APPLICATION_JSON)
@Path("/api/log")
public interface LogService {
   @GET
   @Path("dataset/{source}/{testId}")
   List<DatasetLog> getDatasetLog(@PathParam("source") String source,
                                  @PathParam("testId") int testId,
                                  @QueryParam("level") @DefaultValue("1") int level,
                                  @QueryParam("datasetId") Integer datasetId,
                                  @QueryParam("page") Integer page,
                                  @QueryParam("limit") Integer limit);

   @GET
   @Path("dataset/{source}/{testId}/count")
   long getDatasetLogCount(@PathParam("source") String source,
                           @PathParam("testId") int testId,
                           @QueryParam("level") @DefaultValue("1") int level,
                           @QueryParam("datasetId") Integer datasetId);

   @DELETE
   @Path("dataset/{source}/{testId}")
   void deleteDatasetLogs(@PathParam("source") String source,
                          @PathParam("testId") int testId,
                          @QueryParam("datasetId") Integer datasetId,
                          @QueryParam("from") Long from,
                          @QueryParam("to") Long to);

   @GET
   @Path("transformation/{testId}")
   List<TransformationLog> getTransformationLog(@PathParam("testId") int testId,
                                                @QueryParam("level") @DefaultValue("1") int level,
                                                @QueryParam("runId") Integer runId,
                                                @QueryParam("page") Integer page,
                                                @QueryParam("limit") Integer limit);

   @GET
   @Path("transformation/{testId}/count")
   long getTransformationLogCount(@PathParam("testId") int testId,
                                  @QueryParam("level") @DefaultValue("1") int level,
                                  @QueryParam("runId") Integer runId);

   @DELETE
   @Path("transformation/{testId}")
   void deleteTransformationLogs(@PathParam("testId") int testId,
                                 @QueryParam("runId") Integer runId,
                                 @QueryParam("from") Long from,
                                 @QueryParam("to") Long to);

   @GET
   @Path("action/{testId}")
   List<ActionLog> getActionLog(@PathParam("testId") int testId,
                                @QueryParam("level") @DefaultValue("1") int level,
                                @QueryParam("page") Integer page,
                                @QueryParam("limit") Integer limit);

   @GET
   @Path("action/{testId}/count")
   long getActionLogCount(@PathParam("testId") int testId,
                          @QueryParam("level") @DefaultValue("1") int level);

   @DELETE
   @Path("action/{testId}")
   void deleteActionLogs(@PathParam("testId") int testId,
                         @QueryParam("from") Long from,
                         @QueryParam("to") Long to);
}
