package io.hyperfoil.tools.horreum.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
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

import io.hyperfoil.tools.yaup.StringUtil;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.hibernate.transform.AliasToEntityMapResultTransformer;
import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;
import io.hyperfoil.tools.horreum.entity.alerting.GrafanaDashboard;
import io.hyperfoil.tools.horreum.entity.alerting.GrafanaPanel;
import io.hyperfoil.tools.horreum.entity.alerting.Variable;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.SchemaExtractor;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.grafana.Dashboard;
import io.hyperfoil.tools.horreum.grafana.GrafanaClient;
import io.hyperfoil.tools.horreum.grafana.Target;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
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
         "   SELECT id, calculation, unnest(string_to_array(accessors, ';')) as accessor FROM variable" +
         "   WHERE testid = ?" +
         ") SELECT vars.id as vid, vars.calculation, vars.accessor, se.jsonpath, schema.uri  FROM vars " +
         "JOIN schemaextractor se ON se.accessor = replace(vars.accessor, '[]', '') " +
         "JOIN schema ON schema.id = se.schema_id WHERE schema.uri = ANY(string_to_array(?, ';'));";
   //@formatter:on
   // the :::: is used instead of :: as Hibernate converts four-dot into colon
   private static final String UPLOAD_RUN = "CREATE TEMPORARY TABLE current_run AS SELECT * FROM (VALUES (?::::jsonb)) as t(data);";
   static final String HORREUM_ALERTING = "horreum.alerting";
   private static final Sort SORT_BY_TIMESTAMP_DESCENDING = Sort.by("timestamp", Sort.Direction.Descending);

   @Inject
   SqlService sqlService;

   @Inject
   EntityManager em;

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
      onNewRun(run, true);
   }

   private void onNewRun(Run run, boolean notify) {
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
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, roles)) {

         Query setRun = em.createNativeQuery(UPLOAD_RUN);
         setRun.setParameter(1, run.data.toString());
         setRun.executeUpdate();
         try {
            Query lookup = em.createNativeQuery(LOOKUP_VARS);
            lookup.setParameter(1, run.testid);
            lookup.setParameter(2, String.join(";", schemas));
            @SuppressWarnings("unchecked")
            List<Object[]> varSelection = lookup.getResultList();
            Map<String, String> usedAccessors = new HashMap<>();

            ACCESSOR_LOOP:
            for (Object[] row : varSelection) {
               int id = (Integer) row[0];
               String calc = (String) row[1];
               String accessor = (String) row[2];
               String jsonpath = (String) row[3];
               String schema = (String) row[4];
               VarInfo var = vars.computeIfAbsent(id, i -> new VarInfo(i, calc));

               boolean isArray = SchemaExtractor.isArray(accessor);
               if (isArray) {
                  // we have to allow select both as first match and as an array, while keeping only one in the result
                  accessor = SchemaExtractor.arrayName(accessor) + "___arr";
               }

               for (; ; ) {
                  String prev = usedAccessors.putIfAbsent(accessor, schema);
                  if (prev == null) {
                     break;
                  } else if (prev.equals(schema)) {
                     var.accessors.add(accessor);
                     continue ACCESSOR_LOOP;
                  }
                  log.warnf("Accessor %s used for multiple schemas: %s, %s", accessor, schema, prev);
                  accessor = accessor + "_";
               }
               if (isArray) {
                  extractionQuery.append(", jsonb_path_query_array(data, '");
               } else {
                  extractionQuery.append(", jsonb_path_query_first(data, '");
               }
               if (schema.equals(firstLevelSchema)) {
                  extractionQuery.append("$");
               } else {
                  extractionQuery.append("$.*");
               }
               // four colons to escape it for Hibernate
               extractionQuery.append(jsonpath).append("'::::jsonpath)::::text as ").append(StringUtil.quote(accessor, "\""));
               var.accessors.add(accessor);
            }
            extractionQuery.append(" FROM current_run");

            Query extraction = em.createNativeQuery(extractionQuery.toString());
            //noinspection deprecation
            extraction.unwrap(org.hibernate.query.Query.class).setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);
            Map<String, String> extracted;
            try {
               extracted = (Map<String, String>) extraction.getSingleResult();
            } catch (NoResultException e) {
               log.errorf("Run %d does not exist in the database!", run.id);
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
                     log.errorf("Variable %s has more than one accessor (%s) but no calculation function.", variable.name, var.accessors);
                  }
                  String accessor = var.accessors.get(0);
                  String value = extracted.get(accessor.toLowerCase());
                  if (value == null) {
                     log.infof("Null value for %s in run %s, accessor %s - datapoint is not created", variable.name, run.id, accessor);
                     continue;
                  }
                  String maybeNumber = value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"' ?
                        value.substring(1, value.length() - 1) : value;
                  try {
                     dataPoint.value = Double.parseDouble(maybeNumber);
                  } catch (NumberFormatException e) {
                     log.errorf(e, "Cannot turn %s into a floating-point value for variable %s", value, variable.name);
                     continue;
                  }
               } else {
                  StringBuilder code = new StringBuilder();
                  if (var.accessors.size() > 1) {
                     code.append("const __obj = {\n");
                     for (String accessor : var.accessors) {
                        String value = extracted.get(accessor.toLowerCase());
                        if (accessor.endsWith("___arr")) {
                           code.append(accessor, 0, accessor.length() - 6);
                        } else {
                           code.append(accessor);
                        }
                        code.append(": ");
                        appendValue(code, value);
                        code.append(",\n");
                     }
                     code.append("};\n");
                  } else {
                     code.append("const __obj = ");
                     appendValue(code, extracted.get(var.accessors.get(0).toLowerCase()));
                     code.append(";\n");
                  }
                  code.append("const __func = ").append(var.calculation).append(";\n");
                  code.append("__func(__obj)");
                  Double value = execute(code.toString(), variable.name);
                  if (value == null) {
                     continue;
                  }
                  dataPoint.value = value;
               }
               dataPoint.persist();
               publishLater(DataPoint.EVENT_NEW, new DataPoint.Event(dataPoint, notify));
            }
         } catch (Throwable t) {
            log.error("Failed to create new datapoints", t);
         } finally {
            em.createNativeQuery("DROP TABLE current_run").executeUpdate();
         }
      }
   }

   private void appendValue(StringBuilder code, String value) {
      if (value == null) {
         code.append("null");
         return;
      }
      if (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
         String maybeNumber = value.substring(1, value.length() - 1);
         try {
            code.append(Integer.parseInt(maybeNumber));
            return;
         } catch (NumberFormatException e1) {
               try {
               code.append(Double.parseDouble(maybeNumber));
               return;
            } catch (NumberFormatException e2) {
                // ignore
            }
         }
      }
      code.append(value);
   }

   private Double execute(String jsCode, String name) {
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
            } else if (value.isNull()) {
               // returning null is intentional or the data does not exist, don't warn
               log.infof("Result for variable %s is null, skipping.", name);
               return null;
            } else if ("undefined".equals(value.toString())) {
               // returning undefined is intentional, don't warn
               log.infof("Result for variable %s is undefined, skipping.", name);
               return null;
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
         Query deleteChanges = em.createNativeQuery("DELETE FROM change WHERE runid ?");
         deleteChanges.setParameter(1, runId).executeUpdate();
         DataPoint.delete("runid", runId);
      }
   }

   @Transactional
   @ConsumeEvent(value = DataPoint.EVENT_NEW, blocking = true)
   public void onNewDataPoint(DataPoint.Event event) {
      DataPoint dataPoint = event.dataPoint;
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, Collections.singletonList(HORREUM_ALERTING))) {
         // The variable referenced by datapoint is a fake
         Variable variable = Variable.findById(dataPoint.variable.id);
         log.infof("Processing new datapoint for run %d, variable %d (%s), value %f", dataPoint.runId,
               dataPoint.variable.id, variable != null ? variable.name : "<unknown>", dataPoint.value);
         Change lastChange = Change.find("variable = ?1", SORT_BY_TIMESTAMP_DESCENDING, variable).range(0, 0).firstResult();
         PanacheQuery<DataPoint> query;
         if (lastChange == null) {
            query = DataPoint.find("variable", SORT_BY_TIMESTAMP_DESCENDING, variable);
         } else {
            if (lastChange.timestamp.compareTo(dataPoint.timestamp) > 0) {
               // We won't revision changes until next variable recalculation
               log.infof("Ignoring datapoint %d from %s as there is a newer change %d from %s.",
                     dataPoint.id, dataPoint.timestamp, lastChange.id, lastChange.timestamp);
               return;
            }
            log.infof("Filtering datapoints newer than %s (change %d)", lastChange.timestamp, lastChange.id);
            query = DataPoint.find("variable = ?1 AND timestamp >= ?2", SORT_BY_TIMESTAMP_DESCENDING, variable, lastChange.timestamp);
         }
         List<DataPoint> dataPoints = query.list();

         // Last datapoint is already in the list
         if (dataPoints.isEmpty()) {
            log.error("The published datapoint should be already in the list");
            return;
         }
         DataPoint firstDatapoint = dataPoints.get(0);
         if (!firstDatapoint.id.equals(dataPoint.id)) {
            log.infof("Ignoring datapoint %d from %s as there's a newer datapoint %d from %s",
                  dataPoint.id, dataPoint.timestamp, firstDatapoint.id, firstDatapoint.timestamp);
            return;
         }
         if (dataPoints.size() <= Math.max(1, variable.minWindow)) {
            log.infof("Too few (%d) previous datapoints for variable %d, skipping analysis", dataPoints.size() - 1, variable.id);
            return;
         }
         SummaryStatistics statistics = new SummaryStatistics();
         dataPoints.stream().skip(1).mapToDouble(DataPoint::value).forEach(statistics::addValue);
         double ratio = dataPoint.value/statistics.getMean();
         if (ratio < 1 - variable.maxDifferenceLastDatapoint || ratio > 1 + variable.maxDifferenceLastDatapoint) {
            log.infof("Value %f exceeds %f +- %f%% (based on %d datapoints stddev is %f)",
                  dataPoint.value, statistics.getMean(),
                  variable.maxDifferenceLastDatapoint, dataPoints.size() - 1, statistics.getStandardDeviation());
            Change change = new Change();
            change.variable = firstDatapoint.variable;
            change.timestamp = firstDatapoint.timestamp;
            change.runId = firstDatapoint.runId;
            change.description = "Last datapoint is out of range: value=" +
                  dataPoint.value + ", mean=" + statistics.getMean() + ", count=" + statistics.getN() + " stddev=" + statistics.getStandardDeviation() +
                  ", range=" + ((1 - variable.maxDifferenceLastDatapoint) * statistics.getMean()) +
                  ".." + ((1 + variable.maxDifferenceLastDatapoint) * statistics.getMean());
            em.persist(change);
            publishLater(Change.EVENT_NEW, new Change.Event(change, event.notify));
         } else if (dataPoints.size() >= 2 * variable.floatingWindow){
            SummaryStatistics older = new SummaryStatistics(), window = new SummaryStatistics();
            dataPoints.stream().skip(variable.floatingWindow).mapToDouble(dp -> dp.value).forEach(older::addValue);
            dataPoints.stream().limit(variable.floatingWindow).mapToDouble(dp -> dp.value).forEach(window::addValue);

            double floatingRatio = window.getMean() / older.getMean();
            if (floatingRatio < 1 - variable.maxDifferenceFloatingWindow || floatingRatio > 1 + variable.maxDifferenceFloatingWindow) {
               DataPoint dp = null;
               // We cannot know which datapoint is first with the regression; as a heuristic approach
               // we'll select first datapoint with value lower than mean (if this is a drop, e.g. throughput)
               // or above the mean (if this is an increase, e.g. memory usage).
               for (int i = variable.floatingWindow - 1; i >= 0; --i) {
                  dp = dataPoints.get(i);
                  if (floatingRatio < 1 && dp.value < older.getMean()) {
                     break;
                  } else if (floatingRatio > 1 && dp.value > older.getMean()) {
                     break;
                  }
               }
               Change change = new Change();
               change.variable = dp.variable;
               change.timestamp = dp.timestamp;
               change.runId = dp.runId;
               change.description = String.format("Change detected in floating window, runs %d (%s) - %d (%s): mean %f (stddev %f), previous mean %f (stddev %f)",
                     dataPoints.get(variable.floatingWindow - 1).runId, dataPoints.get(variable.floatingWindow - 1).timestamp,
                     dataPoints.get(0).runId, dataPoints.get(0).timestamp,
                     window.getMean(), window.getStandardDeviation(), older.getMean(), older.getStandardDeviation());

               em.persist(change);
               log.info(change.description);
               publishLater(Change.EVENT_NEW, new Change.Event(change, event.notify));
            }
         }
      }
   }

   private double[] omitMinMax(DataPoint[] datapoints) {
      DataPoint min = null;
      DataPoint max = null;
      for (DataPoint datapoint : datapoints) {
         if (min == null || datapoint.value < min.value) {
            min = datapoint;
         }
         if (max == null || datapoint.value > max.value) {
            max = datapoint;
         }
      }
      DataPoint finalMin = min;
      DataPoint finalMax = max;
      log.infof("From runs %s ignoring %d (%f) and %d (%f)", Stream.of(datapoints).map(dp -> dp.runId).collect(Collectors.toList()), min.runId, min.value, max.runId, max.value);
      return Stream.of(datapoints).filter(dp -> dp != finalMin && dp != finalMax).mapToDouble(dp -> dp.value).toArray();
   }

   private void publishLater(String eventName, Object event) {
      try {
         tm.getTransaction().registerSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
               if (status == Status.STATUS_COMMITTED || status == Status.STATUS_COMMITTING) {
                  eventBus.publish(eventName, event);
               }
            }
         });
      } catch (RollbackException e) {
         log.debug("Not publishing the event as the transaction has been marked rollback-only");
      } catch (SystemException e) {
         log.errorf(e, "Failed to publish event %s: %s after transaction completion", eventName, event);
      }
   }

   @PermitAll
   @GET
   @Path("variables")
   public List<Variable> variables(@QueryParam("test") Integer testId) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         if (testId != null) {
            return Variable.list("testid", testId);
         } else {
            return Variable.listAll();
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
               current.maxDifferenceLastDatapoint = matching.maxDifferenceLastDatapoint;
               current.maxDifferenceFloatingWindow = matching.maxDifferenceFloatingWindow;
               current.floatingWindow = matching.floatingWindow;
               current.persist();
            }
         }
         for (Variable variable : variables) {
            if (currentVariables.stream().noneMatch(v -> v.id.equals(variable.id))) {
               if (variable.id <= 0) {
                  variable.id = null;
               }
               variable.testId = testId;
               variable.persist(); // insert
            }
         }

         List<GrafanaDashboard> dashboards = GrafanaDashboard.find("testId", testId).list();
         for (GrafanaDashboard dashboard : dashboards) {
            try {
               grafana.deleteDashboard(dashboard.uid);
            } catch (WebApplicationException e) {
               log.warnf(e, "Failed to delete dasboard %s", dashboard.uid);
            }
            dashboard.panels.forEach(GrafanaPanel::delete);
            dashboard.delete();
         }

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
      String tags = dashboard.tags == null ? "" : dashboard.tags;
      if (clientDashboard == null) {
         clientDashboard = new Dashboard();
         clientDashboard.title = Test.<Test>findByIdOptional(testId).map(t -> t.name).orElse("Test " + testId)
               + (dashboard.tags == null ? "" : ", " + dashboard.tags);
      } else {
         clientDashboard.panels.clear();
         clientDashboard.annotations.list.clear();
      }
      int i = 0;
      Map<String, List<Variable>> byGroup = new TreeMap<>();
      for (Variable variable : variables) {
         clientDashboard.annotations.list.add(new Dashboard.Annotation(variable.name, variable.id + ";" + tags));
         byGroup.computeIfAbsent(variable.group == null || variable.group.isEmpty() ? variable.name : variable.group, g -> new ArrayList<>()).add(variable);
      }
      for (Map.Entry<String, List<Variable>> entry : byGroup.entrySet()) {
         entry.getValue().sort(Comparator.comparing(v -> v.order));
         Dashboard.Panel panel = new Dashboard.Panel(entry.getKey(), new Dashboard.GridPos(12 * (i % 2), 9 * (i / 2), 12, 9));
         GrafanaPanel gpanel = new GrafanaPanel();
         gpanel.name = entry.getKey();
         gpanel.variables = new ArrayList<>();
         dashboard.panels.add(gpanel);
         for (Variable variable : entry.getValue()) {
            gpanel.variables.add(variable);
            panel.targets.add(new Target(variable.id + ";" + tags, "timeseries", "T" + i));
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
   @Path("tags")
   public Response tags(@QueryParam("test") Integer testId) {
      if (testId == null) {
         return Response.status(Response.Status.BAD_REQUEST).entity("Missing param 'test'").build();
      }
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         Query tagComboQuery = em.createNativeQuery("SELECT tags::::text FROM run LEFT JOIN run_tags ON run_tags.runid = run.id WHERE run.testid = ? GROUP BY tags");
         Json result = new Json(true);
         for (String tags : ((List<String>) tagComboQuery.setParameter(1, testId).getResultList())) {
            result.add(Json.fromString(tags));
         }
         return Response.ok(result).build();
      }
   }

   @PermitAll
   @GET
   @Path("dashboard")
   @Transactional
   public Response dashboard(@QueryParam("test") Integer testId, @QueryParam("tags") String tags) throws SystemException {
      if (testId == null) {
         return Response.status(Response.Status.BAD_REQUEST).entity("Missing param 'test'").build();
      }
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         GrafanaDashboard dashboard;
         if (tags == null || tags.isEmpty()) {
            dashboard = GrafanaDashboard.find("testId = ?1 AND (tags IS NULL OR tags = '')", testId).firstResult();
         } else {
            dashboard = GrafanaDashboard.find("testId = ?1 AND tags = ?2", testId, tags).firstResult();
         }
         if (dashboard == null) {
            dashboard = new GrafanaDashboard();
            dashboard.testId = testId;
            dashboard.tags = tags;
            dashboard.panels = new ArrayList<>();
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
         } catch (Throwable t) {
            log.error("Failure in transaction", t);
            tm.setRollbackOnly();
            throw t;
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
   public Response recalculate(@QueryParam("test") Integer testId, @QueryParam("notify") boolean notify) {
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
            try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
               @SuppressWarnings("unchecked")
               List<Integer> ids = em.createNativeQuery("SELECT id FROM run WHERE testid = ?1 order by start").setParameter(1, testId).getResultList();
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
                  try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
                     Run run = Run.findById(runId);
                     onNewRun(run, false);
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
