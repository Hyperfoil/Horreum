package io.hyperfoil.tools.horreum.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.sql.DataSource;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.stat.inference.TTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;
import io.hyperfoil.tools.horreum.entity.alerting.GrafanaDashboard;
import io.hyperfoil.tools.horreum.entity.alerting.Variable;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.SchemaExtractor;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.grafana.Dashboard;
import io.hyperfoil.tools.horreum.grafana.GrafanaClient;
import io.hyperfoil.tools.horreum.grafana.Target;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

@ApplicationScoped
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
@Path("/api/alerting")
public class AlertingService {
   private static final Logger log = Logger.getLogger(AlertingService.class);

   //@formatter:off
   private static final String LOOKUP_VARS =
         "WITH vars AS (" +
         "   SELECT id, calculation, unnest(regexp_split_to_array(accessors, ';')) as accessor FROM variable" +
         "   WHERE testid = ?" +
         ") SELECT vars.id as vid, vars.calculation, vars.accessor, se.jsonpath, schema.uri  FROM vars " +
         "JOIN schemaextractor se ON se.accessor = replace(vars.accessor, '[]', '') " +
         "JOIN schema ON schema.id = se.schema_id WHERE schema.uri = ANY(?);";
   //@formatter:on
   private static final String UPLOAD_RUN = "CREATE TEMPORARY TABLE current_run AS SELECT * FROM (VALUES (?::jsonb)) as t(data);";
   static final String HORREUM_ALERTING = "horreum.alerting";
   private static final Sort SORT_BY_TIMESTAMP_DESCENDING = Sort.by("timestamp", Sort.Direction.Descending);

   @Inject
   SqlService sqlService;

   @Inject
   EntityManager em;

   @Inject
   DataSource dataSource;

   @Inject
   EventBus eventBus;

   @Inject
   SecurityIdentity identity;

   @Inject @RestClient
   GrafanaClient grafana;

   @ConfigProperty(name = "horreum.grafana/mp-rest/url")
   String grafanaBaseUrl;

   @Inject
   TransactionManager tm;

   @Inject
   Vertx vertx;

   private Map<Integer, Integer> recalcProgress = new HashMap<>();

