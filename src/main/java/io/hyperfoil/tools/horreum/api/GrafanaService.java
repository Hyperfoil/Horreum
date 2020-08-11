package io.hyperfoil.tools.horreum.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.security.PermitAll;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;
import io.hyperfoil.tools.horreum.entity.alerting.Variable;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.grafana.Dashboard;
import io.hyperfoil.tools.horreum.grafana.GrafanaClient;
import io.hyperfoil.tools.horreum.grafana.Target;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * This service works as a backend for calls from Grafana (using
 * <a href="https://grafana.com/grafana/plugins/simpod-json-datasource">simpod-json-datasource</a>)
 * since Horreum exposes charts as embedded Grafana panels.
 */
@ApplicationScoped
@Path("/api/grafana")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GrafanaService {
   private static final Logger log = Logger.getLogger(GrafanaService.class);

   @Inject
   SqlService sqlService;

   @Inject
   SecurityIdentity identity;

   @Inject
   EntityManager em;

   @Inject @RestClient
   GrafanaClient grafana;

   @PermitAll
   @GET
   @Path("/")
   public Response healthcheck() {
      return Response.ok().build();
   }

   @PermitAll
   @POST
   @Path("/search")
   public Object[] search(Target query) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         return Variable.<Variable>listAll().stream().map(v -> String.valueOf(v.id)).toArray();
      }
   }

   @PermitAll
   @POST
   @Path("/query")
   public Response query(@Context HttpServletRequest request, Query query) {
      List<TimeseriesTarget> result = new ArrayList<>();
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         for (Target target : query.targets) {
            if (target.type != null && !target.type.equals("timeseries")) {
               return Response.status(Response.Status.BAD_REQUEST).entity("Tables are not implemented").build();
            }
            int variableId = targetToVariableId(target.target);
            if (variableId < 0) {
               return Response.status(Response.Status.BAD_REQUEST).entity("Target must be variable ID").build();
            }
            Variable variable = Variable.findById(variableId);
            String testName = "unknown", variableName = String.valueOf(variableId);
            if (variable != null) {
               variableName = variable.name;
               // TODO: breaking service separation
               Test test = Test.findById(variable.testId);
               if (test != null) {
                  testName = test.name;
               }
            }
            TimeseriesTarget tt = new TimeseriesTarget();
            tt.target = testName + "/" + variableName;
            result.add(tt);
            DataPoint.<DataPoint>find("variable_id = ?1 AND timestamp >= ?2 AND timestamp <= ?3", variableId, query.range.from, query.range.to)
                  .stream().forEach(dp -> tt.datapoints.add(new Number[] { dp.value, dp.timestamp.toEpochMilli() }));
         }
      }
      return Response.ok(result).build();
   }

   private int targetToVariableId(String target) {
      int variableId;
      try {
         variableId = Integer.parseInt(target);
      } catch (NumberFormatException e) {
         // TODO: support test name/variable name?
         variableId = -1;
      }
      return variableId;
   }

   @PermitAll
   @OPTIONS
   @Path("/annotations")
   public Response annotations() {
      return Response.ok()
            .header("Access-Control-Allow-Headers", "accept, content-type")
            .header("Access-Control-Allow-Methods", "POST")
            .header("Access-Control-Allow-Origin", "*")
            .build();
   }

   @PermitAll
   @POST
   @Path("/annotations")
   public Response annotations(AnnotationsQuery query) {
      // Note that annotations are per-dashboard, not per-panel:
      // https://github.com/grafana/grafana/issues/717
      List<AnnotationDefinition> annotations = new ArrayList<>();
      int variableId = targetToVariableId(query.annotation.query);
      if (variableId < 0) {
         return Response.status(Response.Status.BAD_REQUEST).entity("Query must be variable ID").build();
      }
      // TODO: use identity forwarded
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         Change.<Change>find("dataPoint.variable.id = ?1 AND dataPoint.timestamp >= ?2 AND dataPoint.timestamp <= ?3", variableId, query.range.from, query.range.to)
               .stream().forEach(change -> annotations.add(createAnnotation(change)));
      }
      return Response.ok(annotations).build();
   }

   private AnnotationDefinition createAnnotation(Change change) {
      String content = change.description + "<br>Confirmed: " + change.confirmed;
      return new AnnotationDefinition("Change in run " + change.dataPoint.runId, content, false, change.dataPoint.timestamp.toEpochMilli(), 0, new String[0]);
   }

   @PermitAll
   @GET
   @Path("dashboards")
   public Response createDashboard(@QueryParam("testid") Integer testId) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         Test test = Test.findById(testId);
         Dashboard dashboard = new Dashboard();
         dashboard.title = test.name;
         int i = 0;
         for (Variable v : Variable.<Variable>list("testId", testId)) {
            dashboard.annotations.list.add(new Dashboard.Annotation(v.name, String.valueOf(v.id)));
            Dashboard.Panel panel = new Dashboard.Panel(v.name, new Dashboard.GridPos(12 * (i % 2), 9 * (i / 2), 12, 9));
            panel.targets.add(new Target(String.valueOf(v.id), "timeseries", "T" + i));
            dashboard.panels.add(panel);
            ++i;
         }
         try {
            GrafanaClient.DashboardSummary response = grafana.createOrUpdateDashboard(new GrafanaClient.PostDashboardRequest(dashboard, false));
            if (response == null) {
               return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
            } else {
               return Response.ok(response.uid + ": " + response.url).build();
            }
         } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 412) {
               return Response.status(Response.Status.BAD_REQUEST).entity("Dashboard already exists.").build();
            }
            log.error("Failed to create the dashboard", e);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
         }
      }
   }

   public static class Query {
      public Range range;
      public List<Target> targets;
   }

   public static class Range {
      public Instant from;
      public Instant to;
   }

   public static class TimeseriesTarget {
      public String target;
      public List<Number[]> datapoints = new ArrayList<>();
   }

   public static class AnnotationsQuery {
      public Range range;
      public AnnotationQuery annotation;
   }

   public static class AnnotationQuery {
      public String name;
      public String datasource;
      public String iconColor;
      public boolean enable;
      public String query;
   }

   public static class AnnotationDefinition {
      public String title;
      public String text;
      public boolean isRegion;
      public long time;
      public long timeEnd;
      public String[] tags;

      public AnnotationDefinition(String title, String text, boolean isRegion, long time, long timeEnd, String[] tags) {
         this.title = title;
         this.text = text;
         this.isRegion = isRegion;
         this.time = time;
         this.timeEnd = timeEnd;
         this.tags = tags;
      }
   }

}
