package io.hyperfoil.tools.horreum.api.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.api.ConditionConfig;
import io.hyperfoil.tools.horreum.api.alerting.*;
import io.hyperfoil.tools.horreum.api.data.DataSet;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import com.fasterxml.jackson.annotation.JsonProperty;

@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
@Path("/api/alerting")
public interface AlertingService {
   @GET
   @Path("variables")
   List<Variable> variables(@QueryParam("test") Integer testId);

   @POST
   @Path("variables")
   void updateVariables(@Parameter(required = true) @QueryParam("test") int testId,
                        @RequestBody(required = true) List<Variable> variables);

   @GET
   @Path("dashboard")
   DashboardInfo dashboard(@Parameter(required = true) @QueryParam("test") int testId,
                           @QueryParam("fingerprint") String fingerprint);

   @GET
   @Path("changes")
   List<Change> changes(@Parameter(required = true) @QueryParam("var") int varId,
                        @QueryParam("fingerprint") String fingerprint);

   @POST
   @Path("change/{id}")
   void updateChange(@Parameter(required = true) @PathParam("id") int id,
                     @RequestBody(required = true) Change change);

   @DELETE
   @Path("change/{id}")
   void deleteChange(@PathParam("id") int id);

   @POST
   @Path("recalculate")
   void recalculateDatapoints(@Parameter(required = true) @QueryParam("test") int testId,
                              @QueryParam("notify") boolean notify,
                              @QueryParam("debug") boolean debug,
                              @QueryParam("from") Long from, @QueryParam("to") Long to);

   @GET
   @Path("recalculate")
   DatapointRecalculationStatus getRecalculationStatus(@Parameter(required = true) @QueryParam("test") int testId);

   @POST
   @Path("/datapoint/last")
   List<DatapointLastTimestamp> findLastDatapoints(@RequestBody(required = true) LastDatapointsParams params);

   @POST
   @Path("/expectRun")
   void expectRun(@Parameter(required = true) @QueryParam("test") String test,
                  @Parameter(required = true) @QueryParam("timeout") Long timeoutSeconds,
                  @QueryParam("expectedby") String expectedBy,
                  @QueryParam("backlink") String backlink);

   // Test mode only
   @GET
   @Path("/expectations")
   List<RunExpectation> expectations();

   @POST
   @Path("/changeDetection")
   void updateChangeDetection(@QueryParam("testId") @Parameter(required = true) int testId, @RequestBody(required = true) AlertingService.ChangeDetectionUpdate update);

   @GET
   @Path("/changeDetectionModels")
   List<ConditionConfig> changeDetectionModels();

   @GET
   @Path("/defaultChangeDetectionConfigs")
   List<ChangeDetection> defaultChangeDetectionConfigs();

   @GET
   @Path("/missingdatarule")
   List<MissingDataRule> missingDataRules(@Parameter(required = true) @QueryParam("testId") int testId);

   @POST
   @Path("/missingdatarule")
   int updateMissingDataRule(
         @Parameter(required = true) @QueryParam("testId") int testId,
         @RequestBody(required = true) MissingDataRule rule);

   @DELETE
   @Path("/missingdatarule/{id}")
   void deleteMissingDataRule(@PathParam("id") int id);

   class DashboardInfo {
      @JsonProperty(required = true)
      public int testId;
      @NotNull
      public String uid;
      @NotNull
      public String url;
      @NotNull
      public List<PanelInfo> panels = new ArrayList<>();
   }

   class PanelInfo {
      @NotNull
      public String name;
      @NotNull
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

   class DatapointRecalculationStatus {
      @JsonProperty(required = true)
      public int percentage;
      @JsonProperty(required = true)
      public boolean done;
      public Integer totalDatasets;
      public Integer errors;
      @NotNull
      public Collection<DataSet.Info> datasetsWithoutValue;
   }


   class DatapointLastTimestamp {
      @JsonProperty(required = true)
      public int variable;
      @NotNull
      public Number timestamp;
   }

   class LastDatapointsParams {
      @NotNull
      public int[] variables;
      @NotNull
      public String fingerprint;
   }

   class ChangeDetectionUpdate {
      public List<String> timelineLabels;
      public String timelineFunction;
      public List<String> fingerprintLabels;
      public String fingerprintFilter;
   }
}