   @Transactional
   @ConsumeEvent(value = Run.EVENT_NEW, blocking = true)
   public void onNewRun(Run run) {
      log.infof("Received run ID %d", run.id);
      String firstLevelSchema = run.data.getString("$schema");
      List<String> schemas = run.data.values().stream()
            .filter(Json.class::isInstance).map(Json.class::cast)
            .map(json -> json.getString("$schema")).filter(Objects::nonNull)
            .collect(Collectors.toList());
      if (firstLevelSchema != null) {
         schemas.add(firstLevelSchema);
      }

      // In order to create datapoints we'll use the horreum.alerting ownership
      List<String> roles = Arrays.asList(run.owner, HORREUM_ALERTING);

      // TODO: We will have the JSONPaths in PostgreSQL format while the Run
      // itself is available here in the application.
      // We'll use the database as a library function
      StringBuilder extractionQuery = new StringBuilder("SELECT 1");
      Map<Integer, VarInfo> vars = new HashMap<>();
      try (Connection connection = dataSource.getConnection();
           @SuppressWarnings("unused") CloseMeJdbc closeMe = sqlService.withRoles(connection, roles)) {

         try (PreparedStatement setRun = connection.prepareStatement(UPLOAD_RUN)) {
            setRun.setString(1, run.data.toString());
            setRun.execute();
         } finally {

            try (PreparedStatement lookup = connection.prepareStatement(LOOKUP_VARS)) {
               lookup.setInt(1, run.testid);
               lookup.setArray(2, connection.createArrayOf("text", schemas.toArray()));
               ResultSet resultSet = lookup.executeQuery();
               Set<String> usedAccessors = new HashSet<>();

               while (resultSet.next()) {
                  int id = resultSet.getInt(1);
                  String calc = resultSet.getString(2);
                  String accessor = resultSet.getString(3);
                  String jsonpath = resultSet.getString(4);
                  String schema = resultSet.getString(5);
                  if (SchemaExtractor.isArray(accessor)) {
                     extractionQuery.append(", jsonb_path_query_array(data, '");
                     accessor = SchemaExtractor.arrayName(accessor);
                  } else {
                     extractionQuery.append(", jsonb_path_query_first(data, '");
                  }
                  while (!usedAccessors.add(accessor)) {
                     log.warnf("Accessor %s used for multiple schemas", accessor);
                     accessor = accessor + "_";
                  }
                  if (schema.equals(firstLevelSchema)) {
                     extractionQuery.append("$");
                  } else {
                     extractionQuery.append("$.*");
                  }
                  extractionQuery.append(jsonpath).append("'::jsonpath)#>>'{}' as ").append(accessor);
                  VarInfo var = vars.computeIfAbsent(id, i -> new VarInfo(i, calc));
                  var.accessors.add(accessor);
               }
               resultSet.close();
            }
            extractionQuery.append(" FROM current_run");

            List<DataPoint> newDataPoints = new ArrayList<>();
            try (PreparedStatement extraction = connection.prepareStatement(extractionQuery.toString())) {
               ResultSet resultSet = extraction.executeQuery();
               if (!resultSet.next()) {
                  log.errorf("Run %d does not exist in the database!", run.id);
                  //noinspection ReturnInsideFinallyBlock
                  return;
               }
               for (VarInfo var : vars.values()) {
                  if (var.accessors.isEmpty()) {
                     continue;
                  }
                  DataPoint dataPoint = new DataPoint();
                  // TODO: faking the variable
                  Variable variable = new Variable();
                  variable.id = var.id;
                  dataPoint.variable = variable;
                  dataPoint.runId = run.id;
                  dataPoint.timestamp = run.start;
                  if (var.calculation == null || var.calculation.isEmpty()) {
                     if (var.accessors.size() > 1) {
                        log.errorf("Variable %d has more than one accessor (%s) but no calculation function.", var.id, var.accessors);
                     }
                     String accessor = var.accessors.get(0);
                     String value = resultSet.getString(accessor);
                     if (value == null) {
                        log.infof("Null value for %s in run %s, accessor %s - datapoint is not created", variable.name, run.id, accessor);
                        continue;
                     }
                     try {
                        dataPoint.value = Double.parseDouble(value);
                     } catch (NumberFormatException e) {
                        log.errorf(e, "Cannot turn %s into a floating-point value", value);
                        continue;
                     }
                  } else {
                     StringBuilder code = new StringBuilder();
                     if (var.accessors.size() > 1) {
                        code.append("const __obj = {\n");
                        for (String accessor : var.accessors) {
                           code.append(accessor).append(": ").append(resultSet.getString(accessor)).append(",\n");
                        }
                        code.append("};\n");
                     } else {
                        code.append("const __obj = ").append(resultSet.getString(var.accessors.get(0))).append(";\n");
                     }
                     code.append("const __func = ").append(var.calculation).append(";\n");
                     code.append("__func(__obj)");
                     Double value = execute(code.toString());
                     if (value == null) {
                        continue;
                     }
                     dataPoint.value = value;
                  }
                  newDataPoints.add(dataPoint);
               }
            } finally {
               try (PreparedStatement dropRun = connection.prepareStatement("DROP TABLE current_run")) {
                  dropRun.execute();
               }
            }

            // TODO: maybe do this through entity manager?
            for (DataPoint dataPoint : newDataPoints) {
               try (PreparedStatement insert = connection.prepareStatement("INSERT INTO datapoint (variable_id, runid, timestamp, value) VALUES (?, ?, ?, ?);")) {
                  insert.setInt(1, dataPoint.variable.id);
                  insert.setInt(2, dataPoint.runId);
                  insert.setTimestamp(3, Timestamp.from(dataPoint.timestamp));
                  insert.setDouble(4, dataPoint.value);
                  insert.execute();
               }
               eventBus.publish(DataPoint.EVENT_NEW, dataPoint);
            }
         }
      } catch (SQLException e) {
         log.error("SQL commands failed", e);
      }
   }

