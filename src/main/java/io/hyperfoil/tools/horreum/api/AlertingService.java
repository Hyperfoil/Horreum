package io.hyperfoil.tools.horreum.api;

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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
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
import javax.ws.rs.ProcessingException;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.hyperfoil.tools.horreum.entity.alerting.CalculationLog;
import io.hyperfoil.tools.horreum.entity.alerting.LastMissingRunNotification;
import io.hyperfoil.tools.horreum.regression.RegressionModel;
import io.hyperfoil.tools.horreum.regression.StatisticalVarianceRegressionModel;
import io.hyperfoil.tools.yaup.StringUtil;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.hibernate.jpa.TypedParameterValue;
import org.hibernate.transform.AliasToEntityMapResultTransformer;
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
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
@Path("/api/alerting")
public class AlertingService {
   private static final Logger log = Logger.getLogger(AlertingService.class);

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
   SqlService sqlService;

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

   @ConfigProperty(name = "horreum.internal.url")
   String internalUrl;

   @Inject
   TransactionManager tm;

   @Inject
   Vertx vertx;

   @Inject
   NotificationService notificationService;

   // entries can be removed from timer thread while normally this is updated from one of blocking threads
   private final ConcurrentMap<Integer, Recalculation> recalcProgress = new ConcurrentHashMap<>();

