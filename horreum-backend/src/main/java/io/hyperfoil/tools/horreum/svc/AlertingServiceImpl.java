package io.hyperfoil.tools.horreum.svc;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.transaction.InvalidTransactionException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import io.hyperfoil.tools.horreum.api.AlertingService;
import io.hyperfoil.tools.horreum.entity.alerting.CalculationLog;
import io.hyperfoil.tools.horreum.entity.alerting.RunExpectation;
import io.hyperfoil.tools.horreum.entity.alerting.LastMissingRunNotification;
import io.hyperfoil.tools.horreum.regression.RegressionModel;
import io.hyperfoil.tools.horreum.regression.StatisticalVarianceRegressionModel;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.hibernate.jpa.TypedParameterValue;
import org.hibernate.transform.Transformers;
import org.jboss.logging.Logger;

import com.vladmihalcea.hibernate.type.array.LongArrayType;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;
import io.hyperfoil.tools.horreum.entity.alerting.Variable;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.SchemaExtractor;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.grafana.Dashboard;
import io.hyperfoil.tools.horreum.grafana.GrafanaClient;
import io.hyperfoil.tools.horreum.grafana.Target;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

@ApplicationScoped
public class AlertingServiceImpl implements AlertingService {
   private static final Logger log = Logger.getLogger(AlertingServiceImpl.class);

   //@formatter:off
   private static final String LOOKUP_VARS =
         "WITH vars AS (" +
         "   SELECT id, name, calculation, unnest(string_to_array(accessors, ';')) as accessor FROM variable" +
         "   WHERE testid = ?" +
         ") SELECT vars.id as vid, vars.name as name, vars.calculation, vars.accessor, se.jsonpath, schema.uri  FROM vars " +
         "JOIN schemaextractor se ON se.accessor = replace(vars.accessor, '[]', '') " +
         "JOIN schema ON schema.id = se.schema_id;";

   private static final String LOOKUP_STALE =
         "WITH last_run AS (" +
         "   SELECT DISTINCT ON (run.testid, run_tags.tags) run.id, run.testid, " +
         "      EXTRACT(EPOCH FROM run.start) * 1000 AS timestamp, run_tags.tags FROM run " +
         "   JOIN run_tags ON run_tags.runid = run.id " +
         "   ORDER BY run.testid, run_tags.tags, run.start DESC " +
         ") SELECT last_run.testid, last_run.tags::::text, last_run.id, last_run.timestamp, ts.maxStaleness FROM last_run " +
         "JOIN test ON test.id = last_run.testid " +
         "JOIN test_stalenesssettings ts ON last_run.testid = ts.test_id AND " +
         "     (ts.tags IS NULL OR ts.tags @> last_run.tags AND last_run.tags @> ts.tags) " +
         "LEFT JOIN lastmissingrunnotification lmrn ON last_run.testid = lmrn.testid " +
         "   AND lmrn.tags @> last_run.tags AND last_run.tags @> lmrn.tags " +
         "WHERE test.notificationsenabled = true " +
         "   AND timestamp < EXTRACT(EPOCH FROM current_timestamp) * 1000 - ts.maxstaleness " +
         "   AND (lmrn.lastnotification IS NULL OR " +
         "       (EXTRACT(EPOCH FROM current_timestamp) - EXTRACT(EPOCH FROM lmrn.lastnotification))* 1000 > ts.maxstaleness)";
   //@formatter:on
   // the :::: is used instead of :: as Hibernate converts four-dot into colon
   private static final String UPLOAD_RUN = "CREATE TEMPORARY TABLE current_run AS SELECT * FROM (VALUES (?::::jsonb)) as t(data);";
   static final String HORREUM_ALERTING = "horreum.alerting";
   private static final Sort SORT_BY_TIMESTAMP_DESCENDING = Sort.by("timestamp", Sort.Direction.Descending);

   @Inject
   SqlServiceImpl sqlService;

   @Inject
   TestServiceImpl testService;

   @Inject
   EntityManager em;

   @Inject
   EventBus eventBus;

   @Inject
   SecurityIdentity identity;

   @Inject @RestClient
   GrafanaClient grafana;

   @ConfigProperty(name = "horreum.grafana.url")
   Optional<String> grafanaBaseUrl;

   @ConfigProperty(name = "horreum.grafana.update.datasource")
   Optional<Boolean> updateGrafanaDatasource;

   @ConfigProperty(name = "horreum.test")
   Optional<Boolean> isTest;

   @ConfigProperty(name = "horreum.internal.url")
   String internalUrl;

   @Inject
   TransactionManager tm;