   private List<String> columns(ResultSet resultSet) throws SQLException {
      List<String> columns = new ArrayList<>();
      ResultSetMetaData metaData = resultSet.getMetaData();
      for (int i = 1; i <= metaData.getColumnCount(); ++i) {
         columns.add(metaData.getColumnName(i));
      }
      return columns;
   }

   private Double execute(String jsCode) {
      try (Context context = Context.newBuilder(new String[]{ "js"}).build()) {
         context.enter();
         try {
            Value value = context.eval("js", jsCode);
            if (value.isNumber()) {
               return value.asDouble();
            } else if (value.isString()) {
               try {
                  return Double.parseDouble(value.asString());
               } catch (NumberFormatException e) {
                  log.warnf("Evaluation failed: Return value %s cannot be parsed into a number.", value);
                  return null;
               }
            } else {
               log.warnf("Evaluation failed: Return value %s is not a number.", value);
               return null;
            }
         } catch (PolyglotException e) {
            log.warnf("Evaluation failed: '%s' Code:\n%s", e.getMessage(), jsCode);
            return null;
         } finally {
            context.leave();
         }
      }
   }

   @Transactional
   @ConsumeEvent(value = Run.EVENT_TRASHED, blocking = true)
   public void onRunTrashed(Integer runId) {
      log.infof("Trashing datapoints for run %d", runId);
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, Collections.singletonList(HORREUM_ALERTING))) {
         Query deleteChanges = em.createNativeQuery("DELETE FROM change WHERE datapoint_id IN (SELECT id FROM datapoint WHERE runid = ?)");
         deleteChanges.setParameter(1, runId).executeUpdate();
         DataPoint.delete("runid", runId);
      }
   }

   @Transactional
   @ConsumeEvent(value = DataPoint.EVENT_NEW, blocking = true)
   public void onNewDataPoint(DataPoint dataPoint) {
      log.infof("Processing new datapoint for run %d, variable %d", dataPoint.runId, dataPoint.variable.id);
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, Collections.singletonList(HORREUM_ALERTING))) {
         // The variable referenced by datapoint is a fake
         Variable variable = Variable.findById(dataPoint.variable.id);
         Change lastChange = Change.find("variable = ?1", SORT_BY_TIMESTAMP_DESCENDING, variable).range(0, 0).firstResult();
         List<DataPoint> dataPoints;
         if (lastChange == null) {
            dataPoints = DataPoint.find("variable", SORT_BY_TIMESTAMP_DESCENDING, variable).range(0, variable.maxWindow).list();
         } else {
            dataPoints = DataPoint.find("variable = ?1 AND runId >= ?2", SORT_BY_TIMESTAMP_DESCENDING, variable, lastChange.runId)
                  .range(0, variable.maxWindow).list();
         }
         // Last datapoint is already in the list
         assert !dataPoints.isEmpty();
         DataPoint firstDatapoint = dataPoints.get(0);
         assert Math.abs(firstDatapoint.value - dataPoint.value) < 0.000001;
         // From 1 result we cannot estimate stddev either, so it's not useful
         if (dataPoints.size() <= 2) {
            log.infof("Criterion %d has too few data (%d datapoints), skipping analysis", variable.id, dataPoints.size());
            return;
         }
         SummaryStatistics statistics = new SummaryStatistics();
         dataPoints.stream().skip(1).mapToDouble(dp -> dp.value).forEach(statistics::addValue);
         double diff = Math.abs(statistics.getMean() - dataPoint.value);
         if (diff > statistics.getStandardDeviation() * variable.deviationFactor) {
            log.infof("Value %f exceeds %f +- %f x %f", dataPoint.value, statistics.getMean(), statistics.getStandardDeviation(), variable.deviationFactor);
            Change change = new Change();
            change.variable = firstDatapoint.variable;
            change.timestamp = firstDatapoint.timestamp;
            change.runId = firstDatapoint.runId;
            change.description = "Last datapoint is out of deviation range";
            em.persist(change);
            eventBus.publish(Change.EVENT_NEW, change);
         } else {
            double lowestPValue = 1.0;
            int changeIndex = -1;
            // we want at least 2 values in each population
            for (int i = 2; i <= dataPoints.size() - 2; ++i) {
               double[] populationA = dataPoints.stream().limit(i).mapToDouble(dp -> dp.value).toArray();
               double[] populationB = dataPoints.stream().skip(i).mapToDouble(dp -> dp.value).toArray();
               final double pValue = new TTest().tTest(populationA, populationB);
               if (pValue < lowestPValue && pValue < 1 - variable.confidence) {
                  changeIndex = i;
                  lowestPValue = pValue;
               }
            }
            if (changeIndex >= 0) {
               Change change = new Change();
               DataPoint dp = dataPoints.get(changeIndex - 1);
               change.variable = dp.variable;
               change.timestamp = dp.timestamp;
               change.runId = dp.runId;
               change.description = String.format("Change detected with confidence %.3f%%", (1 - lowestPValue) * 100);
               log.infof("T-test found likelihood of %f%% that there's a change at run ID %d", lowestPValue * 100, change.runId);
               em.persist(change);
               eventBus.publish(Change.EVENT_NEW, change);
            }
         }
      }
   }

   @PermitAll
   @GET
   @Path("variables")
   public Response variables(@QueryParam("test") Integer testId) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         if (testId != null) {
            return Response.ok(Variable.list("testid", testId)).build();
         } else {
            return Response.ok(Variable.listAll()).build();
         }
      }
   }

   @RolesAllowed("tester")
   @POST
   @Path("variables")
   @Transactional
   public Response variables(@QueryParam("test") Integer testId, List<Variable> variables) throws SystemException {
      if (testId == null) {
         return Response.status(Response.Status.BAD_REQUEST).entity("Missing query param 'test'").build();
      }
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         List<Variable> currentVariables = Variable.list("testid", testId);
         for (Variable current : currentVariables) {
            Variable matching = variables.stream().filter(v -> current.id.equals(v.id)).findFirst().orElse(null);
            if (matching == null) {
               DataPoint.delete("variable_id", current.id);
               current.delete();
            } else {
               current.name = matching.name;
               current.group = matching.group;
               current.order = matching.order;
               current.accessors = matching.accessors;
               current.calculation = matching.calculation;
               current.maxWindow = matching.maxWindow <= 0 ? Integer.MAX_VALUE : matching.maxWindow;
               current.deviationFactor = matching.deviationFactor;
               current.confidence = matching.confidence;
               current.persist();
            }
         }
         for (Variable variable : variables) {
            if (currentVariables.stream().noneMatch(v -> v.id.equals(variable.id))) {
               if (variable.id <= 0) {
                  variable.id = null;
               }
               variable.testId = testId;
               variable.maxWindow = variable.maxWindow <= 0 ? Integer.MAX_VALUE : variable.maxWindow;
               variable.persist(); // insert
            }
         }
         GrafanaDashboard dashboard = GrafanaDashboard.find("testId", testId).firstResult();
         if (dashboard == null) {
            dashboard = new GrafanaDashboard();
            dashboard.testId = testId;
            dashboard.variables = new ArrayList<>();

            Test test = Test.findById(testId);
            if (test != null) {
               try {
                  dashboard.uid = grafana.searchDashboard(test.name).stream().findFirst().map(ds -> ds.uid).orElse(null);
               } catch (WebApplicationException e) {
                  if (e.getResponse().getStatus() == 404) {
                     log.infof("Dashboard for test %s does not exist");
                  } else {
                     log.errorf(e, "Cannot lookup Grafana dashboard for test %s", test.name);
                     tm.setRollbackOnly();
                     return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
                  }
               }
            }
         } else {
            dashboard.variables.clear();
         }

         if (!createDashboard(testId, variables, dashboard)) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("Cannot update Grafana dashboard.").build();
         }
         // TODO: We should probably recalculate all datapoints when accessors/calculation changes
         // TODO: Unconfirmed changes should be deleted (and recomputed), too, but confirmed ones should persist
         em.flush();
         return Response.ok().build();
      }
   }

   private boolean createDashboard(int testId, List<Variable> variables, GrafanaDashboard dashboard) throws SystemException {
      Dashboard clientDashboard = null;
      if (dashboard.uid != null) {
         try {
            GrafanaClient.GetDashboardResponse response = grafana.getDashboard(dashboard.uid);
            clientDashboard = response.dashboard;
         } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
               log.infof("Dashboard %s cannot be found, creating another", dashboard.uid);
               dashboard.uid = null;
               dashboard.url = null;
            } else {
               log.errorf(e, "Failed to get existing dashboard with UID %s", dashboard.uid);
            }
         }
      }
      if (clientDashboard == null) {
         clientDashboard = new Dashboard();
         clientDashboard.title = Test.<Test>findByIdOptional(testId).map(t -> t.name).orElse("Test " + testId);
      } else {
         clientDashboard.panels.clear();
         clientDashboard.annotations.list.clear();
      }
      int i = 0;
      Map<String, List<Variable>> byGroup = new HashMap<>();
      for (Variable variable : variables) {
         clientDashboard.annotations.list.add(new Dashboard.Annotation(variable.name, String.valueOf(variable.id)));
         dashboard.variables.add(variable);
         byGroup.computeIfAbsent(variable.group == null || variable.group.isEmpty() ? "" : variable.group, g -> new ArrayList<>()).add(variable);
      }
      for (Map.Entry<String, List<Variable>> entry : byGroup.entrySet()) {
         Dashboard.Panel panel = new Dashboard.Panel(entry.getKey(), new Dashboard.GridPos(12 * (i % 2), 9 * (i / 2), 12, 9));
         for (Variable variable : entry.getValue()) {
            panel.targets.add(new Target(String.valueOf(variable.id), "timeseries", "T" + i));
         }
         clientDashboard.panels.add(panel);
         ++i;

      }
      try {
         GrafanaClient.DashboardSummary response = grafana.createOrUpdateDashboard(new GrafanaClient.PostDashboardRequest(clientDashboard, true));
         if (response != null) {
            dashboard.uid = response.uid;
            dashboard.url = grafanaBaseUrl + response.url;
         }
      } catch (WebApplicationException e) {
         log.errorf(e, "Failed to create/update dashboard %s", clientDashboard.uid);
         tm.setRollbackOnly();
         return false;
      }
      dashboard.persist();
      return true;
   }

   @PermitAll
   @GET
   @Path("dashboard")
   @Transactional
   public Response dashboard(@QueryParam("test") Integer testId) throws SystemException {
      if (testId == null) {
         return Response.status(Response.Status.BAD_REQUEST).entity("Missing param 'test'").build();
      }
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         GrafanaDashboard dashboard = GrafanaDashboard.find("testId", testId).firstResult();
         if (dashboard == null) {
            dashboard = new GrafanaDashboard();
            dashboard.testId = testId;
            dashboard.variables = new ArrayList<>();
            if (!createDashboard(testId, Variable.list("testid", testId), dashboard)) {
               return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("Cannot update Grafana dashboard.").build();
            }
         }
         return Response.ok(dashboard).build();
      }
   }

   @PermitAll
   @GET
   @Path("changes")
   public Response changes(@QueryParam("var") Integer varId) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, Collections.singletonList(HORREUM_ALERTING))) {
         Variable v = Variable.findById(varId);
         if (v == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Variable " + varId + " not found").build();
         }
         // TODO: Avoid sending variable in each datapoint
         return Response.ok(Change.list("variable", v)).build();
      }
   }

   @RolesAllowed(Roles.TESTER)
   @POST
   @Path("change/{id}")
   @Transactional
   public Response updateChange(@PathParam("id") Integer id, Change change) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, Collections.singletonList(HORREUM_ALERTING))) {
         if (id != change.id) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Path ID and entity don't match").build();
         }
         em.merge(change);
         return Response.ok().build();
      }
   }

   @RolesAllowed(Roles.TESTER)
   @DELETE
   @Path("change/{id}")
   @Transactional
   public Response deleteChange(@PathParam("id") Integer id) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, Collections.singletonList(HORREUM_ALERTING))) {
         if (Change.deleteById(id)) {
            return Response.ok().build();
         } else {
            return Response.status(Response.Status.NOT_FOUND).build();
         }
      }
   }

   private <T> T withTx(Supplier<T> supplier) {
      try {
         tm.begin();
         try {
            return supplier.get();
         } catch (Exception e) {
            tm.setRollbackOnly();
            throw e;
         } finally {
            if (tm.getStatus() == Status.STATUS_ACTIVE) {
               tm.commit();
            } else {
               tm.rollback();
            }
         }
      } catch (Exception ex) {
         log.error("Failed to run transaction", ex);
      }
      return null;
   }

   @RolesAllowed(Roles.TESTER)
   @POST
   @Path("recalculate")
   public Response recalculate(@QueryParam("test") Integer testId) {
      if (testId == null) {
         return Response.status(Response.Status.BAD_REQUEST).entity("Missing param 'test'").build();
      }
      SecurityIdentity identity = CachedSecurityIdentity.of(this.identity);

      vertx.executeBlocking(promise -> {
         List<Integer> runIds = withTx(() -> {
            if (recalcProgress.putIfAbsent(testId, 0) != null) {
               promise.complete();
               return null;
            }
            try (CloseMe closeMe = sqlService.withRoles(em, identity)) {
               @SuppressWarnings("unchecked")
               List<Integer> ids = em.createNativeQuery("SELECT id FROM run WHERE testid = ?1").setParameter(1, testId).getResultList();
               DataPoint.delete("runId in ?1", ids);
               Change.delete("runId in ?1 AND confirmed = false", ids);
               return ids;
            }
         });
         if (runIds != null) {
            int completed = 0;
            for (int runId : runIds) {
               // Since the evaluation might take few moments and we're dealing potentially with thousands
               // of runs we'll process each run in a separate transaction
               withTx(() -> {
                  try (CloseMe closeMe = sqlService.withRoles(em, identity)) {
                     Run run = Run.findById(runId);
                     onNewRun(run);
                  }
                  return null;
               });
               recalcProgress.put(testId, 100 * ++completed / runIds.size());
            }
         }
         recalcProgress.remove(testId);
         promise.complete();
      }, result -> {});
      return Response.ok().build();
   }

   @RolesAllowed(Roles.TESTER)
   @GET
   @Path("recalculate")
   public Response recalculateProgress(@QueryParam("test") Integer testId) {
      if (testId == null) {
         return Response.status(Response.Status.BAD_REQUEST).entity("Missing param 'test'").build();
      }
      Integer progress = recalcProgress.get(testId);
      Json json = new Json(false);
      json.add("percentage", progress == null ? 100 : progress);
      json.add("done", progress == null);
      return Response.ok(json).build();
   }

   @RolesAllowed(Roles.ADMIN)
   @POST
   @Path("testNewChange")
   public void testNewChange() {
      Change c = new Change();
      c.timestamp = Instant.now();
      c.runId = 1;
      c.variable = Variable.findAll().firstResult();
      c.description = "Foobar";
      eventBus.publish(Change.EVENT_NEW, c);
   }

   private static class VarInfo {
      final int id;
      final String calculation;
      final List<String> accessors = new ArrayList<>();

      private VarInfo(int id, String calculation) {
         this.id = id;
         this.calculation = calculation;
      }
   }
}
