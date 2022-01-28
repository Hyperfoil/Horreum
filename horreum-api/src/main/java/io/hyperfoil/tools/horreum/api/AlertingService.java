package io.hyperfoil.tools.horreum.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.RegressionDetection;
import io.hyperfoil.tools.horreum.entity.alerting.RunExpectation;
import io.hyperfoil.tools.horreum.entity.alerting.Variable;

@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
@Path("/api/alerting")
public interface AlertingService {
   @GET
   @Path("variables")
   List<Variable> variables(@QueryParam("test") Integer testId);

   @POST
   @Path("variables")
   void variables(@QueryParam("test") Integer testId, List<Variable> variables);

   @GET
   @Path("dashboard")
   DashboardInfo dashboard(@QueryParam("test") Integer testId, @QueryParam("tags") String tags);

   @GET
   @Path("changes")
   List<Change> changes(@QueryParam("var") Integer varId);

   @POST
   @Path("change/{id}")
   void updateChange(@PathParam("id") Integer id, Change change);

   @DELETE
   @Path("change/{id}")
   void deleteChange(@PathParam("id") Integer id);

   @POST
   @Path("recalculate")
   void recalculate(@QueryParam("test") Integer testId, @QueryParam("notify") boolean notify,
                    @QueryParam("debug") boolean debug, @QueryParam("from") Long from, @QueryParam("to") Long to);

   @GET
   @Path("recalculate")
   RecalculationStatus recalculateProgress(@QueryParam("test") Integer testId);

   @POST
   @Path("/datapoint/last")
   List<DatapointLastTimestamp> findLastDatapoints(LastDatapointsParams params);

   @POST
   @Path("/expectRun")
   void expectRun(@QueryParam("test") String test,
                  @QueryParam("timeout") Long timeoutSeconds,
                  @QueryParam("tags") String tags,
                  @QueryParam("expectedby") String expectedBy,
                  @QueryParam("backlink") String backlink);

   // Test mode only
   @GET
   @Path("/expectations")
   List<RunExpectation> expectations();

   @GET
   @Path("/models")
   List<RegressionModelConfig> models();

   @GET
   @Path("/defaultRegressionConfigs")
   List<RegressionDetection> defaultRegressionConfigs();

   class DashboardInfo {
      public int testId;
      public String uid;
      public String url;
      public List<PanelInfo> panels = new ArrayList<>();
   }

   class PanelInfo {
      public String name;
      public List<Variable> variables;

      public PanelInfo(String name, List<Variable> variables) {
         this.name = name;
         this.variables = variables;
      }
   }

   class AccessorInfo {
      public final String schema;
      public final String jsonpath;

      public AccessorInfo(String schema, String jsonpath) {
         this.schema = schema;
         this.jsonpath = jsonpath;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         AccessorInfo that = (AccessorInfo) o;
         return schema.equals(that.schema);
      }

      @Override
      public int hashCode() {
         return schema.hashCode();
      }
   }

   class RecalculationStatus {
      public int percentage;
      public boolean done;
      public Integer totalRuns;
      public Integer errors;
      public Set<Integer> runsWithoutAccessor;
      public Set<Integer> runsWithoutValue;
   }

   class DatapointLastTimestamp {
      public int variable;
      public Number timestamp;
   }

   class LastDatapointsParams {
      public int[] variables;
      public String tags;
   }
}
