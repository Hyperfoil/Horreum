package io.hyperfoil.tools.horreum.api.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.tools.horreum.api.changes.Target;

@Path("/api/changes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ChangesService {

   @POST
   @Path("/search")
   String[] search(@RequestBody(required = true) Target query);

   @POST
   @Path("/query")
   List<TimeseriesTarget> query(@RequestBody(required = true) Query query);

   @Operation(hidden = true)
   @OPTIONS
   @Path("/annotations")
   Response annotations(@HeaderParam("Origin") String origin);

   @POST
   @Path("/annotations")
   List<AnnotationDefinition> annotations(@RequestBody(required = true) AnnotationsQuery query);

   class Query {
      @NotNull
      public Range range;
      @NotNull
      public List<Target> targets;
   }

   class Range {
      @NotNull
      public Instant from;
      @NotNull
      public Instant to;
      // custom field used by UI to display lines coming into view and out of that
      public boolean oneBeforeAndAfter;
   }

   class TimeseriesTarget {
      @NotNull
      public String target;
      @NotNull
      public List<Number[]> datapoints = new ArrayList<>();
      @JsonProperty(required = true)
      public int variableId;
   }

   class AnnotationsQuery {
      public Range range;
      public AnnotationQuery annotation;
   }

   class AnnotationQuery {
      public String name;
      public String datasource;
      public String iconColor;
      public boolean enable;
      public String query;
   }

   class AnnotationDefinition {
      public String title;
      public String text;
      public boolean isRegion;
      public long time;
      public long timeEnd;
      public String[] tags;
      public int changeId;
      public int variableId;
      public int runId;
      public int datasetOrdinal;

      public AnnotationDefinition(String title, String text, boolean isRegion, long time, long timeEnd, String[] tags, int changeId, int variableId, int runId, int datasetOrdinal) {
         this.title = title;
         this.text = text;
         this.isRegion = isRegion;
         this.time = time;
         this.timeEnd = timeEnd;
         this.tags = tags;
         this.changeId = changeId;
         this.variableId = variableId;
         this.runId = runId;
         this.datasetOrdinal = datasetOrdinal;
      }
   }
}