   @Inject
   Vertx vertx;

   @Inject
   NotificationServiceImpl notificationService;

   // entries can be removed from timer thread while normally this is updated from one of blocking threads
   private final ConcurrentMap<Integer, Recalculation> recalcProgress = new ConcurrentHashMap<>();

   @Transactional
   @ConsumeEvent(value = Run.EVENT_NEW, blocking = true)
   public void onNewRun(Run run) {
      boolean sendNotifications;
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, Collections.singleton(HORREUM_ALERTING))) {
         sendNotifications = (Boolean) em.createNativeQuery("SELECT notificationsenabled FROM test WHERE id = ?")
               .setParameter(1, run.testid).getSingleResult();
      } catch (NoResultException e) {
         sendNotifications = true;
      }
      onNewRun(run, sendNotifications, false, null);
   }

   @PostConstruct
   void init() {
      if (grafanaBaseUrl.isPresent() && updateGrafanaDatasource.orElse(true)) {
         vertx.setTimer(1, this::setupGrafanaDatasource);
      }
   }

   private void setupGrafanaDatasource(@SuppressWarnings("unused") long timerId) {
      vertx.executeBlocking(promise -> {
         setupGrafanaDatasource();
         promise.complete();
      }, false, null);
   }

   private void setupGrafanaDatasource() {
      String url = internalUrl + "/api/grafana";
      try {
         boolean create = true;
         for (GrafanaClient.Datasource ds : grafana.listDatasources()) {
            if (ds.name.equals("Horreum")) {
               if (!url.equals(ds.url) && ds.id != null) {
                  log.infof("Deleting Grafana datasource %d: has URL %s, expected %s", ds.id, ds.url, url);
                  grafana.deleteDatasource(ds.id);
               } else {
                  create = false;
               }
            }
         }
         if (create) {
            GrafanaClient.Datasource newDatasource = new GrafanaClient.Datasource();
            newDatasource.url = url;
            grafana.addDatasource(newDatasource);
         }
         vertx.setTimer(60000, this::setupGrafanaDatasource);
      } catch (ProcessingException | WebApplicationException e) {
         log.warn("Cannot set up datasource, retry in 5 seconds.", e);
         vertx.setTimer(5000, this::setupGrafanaDatasource);
      }
   }

   private void onNewRun(Run run, boolean notify, boolean debug, Recalculation recalculation) {
      log.infof("Received run ID %d", run.id);
      // In order to create datapoints we'll use the horreum.alerting ownership
      List<String> roles = Arrays.asList(run.owner, HORREUM_ALERTING);

      // TODO: We will have the JSONPaths in PostgreSQL format while the Run
      // itself is available here in the application.
      // We'll use the database as a library function
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, roles)) {

         Query setRun = em.createNativeQuery(UPLOAD_RUN);
         setRun.setParameter(1, run.data.toString());
         setRun.executeUpdate();
         try {
            emitDatapoints(run, notify, debug, recalculation);
         } catch (Throwable t) {
            log.error("Failed to create new datapoints", t);
         } finally {
            try {
               if (tm.getTransaction().getStatus() == Status.STATUS_ACTIVE) {
                  em.createNativeQuery("DROP TABLE current_run").executeUpdate();
               }
            } catch (SystemException e) {
               log.error("Failure getting current transaction status.", e);
            }
         }
      }
   }

   private void emitDatapoints(Run run, boolean notify, boolean debug, Recalculation recalculation) {
      String firstLevelSchema = run.data.getString("$schema");
      Map<String, Object> schemas = run.data.stream()
            .filter(entry -> entry.getValue() instanceof Json && ((Json) entry.getValue()).has("$schema"))
            .collect(Collectors.toMap(
                  entry -> ((Json) entry.getValue()).getString("$schema"),
                  Map.Entry::getKey));
      if (firstLevelSchema != null) {
         schemas.put(firstLevelSchema, null);
      }

      // Make sure that the return type will be Object[]
      StringBuilder extractionQuery = new StringBuilder("SELECT 1");
      Map<Integer, VarInfo> vars = new HashMap<>();
      Map<String, Set<AccessorInfo>> allAccessors = new HashMap<>();

      Query lookup = em.createNativeQuery(LOOKUP_VARS);
      lookup.setParameter(1, run.testid);
      @SuppressWarnings("unchecked")
      List<Object[]> varSelection = lookup.getResultList();

      for (Object[] row : varSelection) {
         int id = (Integer) row[0];
         String name = (String) row[1];
         String calc = (String) row[2];
         String accessor = (String) row[3];
         String jsonpath = (String) row[4];
         String schema = (String) row[5];
         VarInfo var = vars.computeIfAbsent(id, i -> new VarInfo(id, name, calc));
         var.accessors.add(accessor);
         Set<AccessorInfo> accessors = allAccessors.computeIfAbsent(accessor, a -> new HashSet<>());
         // note that as we do single query there may be array and non-array variant for different variables
         accessors.add(new AccessorInfo(schema, jsonpath));
      }
      if (allAccessors.isEmpty()) {
         log.infof("No regression vars for run %d, skipping.", run.id);
         return;
      }

      Map<String, Object> extracted = new HashMap<>();
      String[] names = new String[allAccessors.size()];
      int index = 0;
      for (var entry: allAccessors.entrySet()) {
         String accessor = entry.getKey();
         names[index++] = accessor;
         boolean isArray = SchemaExtractor.isArray(accessor);
         List<AccessorInfo> matching = entry.getValue().stream().filter(ai -> schemas.containsKey(ai.schema)).collect(Collectors.toList());
         if (matching.isEmpty()) {
            if (recalculation != null) {
               recalculation.runsWithoutAccessor.add(run.id);
            }
            logCalculationMessage(run.testid, run.id, CalculationLog.WARN,
                  "Accessor %s referenced from variables %s cannot be extracted: requires one of these schemas: %s", accessor,
                  vars.values().stream().filter(var -> var.accessors.contains(accessor)).map(var -> var.name).collect(Collectors.toList()),
                  entry.getValue().stream().map(ai -> ai.schema).collect(Collectors.toList()));
            extractionQuery.append(", NULL as _").append(index);
         } else if (matching.size() > 1) {
            // we want deterministic order (at least)
            matching.sort(Comparator.comparing(ai -> ai.schema));
            logCalculationMessage(run.testid, run.id, CalculationLog.WARN,
                  "Accessor %s referenced from variables %s is used for multiple schemas: %s", accessor,
                  vars.values().stream().filter(var -> var.accessors.contains(accessor)).map(var -> var.name).collect(Collectors.toList()),
                  matching.stream().map(ai -> ai.schema).collect(Collectors.toList()));
            extractionQuery.append(", to_json(array[");
            for (int i = 0; i < matching.size(); i++) {
               AccessorInfo ai = matching.get(i);
               if (i != 0) extractionQuery.append(", ");
               appendPathQuery(extractionQuery, schemas.get(ai.schema), isArray, ai.jsonpath);
            }
            extractionQuery.append("])::::text as _").append(index);
         } else {
            extractionQuery.append(", ");
            AccessorInfo ai = matching.get(0);
            appendPathQuery(extractionQuery, schemas.get(ai.schema), isArray, ai.jsonpath);
            extractionQuery.append("::::text as _").append(index);
         }
      }

      if (index > 0) {
         extractionQuery.append(" FROM current_run");
         Query extraction = em.createNativeQuery(extractionQuery.toString());

         Object[] result;
         try {
            result = (Object[]) extraction.getSingleResult();
         } catch (NoResultException e) {
            log.errorf("Run %d does not exist in the database!", run.id);
            return;
         } catch (PersistenceException e) {
            log.errorf(e, "Failed to extract regression variables for run %d", run.id);
            Transaction old;
            try {
               old = tm.suspend();
               try {
                  sqlService.withTx(() -> {
                     try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, Collections.singletonList(HORREUM_ALERTING))) {
                        logCalculationMessage(run.testid, run.id, CalculationLog.ERROR, "Failed to extract regression variables from database. This is likely due to a malformed JSONPath in one of extractors.");
                        return null;
                     }
                  });
               } finally {
                  tm.resume(old);
               }
            } catch (SystemException | InvalidTransactionException e2) {
               log.error("Failed to switch to a different transaction for logging.", e2);
            }
            return;
         }
         for (int i = 0; i < names.length; i++) {
            extracted.put(names[i], result[i + 1]);
         }
      }

      if (debug) {
         String data = extracted.isEmpty() ? "&lt;no data&gt;" : extracted.entrySet().stream().map(e -> (e.getKey() + " -> " + e.getValue())).collect(Collectors.joining("\n"));
         logCalculationMessage(run.testid, run.id, CalculationLog.DEBUG, "Fetched values for these accessors:<pre>%s</pre>", data);
      }

      Set<String> missingValueVariables = new HashSet<>();
      for (VarInfo var : vars.values()) {
         DataPoint dataPoint = new DataPoint();
         // TODO: faking the variable
         Variable variable = new Variable();
         variable.id = var.id;
         dataPoint.variable = variable;
         dataPoint.runId = run.id;
         dataPoint.timestamp = run.start;

         if (var.calculation == null || var.calculation.isEmpty()) {
            if (var.accessors.size() > 1) {
               logCalculationMessage(run.testid, run.id, CalculationLog.WARN, "Variable %s has more than one accessor (%s) but no calculation function.", var.name, var.accessors);
            }
            String accessor = var.accessors.stream().findFirst().orElseThrow();
            Object value = extracted.get(accessor);
            if (value == null) {
               logCalculationMessage(run.testid, run.id, CalculationLog.INFO, "Null value for variable %s, accessor %s - datapoint is not created", var.name, accessor);
               if (recalculation != null) {
                  recalculation.runsWithoutValue.add(run.id);
               }
               missingValueVariables.add(var.name);
               continue;
            }
            Double number = Util.toDoubleOrNull(value);
            if (number == null) {
               logCalculationMessage(run.testid, run.id, CalculationLog.ERROR, "Cannot turn %s into a floating-point value for variable %s", value, var.name);
               if (recalculation != null) {
                  recalculation.errors++;
               }
               missingValueVariables.add(var.name);
               continue;
            } else {
               dataPoint.value = number;
            }
         } else {
            StringBuilder code = new StringBuilder();
            if (var.accessors.size() > 1) {
               code.append("const __obj = {\n");
               for (String accessor : var.accessors) {
                  Object value = extracted.get(accessor);
                  if (SchemaExtractor.isArray(accessor)) {
                     code.append(SchemaExtractor.arrayName(accessor));
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
               Object value = extracted.get(var.accessors.stream().findFirst().orElseThrow());
               appendValue(code, value);
               code.append(";\n");
            }
            code.append("const __func = ").append(var.calculation).append(";\n");
            code.append("__func(__obj)");
            if (debug) {
               logCalculationMessage(run.testid, run.id, CalculationLog.DEBUG, "Variable %s, <pre>%s</pre>" , var.name, code.toString());
            }
            Double value = execute(run.testid, run.id, code.toString(), var.name);
            if (value == null) {
               if (recalculation != null) {
                  recalculation.runsWithoutValue.add(run.id);
               }
               missingValueVariables.add(var.name);
               continue;
            }
            dataPoint.value = value;
         }
         dataPoint.persist();
         Util.publishLater(tm, eventBus, DataPoint.EVENT_NEW, new DataPoint.Event(dataPoint, notify));
      }
      if (!missingValueVariables.isEmpty()) {
         Util.publishLater(tm, eventBus, Run.EVENT_MISSING_VALUES, new MissingRunValuesEvent(run.id, run.testid, missingValueVariables, notify));
      }
   }

   private void appendPathQuery(StringBuilder query, Object topKey, boolean isArray, String jsonpath) {
      if (isArray) {
         query.append("jsonb_path_query_array(data, '");
      } else {
         query.append("jsonb_path_query_first(data, '");
      }
      if (topKey == null) {
         query.append("$");
      } else if (topKey instanceof Integer) {
         query.append("$[").append((int) topKey).append("]");
      } else {
         query.append("$.\"").append(String.valueOf(topKey).replaceAll("\"", "\\\"")).append('"');
      }
      // four colons to escape it for Hibernate
      query.append(jsonpath).append("'::::jsonpath)");
   }

   private void logCalculationMessage(int testId, int runId, int level, String format, Object... args) {
      new CalculationLog(testId, runId, level, String.format(format, args)).persist();
   }

   private void appendValue(StringBuilder code, Object value) {
      if (value == null) {
         code.append("undefined");
         return;
      }
      if (value instanceof Long) {
         code.append((long) value);
      } else if (value instanceof Integer) {
         code.append((int) value);
      } else if (value instanceof Number){
         code.append(((Number) value).doubleValue());
      } else {
         String str = value.toString();
         if (str.charAt(0) == '"' && str.charAt(str.length() - 1) == '"') {
            String maybeNumber = str.substring(1, str.length() - 1);
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
   }

   private Double execute(int testId, int runId, String jsCode, String name) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (Context context = Context.newBuilder("js").out(out).err(out).build()) {
         context.enter();
         try {
            Value value = context.eval("js", jsCode);
            return Util.toDoubleOrNull(value, error -> {
               logCalculationMessage(testId, runId, CalculationLog.ERROR, "Evaluation of variable %s failed: %s", name, error);
            }, info -> {
               logCalculationMessage(testId, runId, CalculationLog.INFO, "Evaluation of variable %s: %s", name, info);
            });
         } catch (PolyglotException e) {
            logCalculationMessage(testId, runId, CalculationLog.ERROR, "Evaluation of variable %s failed: '%s' Code:<pre>%s</pre>", name, e.getMessage(), jsCode);
            return null;
         } finally {
            if (out.size() > 0) {
               logCalculationMessage(testId, runId, CalculationLog.DEBUG, "Output while calculating variable %s: <pre>%s</pre>", name, out.toString());
            }
            context.leave();
         }
      }
   }

   @Override
   @RolesAllowed(Roles.TESTER)
   public List<CalculationLog> getCalculationLog(Integer testId, Integer page, Integer limit) {
      if (testId == null) {
         return Collections.emptyList();
      }
      if (page == null) {
         page = 0;
      }
      if (limit == null) {
         limit = 25;
      }
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         return CalculationLog.find("testId = ?1", Sort.descending("timestamp"), testId).page(Page.of(page, limit)).list();
      }
   }

   @Override
   @RolesAllowed(Roles.TESTER)
   public long getLogCount(Integer testId) {
      if (testId == null) return -1;
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         return CalculationLog.count("testId = ?1", testId);
      }
   }

   @Transactional
   @ConsumeEvent(value = Run.EVENT_TRASHED, blocking = true)
   public void onRunTrashed(Integer runId) {
      log.infof("Trashing datapoints for run %d", runId);
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, Collections.singletonList(HORREUM_ALERTING))) {
         Query deleteChanges = em.createNativeQuery("DELETE FROM change WHERE runid = ?");
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
         log.debugf("Processing new datapoint for run %d, variable %d (%s), value %f", dataPoint.runId,
               dataPoint.variable.id, variable != null ? variable.name : "<unknown>", dataPoint.value);
         Change lastChange = Change.find("variable = ?1", SORT_BY_TIMESTAMP_DESCENDING, variable).range(0, 0).firstResult();
         PanacheQuery<DataPoint> query;
         if (lastChange == null) {
            query = DataPoint.find("variable", SORT_BY_TIMESTAMP_DESCENDING, variable);
         } else {
            if (lastChange.timestamp.compareTo(dataPoint.timestamp) > 0) {
               // We won't revision changes until next variable recalculation
               log.debugf("Ignoring datapoint %d from %s as there is a newer change %d from %s.",
                     dataPoint.id, dataPoint.timestamp, lastChange.id, lastChange.timestamp);
               return;
            }
            log.debugf("Filtering datapoints newer than %s (change %d)", lastChange.timestamp, lastChange.id);
            query = DataPoint.find("variable = ?1 AND timestamp >= ?2", SORT_BY_TIMESTAMP_DESCENDING, variable, lastChange.timestamp);
         }
         List<DataPoint> dataPoints = query.list();

         RegressionModel regressionModel = new StatisticalVarianceRegressionModel();

         regressionModel.analyze(dataPoint, dataPoints, change -> {
            em.persist(change);
            Util.publishLater(tm, eventBus, Change.EVENT_NEW, new Change.Event(change, event.notify));
         });
      }
   }

   @Override
   @PermitAll
   public List<Variable> variables(Integer testId) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         if (testId != null) {
            return Variable.list("testid", testId);
         } else {
            return Variable.listAll();
         }
      }
   }

   @Override
   @RolesAllowed("tester")
   @Transactional
   public void variables(Integer testId, List<Variable> variables) {
      if (testId == null) {
         throw ServiceException.badRequest("Missing query param 'test'");
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
               current.minWindow = matching.minWindow;
               current.maxDifferenceFloatingWindow = matching.maxDifferenceFloatingWindow;
               current.floatingWindow = matching.floatingWindow;
               current.persist();
            }
         }
         for (Variable variable : variables) {
            if (currentVariables.stream().noneMatch(v -> v.id.equals(variable.id))) {
               if (variable.id == null || variable.id <= 0) {
                  variable.id = null;
               }
               variable.testId = testId;
               variable.persist(); // insert
            }
         }

         if (grafanaBaseUrl.isPresent()) {
            try {
               for (var dashboard : grafana.searchDashboard("", "testId=" + testId)) {
                  grafana.deleteDashboard(dashboard.uid);
               }
            } catch (ProcessingException | WebApplicationException e) {
               log.warnf(e, "Failed to delete dasboards for test %d", testId);
            }
         }

         em.flush();
      } catch (PersistenceException e) {
         throw new WebApplicationException(e, Response.serverError().build());
      }
   }

   private GrafanaClient.GetDashboardResponse findDashboard(int testId, String tags) {
      try {
         List<GrafanaClient.DashboardSummary> list = grafana.searchDashboard("", testId + ":" + tags);
         if (list.isEmpty()) {
            return null;
         } else {
            return grafana.getDashboard(list.get(0).uid);
         }
      } catch (ProcessingException | WebApplicationException e) {
         log.debugf(e, "Error looking up dashboard for test %d, tags %s", testId, tags);
         return null;
      }
   }

   private DashboardInfo createDashboard(int testId, String tags, List<Variable> variables) {
      DashboardInfo info = new DashboardInfo();
      info.testId = testId;
      Dashboard dashboard = new Dashboard();
      dashboard.title = Test.<Test>findByIdOptional(testId).map(t -> t.name).orElse("Test " + testId)
            + (tags.isEmpty() ? "" : ", " + tags);
      dashboard.tags.add(testId + ";" + tags);
      dashboard.tags.add("testId=" + testId);
      int i = 0;
      Map<String, List<Variable>> byGroup = groupedVariables(variables);
      for (Variable variable : variables) {
         dashboard.annotations.list.add(new Dashboard.Annotation(variable.name, variable.id + ";" + tags));
      }
      for (Map.Entry<String, List<Variable>> entry : byGroup.entrySet()) {
         entry.getValue().sort(Comparator.comparing(v -> v.order));
         Dashboard.Panel panel = new Dashboard.Panel(entry.getKey(), new Dashboard.GridPos(12 * (i % 2), 9 * (i / 2), 12, 9));
         info.panels.add(new PanelInfo(entry.getKey(), entry.getValue()));
         for (Variable variable : entry.getValue()) {
            panel.targets.add(new Target(variable.id + ";" + tags, "timeseries", "T" + i));
         }
         dashboard.panels.add(panel);
         ++i;
      }
      try {
         GrafanaClient.DashboardSummary response = grafana.createOrUpdateDashboard(new GrafanaClient.PostDashboardRequest(dashboard, true));
         info.uid = response.uid;
         info.url = grafanaBaseUrl.get() + response.url;
         return info;
      } catch (WebApplicationException e) {
         log.errorf(e, "Failed to create/update dashboard %s", dashboard.uid);
         try {
            tm.setRollbackOnly();
         } catch (SystemException systemException) {
            throw ServiceException.serverError("Failure in transaction");
         }
         return null;
      }
   }

   private Map<String, List<Variable>> groupedVariables(List<Variable> variables) {
      Map<String, List<Variable>> byGroup = new TreeMap<>();
      for (Variable variable : variables) {
         byGroup.computeIfAbsent(variable.group == null || variable.group.isEmpty() ? variable.name : variable.group, g -> new ArrayList<>()).add(variable);
      }
      return byGroup;
   }

   @Override
   @PermitAll
   @Transactional
   public DashboardInfo dashboard(Integer testId, String tags) {
      if (testId == null) {
         throw ServiceException.badRequest("Missing param 'test'");
      }
      if (tags == null) {
         tags = "";
      }
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         GrafanaClient.GetDashboardResponse response = findDashboard(testId, tags);
         List<Variable> variables = Variable.list("testid", testId);
         DashboardInfo dashboard;
         if (response == null) {
            dashboard = createDashboard(testId, tags, variables);
            if (dashboard == null) {
               throw new ServiceException(Response.Status.SERVICE_UNAVAILABLE, "Cannot update Grafana dashboard.");
            }
         } else {
            dashboard = new DashboardInfo();
            dashboard.testId = testId;
            dashboard.uid = response.dashboard.uid;
            dashboard.url = response.meta.url;
            for (var entry : groupedVariables(variables).entrySet()) {
               dashboard.panels.add(new PanelInfo(entry.getKey(), entry.getValue()));
            }
         }
         return dashboard;
      }
   }

   @Override
   @PermitAll
   public List<Change> changes(Integer varId) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, Collections.singletonList(HORREUM_ALERTING))) {
         Variable v = Variable.findById(varId);
         if (v == null) {
            throw ServiceException.notFound("Variable " + varId + " not found");
         }
         // TODO: Avoid sending variable in each datapoint
         return Change.list("variable", v);
      }
   }

   @Override
   @RolesAllowed(Roles.TESTER)
   @Transactional
   public void updateChange(Integer id, Change change) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, Collections.singletonList(HORREUM_ALERTING))) {
         if (id != change.id) {
            throw ServiceException.badRequest("Path ID and entity don't match");
         }
         em.merge(change);
      } catch (PersistenceException e) {
         throw new WebApplicationException(e, Response.serverError().build());
      }
   }

   @Override
   @RolesAllowed(Roles.TESTER)
   @Transactional
   public void deleteChange(Integer id) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, Collections.singletonList(HORREUM_ALERTING))) {
         if (!Change.deleteById(id)) {
            throw ServiceException.notFound("Change not found");
         }
      }
   }

   @Override
   @RolesAllowed(Roles.TESTER)
   public void recalculate(Integer testId, boolean notify,
                           boolean debug, Long from, Long to) {
      if (testId == null) {
         throw ServiceException.badRequest("Missing param 'test'");
      }
      SecurityIdentity identity = CachedSecurityIdentity.of(this.identity);

      vertx.executeBlocking(promise -> {
         Recalculation recalculation = null;
         try {
            recalculation = sqlService.withTx(() -> {
               Recalculation r = new Recalculation();
               if (recalcProgress.putIfAbsent(testId, r) != null) {
                  promise.complete();
                  return null;
               }
               try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
                  Query query = em.createNativeQuery("SELECT id FROM run WHERE testid = ?1 AND (EXTRACT(EPOCH FROM start) * 1000 BETWEEN ?2 AND ?3) AND NOT run.trashed ORDER BY start")
                        .setParameter(1, testId)
                        .setParameter(2, from == null ? Long.MIN_VALUE : from)
                        .setParameter(3, to == null ? Long.MAX_VALUE : to);
                  @SuppressWarnings("unchecked")
                  List<Integer> ids = query.getResultList();
                  r.runs = ids;
                  DataPoint.delete("runId in ?1", ids);
                  Change.delete("runId in ?1 AND confirmed = false", ids);
                  if (ids.size() > 0) {
                     // Due to RLS policies we cannot add a record to a run we don't own
                     logCalculationMessage(testId, ids.get(0), CalculationLog.INFO, "Starting recalculation of %d runs.", ids.size());
                  }
                  return r;
               }
            });
            if (recalculation != null) {
               int numRuns = recalculation.runs == null ? 0 : recalculation.runs.size();
               log.infof("Starting recalculation of test %d, %d runs", testId, numRuns);
               int completed = 0;
               recalcProgress.put(testId, recalculation);
               for (int runId : recalculation.runs) {
                  // Since the evaluation might take few moments and we're dealing potentially with thousands
                  // of runs we'll process each run in a separate transaction
                  Recalculation r = recalculation;
                  sqlService.withTx(() -> {
                     try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
                        Run run = Run.findById(runId);
                        onNewRun(run, false, debug, r);
                     }
                     return null;
                  });
                  recalculation.progress = 100 * ++completed / numRuns;
               }
            }
         } catch (Throwable t) {
            log.error("Recalculation failed", t);
            throw t;
         } finally {
            if (recalculation != null) {
               recalculation.done = true;
               Recalculation r = recalculation;
               vertx.setTimer(30_000, timerId -> recalcProgress.remove(testId, r));
            }
            promise.complete();
         }
      }, result -> {});
   }

   @Override
   @RolesAllowed(Roles.TESTER)
   public RecalculationStatus recalculateProgress(Integer testId) {
      if (testId == null) {
         throw ServiceException.badRequest("Missing param 'test'");
      }
      Recalculation recalculation = recalcProgress.get(testId);
      RecalculationStatus status = new RecalculationStatus();
      status.percentage = recalculation == null ? 100 : recalculation.progress;
      status.done = recalculation == null || recalculation.done;
      if (recalculation != null) {
         status.totalRuns = recalculation.runs.size();
         status.errors = recalculation.errors;
         status.runsWithoutAccessor = recalculation.runsWithoutAccessor;
         status.runsWithoutValue = recalculation.runsWithoutValue;
      }
      return status;
   }

   @Transactional
   @Scheduled(every = "{horreum.alerting.missing.runs.check}")
   public void checkMissingRuns() {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, Collections.singletonList(HORREUM_ALERTING))) {
         @SuppressWarnings("unchecked")
         List<Object[]> results = em.createNativeQuery(LOOKUP_STALE).getResultList();
         for (Object[] row : results) {
            int testId = ((Number) row[0]).intValue();
            Json tags = Json.fromString(String.valueOf(row[1]));
            int runId = ((Number) row[2]).intValue();
            long lastRunTimestamp = ((Number) row[3]).longValue();
            long maxStaleness = ((Number) row[4]).longValue();
            notificationService.notifyMissingRun(testId, tags, maxStaleness, runId, lastRunTimestamp);
            LastMissingRunNotification last = LastMissingRunNotification.find("testid = ?1 AND tags = ?2", testId, tags).firstResult();
            if (last == null) {
               last = new LastMissingRunNotification();
               last.testId = testId;
               last.tags = tags;
            }
            last.lastNotification = Instant.now();
            last.persist();
         }
      }
   }

   @Override
   @PermitAll
   public List<DatapointLastTimestamp> findLastDatapoints(Json params) {
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         Json variables = params.getJson("variables");
         Map<String, String> tags = Tags.parseTags(params.getString("tags"));
         StringBuilder sql = new StringBuilder("SELECT DISTINCT ON(variable_id) variable_id AS variable, EXTRACT(EPOCH FROM timestamp) * 1000 AS timestamp")
            .append(" FROM datapoint LEFT JOIN run_tags on run_tags.runid = datapoint.runid ");
         int counter = Tags.addTagQuery(tags, sql, 1);
         sql.append(" WHERE variable_id = ANY(?").append(counter).append(") ORDER BY variable_id, timestamp DESC;");
         Query query = em.createNativeQuery(sql.toString());
         counter = Tags.addTagValues(tags, query, 1);
         query.setParameter(counter, new TypedParameterValue(LongArrayType.INSTANCE, variables.values().toArray()));
         SqlServiceImpl.setResultTransformer(query, Transformers.aliasToBean(DatapointLastTimestamp.class));
         //noinspection unchecked
         return query.getResultList();
      }
   }

   @RolesAllowed(Roles.UPLOADER)
   @Override
   @Transactional
   public void expectRun(String testNameOrId, Long timeoutSeconds, String tags, String expectedBy, String backlink) {
      if (timeoutSeconds == null) {
         throw ServiceException.badRequest("No timeout set.");
      } else if (timeoutSeconds <= 0) {
         throw ServiceException.badRequest("Timeout must be positive (unit: seconds)");
      }
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         Test test = testService.getByNameOrId(testNameOrId);
         if (test == null) {
            throw ServiceException.notFound("Test " + testNameOrId + " does not exist.");
         }
         RunExpectation runExpectation = new RunExpectation();
         runExpectation.testId = test.id;
         runExpectation.tags = tags != null && !tags.isEmpty() ? Json.fromString(tags) : null;
         runExpectation.expectedBefore = Instant.now().plusSeconds(timeoutSeconds);
         runExpectation.expectedBy = expectedBy != null ? expectedBy : identity.getPrincipal().getName();
         runExpectation.backlink = backlink;
         runExpectation.persist();
      }
   }

   @PermitAll
   @Override
   public List<RunExpectation> expectations() {
      if (!isTest.orElse(false)) {
         throw ServiceException.notFound("Not available without test mode.");
      }
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, HORREUM_ALERTING)) {
         return RunExpectation.listAll();
      }
   }

   @ConsumeEvent(value = Run.EVENT_TAGS_CREATED, blocking = true)
   @Transactional
   public void removeExpected(Run.TagsEvent event) {
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, Collections.singleton(HORREUM_ALERTING))) {
         Query query = em.createNativeQuery("DELETE FROM run_expectation WHERE testid = (SELECT testid FROM run WHERE id = ?1) AND (tags IS NULL OR tags @> ?2 ::::jsonb AND ?2 ::::jsonb @> tags)");
         query.setParameter(1, event.runId);
         query.setParameter(2, event.tags != null ? event.tags : "{}");
         int updated = query.executeUpdate();
         if (updated > 0) {
            log.infof("Removed %d run expectations as run %d with tags %s was added.", updated, event.runId, event.tags);
         }
      }
   }

   @Transactional
   @Scheduled(every = "{horreum.alerting.missing.runs.check}")
   public void checkExpectedRuns() {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, Collections.singletonList(HORREUM_ALERTING))) {
         for (RunExpectation expectation : RunExpectation.<RunExpectation>find("expectedbefore < ?1", Instant.now()).list()) {
            boolean sendNotifications = (Boolean) em.createNativeQuery("SELECT notificationsenabled FROM test WHERE id = ?")
                  .setParameter(1, expectation.testId).getSingleResult();
            if (sendNotifications) {
               notificationService.notifyExpectedRun(expectation.testId, expectation.tags, expectation.expectedBefore.toEpochMilli(), expectation.expectedBy, expectation.backlink);
            } else {
               log.debugf("Skipping expected run notification on test %d since it is disabled.", expectation.testId);
            }
            expectation.delete();
         }
      }
   }

   private static class Recalculation {
      List<Integer> runs = Collections.emptyList();
      int progress;
      boolean done;
      public int errors;
      Set<Integer> runsWithoutAccessor = new HashSet<>();
      Set<Integer> runsWithoutValue = new HashSet<>();
   }
}