   @Transactional
   @ConsumeEvent(value = Run.EVENT_NEW, blocking = true)
   public void onNewRun(Run run) {
      boolean showNotifications;
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, Collections.singleton(HORREUM_ALERTING))) {
         showNotifications = (Boolean) em.createNativeQuery("SELECT notificationsenabled FROM test WHERE id = ?")
               .setParameter(1, run.testid).getSingleResult();
      } catch (NoResultException e) {
         showNotifications = true;
      }
      onNewRun(run, showNotifications, false, null);
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
            em.createNativeQuery("DROP TABLE current_run").executeUpdate();
         }
      }
   }

   private void emitDatapoints(Run run, boolean notify, boolean debug, Recalculation recalculation) {
      String firstLevelSchema = run.data.getString("$schema");
      List<String> schemas = run.data.values().stream()
            .filter(Json.class::isInstance).map(Json.class::cast)
            .map(json -> json.getString("$schema")).filter(Objects::nonNull)
            .collect(Collectors.toList());
      if (firstLevelSchema != null) {
         schemas.add(firstLevelSchema);
      }

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

      for (var entry: allAccessors.entrySet()) {
         String accessor = entry.getKey();
         boolean isArray = SchemaExtractor.isArray(accessor);
         String column = StringUtil.quote(isArray ? SchemaExtractor.arrayName(accessor) + "___arr" : accessor, "\"");
         List<AccessorInfo> matching = entry.getValue().stream().filter(ai -> schemas.contains(ai.schema)).collect(Collectors.toList());
         if (matching.isEmpty()) {
            if (recalculation != null) {
               recalculation.runsWithoutAccessor.add(run.id);
            }
            logCalculationMessage(run.testid, run.id, CalculationLog.WARN,
                  "Accessor %s referenced from variables %s cannot be extracted: requires one of these schemas: %s", accessor,
                  vars.values().stream().filter(var -> var.accessors.contains(accessor)).map(var -> var.name).collect(Collectors.toList()),
                  entry.getValue().stream().map(ai -> ai.schema).collect(Collectors.toList()));
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
               appendPathQuery(extractionQuery, ai.schema.equals(firstLevelSchema), isArray, ai.jsonpath);
            }
            extractionQuery.append("])::::text as ").append(column);
         } else {
            AccessorInfo ai = matching.get(0);
            appendPathQuery(extractionQuery, ai.schema.equals(firstLevelSchema), isArray, ai.jsonpath);
            extractionQuery.append("::::text as ").append(column);
         }
      }

      extractionQuery.append(" FROM current_run");
      Query extraction = em.createNativeQuery(extractionQuery.toString());

      SqlService.setResultTransformer(extraction, AliasToEntityMapResultTransformer.INSTANCE);
      Map<String, String> extracted;
      try {
         @SuppressWarnings("unchecked")
         Map<String, Object> result = (Map<String, Object>) extraction.getSingleResult();
         extracted = result.entrySet().stream()
               .filter(e -> e.getValue() instanceof String)
               .collect(Collectors.toMap(e -> e.getKey().toLowerCase(), e -> String.valueOf(e.getValue())));
      } catch (NoResultException e) {
         log.errorf("Run %d does not exist in the database!", run.id);
         return;
      }
      if (debug) {
         String data = extracted.isEmpty() ? "&lt;no data&gt;" : extracted.entrySet().stream().map(e -> (e.getKey() + " -> " + e.getValue())).collect(Collectors.joining("\n"));
         logCalculationMessage(run.testid, run.id, CalculationLog.DEBUG, "Fetched values for these accessors:<pre>%s</pre>", data);
      }

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
            String column = toColumn(accessor);
            String value = extracted.get(column);
            if (value == null) {
               logCalculationMessage(run.testid, run.id, CalculationLog.INFO, "Null value for variable %s, accessor %s - datapoint is not created", var.name, accessor);
               if (recalculation != null) {
                  recalculation.runsWithoutValue.add(run.id);
               }
               continue;
            }
            String maybeNumber = value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"' ?
                  value.substring(1, value.length() - 1) : value;
            try {
               dataPoint.value = Double.parseDouble(maybeNumber);
            } catch (NumberFormatException e) {
               logCalculationMessage(run.testid, run.id, CalculationLog.ERROR, "Cannot turn %s into a floating-point value for variable %s: %s", value, var.name, e.getMessage());
               if (recalculation != null) {
                  recalculation.errors++;
               }
               continue;
            }
         } else {
            StringBuilder code = new StringBuilder();
            if (var.accessors.size() > 1) {
               code.append("const __obj = {\n");
               for (String accessor : var.accessors) {
                  String column = toColumn(accessor);
                  String value = extracted.get(column);
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
               String column = toColumn(var.accessors.stream().findFirst().orElseThrow());
               String value = extracted.get(column);
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
               continue;
            }
            dataPoint.value = value;
         }
         dataPoint.persist();
         publishLater(DataPoint.EVENT_NEW, new DataPoint.Event(dataPoint, notify));
      }
   }

   private String toColumn(String accessor) {
      String column = accessor.toLowerCase();
      if (SchemaExtractor.isArray(accessor)) {
         column = SchemaExtractor.arrayName(column) + "___arr";
      }
      return column;
   }

   private void appendPathQuery(StringBuilder query, boolean isFirstLevel, boolean isArray, String jsonpath) {
      if (isArray) {
         query.append(", jsonb_path_query_array(data, '");
      } else {
         query.append(", jsonb_path_query_first(data, '");
      }
      if (isFirstLevel) {
         query.append("$");
      } else {
         query.append("$.*");
      }
      // four colons to escape it for Hibernate
      query.append(jsonpath).append("'::::jsonpath)");
   }

   private void logCalculationMessage(int testId, int runId, int level, String format, Object... args) {
      new CalculationLog(testId, runId, level, String.format(format, args)).persist();
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

   private Double execute(int testId, int runId, String jsCode, String name) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (Context context = Context.newBuilder("js").out(out).err(out).build()) {
         context.enter();
         try {
            Value value = context.eval("js", jsCode);
            if (value.isNumber()) {
               return value.asDouble();
            } else if (value.isString()) {
               try {
                  return Double.parseDouble(value.asString());
               } catch (NumberFormatException e) {
                  logCalculationMessage(testId, runId, CalculationLog.ERROR, "Evaluation for variable %s failed: Return value %s cannot be parsed into a number.", name, value);
                  return null;
               }
            } else if (value.isNull()) {
               // returning null is intentional or the data does not exist, don't warn
               logCalculationMessage(testId, runId, CalculationLog.INFO, "Result for variable %s is null, skipping.", name);
               return null;
            } else if ("undefined".equals(value.toString())) {
               // returning undefined is intentional, don't warn
               logCalculationMessage(testId, runId, CalculationLog.INFO, "Result for variable %s is undefined, skipping.", name);
               return null;
            } else {
               logCalculationMessage(testId, runId, CalculationLog.ERROR, "Evaluation for variable %s failed: Return value %s is not a number.", name, value);
               return null;
            }
         } catch (PolyglotException e) {
            logCalculationMessage(testId, runId, CalculationLog.ERROR, "Evaluation for variable %s failed: '%s' Code:<pre>%s</pre>", name, e.getMessage(), jsCode);
            return null;
         } finally {
            if (out.size() > 0) {
               logCalculationMessage(testId, runId, CalculationLog.DEBUG, "Output while calculating variable %s: <pre>%s</pre>", name, out.toString());
            }
            context.leave();
         }
      }
   }

   @RolesAllowed(Roles.TESTER)
   @GET
   @Path("log/{testId}")
   public List<CalculationLog> getCalculationLog(@PathParam("testId") Integer testId,
                                                 @QueryParam("page") Integer page,
                                                 @QueryParam("limit") Integer limit) {
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

   @RolesAllowed(Roles.TESTER)
   @GET
   @Path("log/{testId}/count")
   public long getLogCount(@PathParam("testId") Integer testId) {
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
            publishLater(Change.EVENT_NEW, new Change.Event(change, event.notify));
         });
      }
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
   public Response variables(@QueryParam("test") Integer testId, List<Variable> variables) {
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
         return Response.ok().build();
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

   private DashboardInfo createDashboard(int testId, String tags, List<Variable> variables) throws SystemException {
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
         tm.setRollbackOnly();
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

   @PermitAll
   @GET
   @Path("dashboard")
   @Transactional
   public Response dashboard(@QueryParam("test") Integer testId, @QueryParam("tags") String tags) throws SystemException {
      if (testId == null) {
         return Response.status(Response.Status.BAD_REQUEST).entity("Missing param 'test'").build();
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
               return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("Cannot update Grafana dashboard.").build();
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
   public Response recalculate(@QueryParam("test") Integer testId, @QueryParam("notify") boolean notify,
                               @QueryParam("debug") boolean debug, @QueryParam("from") Long from, @QueryParam("to") Long to) {
      if (testId == null) {
         return Response.status(Response.Status.BAD_REQUEST).entity("Missing param 'test'").build();
      }
      SecurityIdentity identity = CachedSecurityIdentity.of(this.identity);

      vertx.executeBlocking(promise -> {
         Recalculation recalculation = withTx(() -> {
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
               return r;
            }
         });
         if (recalculation != null) {
            int completed = 0;
            recalcProgress.put(testId, recalculation);
            for (int runId : recalculation.runs) {
               // Since the evaluation might take few moments and we're dealing potentially with thousands
               // of runs we'll process each run in a separate transaction
               withTx(() -> {
                  try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
                     Run run = Run.findById(runId);
                     onNewRun(run, false, debug, recalculation);
                  }
                  return null;
               });
               recalculation.progress = 100 * ++completed / recalculation.runs.size();
            }
            recalculation.done = true;
            vertx.setTimer(30_000, timerId -> recalcProgress.remove(testId, recalculation));
         }
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
      Recalculation recalculation = recalcProgress.get(testId);
      Json json = new Json(false);
      json.add("percentage", recalculation == null ? 100 : recalculation.progress);
      json.add("done", recalculation == null || recalculation.done);
      if (recalculation != null) {
         json.add("totalRuns", recalculation.runs.size());
         json.add("errors", recalculation.errors);
         json.add("runsWithoutAccessor", recalculation.runsWithoutAccessor);
         json.add("runsWithoutValue", recalculation.runsWithoutValue);
      }
      return Response.ok(json).build();
   }

   @Transactional
   @Scheduled(every = "{horreum.alerting.missing.runs.check}")
   public void checkRuns() {
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

   @PermitAll
   @POST
   @Path("/datapoint/last")
   public Json findLastDatapoints(Json params) {
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         Json variables = params.getJson("variables");
         Map<String, String> tags = Tags.parseTags(params.getString("tags"));
         StringBuilder sql = new StringBuilder("SELECT DISTINCT ON(variable_id) variable_id, EXTRACT(EPOCH FROM timestamp) * 1000 ")
            .append(" FROM datapoint LEFT JOIN run_tags on run_tags.runid = datapoint.runid ");
         int counter = Tags.addTagQuery(tags, sql, 1);
         sql.append(" WHERE variable_id = ANY(?").append(counter).append(") ORDER BY variable_id, timestamp DESC;");
         Query query = em.createNativeQuery(sql.toString());
         counter = Tags.addTagValues(tags, query, 1);
         query.setParameter(counter, new TypedParameterValue(LongArrayType.INSTANCE, variables.values().toArray()));
         @SuppressWarnings("unchecked")
         List<Object[]> rows = query.getResultList();
         Json.ArrayBuilder result = new Json.ArrayBuilder();
         for (Object[] row: rows) {
            result.add(new Json.MapBuilder().add("variable", row[0]).add("timestamp", row[1]).build());
         }
         return result.build();
      }
   }

   public static class DashboardInfo {
      public int testId;
      public String uid;
      public String url;
      public List<PanelInfo> panels = new ArrayList<>();
   }

   public static class PanelInfo {
      public String name;
      public List<Variable> variables;

      public PanelInfo(String name, List<Variable> variables) {
         this.name = name;
         this.variables = variables;
      }
   }

   private static class AccessorInfo {
      final String schema;
      final String jsonpath;

      private AccessorInfo(String schema, String jsonpath) {
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

   private static class VarInfo {
      final int id;
      final String name;
      final String calculation;
      final Set<String> accessors = new HashSet<>();

      private VarInfo(int id, String name, String calculation) {
         this.id = id;
         this.name = name;
         this.calculation = calculation;
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
