package io.hyperfoil.tools.horreum.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.hyperfoil.tools.horreum.grafana.Target;

@Path("/api/grafana")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface GrafanaService {

   @GET
   @Path("/")
   default Response healthcheck() {
      return Response.ok().build();
   }

   @POST
   @Path("/search")
   Object[] search(Target query);

   @POST
   @Path("/query")
   List<TimeseriesTarget> query(@Context HttpServletRequest request, Query query);

   @OPTIONS
   @Path("/annotations")
   default Response annotations() {
      return Response.ok()
            .header("Access-Control-Allow-Headers", "accept, content-type")
            .header("Access-Control-Allow-Methods", "POST")
            .header("Access-Control-Allow-Origin", "*")
            .build();
   }

   @POST
   @Path("/annotations")
   List<AnnotationDefinition> annotations(AnnotationsQuery query);

   class Query {
      public Range range;
      public List<Target> targets;
   }

   class Range {
      public Instant from;
      public Instant to;
   }

   class TimeseriesTarget {
      public String target;
      public List<Number[]> datapoints = new ArrayList<>();
      // custom fields Grafana does not understand
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
      // custom fields Grafana does not understand
      public int changeId;
      public int variableId;
      public int runId;

      public AnnotationDefinition(String title, String text, boolean isRegion, long time, long timeEnd, String[] tags, int changeId, int variableId, int runId) {
         this.title = title;
         this.text = text;
         this.isRegion = isRegion;
         this.time = time;
         this.timeEnd = timeEnd;
         this.tags = tags;
         this.changeId = changeId;
         this.variableId = variableId;
         this.runId = runId;
      }
   }
}
