package io.hyperfoil.tools.horreum.svc;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import io.hyperfoil.tools.horreum.api.AlertingService;
import io.hyperfoil.tools.horreum.api.ConditionConfig;
import io.hyperfoil.tools.horreum.changedetection.FixedThresholdModel;
import io.hyperfoil.tools.horreum.entity.PersistentLog;
import io.hyperfoil.tools.horreum.entity.alerting.DatasetLog;
import io.hyperfoil.tools.horreum.entity.alerting.ChangeDetection;
import io.hyperfoil.tools.horreum.entity.alerting.MissingDataRule;
import io.hyperfoil.tools.horreum.entity.alerting.MissingDataRuleResult;
import io.hyperfoil.tools.horreum.entity.alerting.RunExpectation;
import io.hyperfoil.tools.horreum.changedetection.ChangeDetectionModel;
import io.hyperfoil.tools.horreum.changedetection.RelativeDifferenceChangeDetectionModel;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hibernate.Hibernate;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.AliasToBeanResultTransformer;
import org.hibernate.transform.Transformers;
import org.hibernate.type.IntegerType;
import org.hibernate.type.TextType;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vladmihalcea.hibernate.type.array.IntArrayType;
import com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.DataPoint;
import io.hyperfoil.tools.horreum.entity.alerting.Variable;
import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.grafana.Dashboard;
import io.hyperfoil.tools.horreum.grafana.GrafanaClient;
import io.hyperfoil.tools.horreum.grafana.Target;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

@ApplicationScoped
@Startup
public class AlertingServiceImpl implements AlertingService {
   private static final Logger log = Logger.getLogger(AlertingServiceImpl.class);

   //@formatter:off
   private static final String LOOKUP_VARIABLES =
         "SELECT var.id as variableId, var.name, var.\"group\", var.calculation, jsonb_array_length(var.labels) AS numLabels, (CASE " +
            "WHEN jsonb_array_length(var.labels) = 1 THEN jsonb_agg(lv.value)->0 " +
            "ELSE COALESCE(jsonb_object_agg(label.name, lv.value) FILTER (WHERE label.name IS NOT NULL), '{}'::::jsonb) " +
         "END) AS value FROM variable var " +
         "LEFT JOIN label ON json_contains(var.labels, label.name) LEFT JOIN label_values lv ON label.id = lv.label_id " +
         "WHERE var.testid = ?1 AND lv.dataset_id = ?2 " +
         "GROUP BY var.id, var.name, var.\"group\", var.calculation";

   private static final String LOOKUP_RULE_LABEL_VALUES =
         "SELECT mdr.id AS rule_id, mdr.condition, " +
         "(CASE " +
            "WHEN mdr.labels IS NULL OR jsonb_array_length(mdr.labels) = 0 THEN NULL " +
            "WHEN jsonb_array_length(mdr.labels) = 1 THEN jsonb_agg(lv.value)->0 " +
            "ELSE COALESCE(jsonb_object_agg(label.name, lv.value) FILTER (WHERE label.name IS NOT NULL), '{}'::::jsonb) " +
         "END) as value FROM missingdata_rule mdr " +
         "LEFT JOIN label ON json_contains(mdr.labels, label.name) " +
         "LEFT JOIN label_values lv ON label.id = lv.label_id AND lv.dataset_id = ?1 " +
         "WHERE mdr.test_id = ?2 " +
         "GROUP BY rule_id, mdr.condition";

   private static final String LOOKUP_LABEL_VALUE_FOR_RULE =
         "SELECT (CASE " +
            "WHEN mdr.labels IS NULL OR jsonb_array_length(mdr.labels) = 0 THEN NULL " +
            "WHEN jsonb_array_length(mdr.labels) = 1 THEN jsonb_agg(lv.value)->0 " +
            "ELSE COALESCE(jsonb_object_agg(label.name, lv.value) FILTER (WHERE label.name IS NOT NULL), '{}'::::jsonb) " +
         "END) as value FROM missingdata_rule mdr " +
         "LEFT JOIN label ON json_contains(mdr.labels, label.name) " +
         "LEFT JOIN label_values lv ON label.id = lv.label_id AND lv.dataset_id = ?1 " +
         "WHERE mdr.id = ?2 " +
         "GROUP BY mdr.labels";

   private static final String LOOKUP_RECENT =
         "SELECT DISTINCT ON(mdr.id) mdr.id, mdr.test_id, mdr.name, mdr.maxstaleness, rr.timestamp " +
         "FROM missingdata_rule mdr " +
         "LEFT JOIN missingdata_ruleresult rr ON mdr.id = rr.rule_id " +
         "WHERE last_notification IS NULL OR EXTRACT(EPOCH FROM last_notification) * 1000 < EXTRACT(EPOCH FROM current_timestamp) * 1000 - mdr.maxstaleness " +
         "ORDER BY mdr.id, timestamp DESC";

   private static final String FIND_LAST_DATAPOINTS =
         "SELECT DISTINCT ON(variable_id) variable_id AS variable, EXTRACT(EPOCH FROM timestamp) * 1000 AS timestamp " +
         "FROM datapoint dp LEFT JOIN fingerprint fp ON fp.dataset_id = dp.dataset_id " +
         "WHERE ((fp.fingerprint IS NULL AND (?1)::::jsonb IS NULL) OR json_equals(fp.fingerprint, (?1)::::jsonb)) AND variable_id = ANY(?2) " +
         "ORDER BY variable_id, timestamp DESC;";
   //@formatter:on
   private static final Instant LONG_TIME_AGO = Instant.ofEpochSecond(0);

   private static final Map<String, ChangeDetectionModel> MODELS = Map.of(
         RelativeDifferenceChangeDetectionModel.NAME, new RelativeDifferenceChangeDetectionModel(),
         FixedThresholdModel.NAME, new FixedThresholdModel());

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

   long grafanaDatasourceTimerId;

   // entries can be removed from timer thread while normally this is updated from one of blocking threads
   private final ConcurrentMap<Integer, Recalculation> recalcProgress = new ConcurrentHashMap<>();

   static {
      System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   @ConsumeEvent(value = DataSet.EVENT_LABELS_UPDATED, blocking = true)
   public void onLabelsUpdated(DataSet.LabelsUpdatedEvent event) {
      boolean sendNotifications;
      DataSet dataset = DataSet.findById(event.datasetId);
      if (dataset == null) {
         // The run is not committed yet?
         vertx.setTimer(1000, timerId -> Util.executeBlocking(vertx,
               CachedSecurityIdentity.ANONYMOUS, () -> onLabelsUpdated(event)
         ));
         return;
      }
      if (event.isRecalculation) {
         sendNotifications = false;
      } else {
         try {
            sendNotifications = (Boolean) em.createNativeQuery("SELECT notificationsenabled FROM test WHERE id = ?")
                  .setParameter(1, dataset.testid).getSingleResult();
         } catch (NoResultException e) {
            sendNotifications = true;
         }
      }
      recalculateDatapointsForDataset(dataset, sendNotifications, false, null);
      recalculateMissingDataRules(dataset);
   }

   private void recalculateMissingDataRules(DataSet dataset) {
      MissingDataRuleResult.deleteForDataset(dataset.id);
      @SuppressWarnings("unchecked") List<Object[]> ruleValues = em.createNativeQuery(LOOKUP_RULE_LABEL_VALUES)
            .setParameter(1, dataset.id).setParameter(2, dataset.testid)
            .unwrap(NativeQuery.class)
            .addScalar("rule_id", IntegerType.INSTANCE)
            .addScalar("condition", TextType.INSTANCE)
            .addScalar("value", JsonNodeBinaryType.INSTANCE)
            .getResultList();
      Util.evaluateMany(ruleValues, row -> (String) row[1], row -> (JsonNode) row[2],
            (row, result) -> {
               int ruleId = (int) row[0];
               if (result.isBoolean()) {
                  if (result.asBoolean()) {
                     createMissingDataRuleResult(dataset, ruleId);
                  }
               } else {
                  logMissingDataMessage(dataset, PersistentLog.ERROR,
                        "Result for missing data rule %d, dataset %d is not a boolean: %s", ruleId, dataset.id, result);
               }
            },
            // Absence of condition means that this dataset is taken into account. This happens e.g. when value == NULL
            row -> createMissingDataRuleResult(dataset, (int) row[0]),
            (row, exception, code) -> logMissingDataMessage(dataset, PersistentLog.ERROR, "Exception evaluating missing data rule %d, dataset %d: '%s' Code: <pre>%s</pre>", row[0], dataset.id, exception.getMessage(), code),
            output -> logMissingDataMessage(dataset, PersistentLog.DEBUG, "Output while evaluating missing data rules for dataset %d: '%s'", dataset.id, output));
   }

   private void createMissingDataRuleResult(DataSet dataset, int ruleId) {
      new MissingDataRuleResult(ruleId, dataset.id, dataset.start).persist();
   }

   @PostConstruct
   void init() {
      if (grafanaBaseUrl.isPresent() && updateGrafanaDatasource.orElse(true)) {
         setupGrafanaDatasource(0);
      }
   }

   @PreDestroy
   void destroy() {
      synchronized (this) {
         // The timer is not cancelled automatically during live reload:
         // https://github.com/quarkusio/quarkus/issues/25254
         vertx.cancelTimer(grafanaDatasourceTimerId);
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
            log.info("Creating new Horreum datasource in Grafana");
            GrafanaClient.Datasource newDatasource = new GrafanaClient.Datasource();
            newDatasource.url = url;
            grafana.addDatasource(newDatasource);
         }
         scheduleNextSetup(10000);
      } catch (ProcessingException | WebApplicationException e) {
         log.warn("Cannot set up Horreum datasource in Grafana , retrying in 5 seconds.", e);
         scheduleNextSetup(5000);
      }
   }

   private void scheduleNextSetup(int delay) {
      synchronized (this) {
         vertx.cancelTimer(grafanaDatasourceTimerId);
         grafanaDatasourceTimerId = vertx.setTimer(delay, this::setupGrafanaDatasource);
      }
   }

   private void recalculateDatapointsForDataset(DataSet dataset, boolean notify, boolean debug, Recalculation recalculation) {
      log.infof("Analyzing dataset %d (%d/%d)", dataset.id, dataset.run.id, dataset.ordinal);
      try {
         Test test = Test.findById(dataset.testid);
         if (test == null) {
            log.errorf("Cannot load test ID %d", dataset.testid);
            return;
         }
         if (!testFingerprint(dataset, test.fingerprintFilter)) {
            return;
         }

         emitDatapoints(dataset, notify, debug, recalculation);
      } catch (Throwable t) {
         log.error("Failed to create new datapoints", t);
      }
   }

   private boolean testFingerprint(DataSet dataset, String filter) {
      if (filter == null || filter.isBlank()) {
         return true;
      }
      @SuppressWarnings("unchecked") Optional<JsonNode> result =
            em.createNativeQuery("SELECT fp.fingerprint FROM fingerprint fp WHERE dataset_id = ?1")
                  .setParameter(1, dataset.id)
                  .unwrap(NativeQuery.class).addScalar("fingerprint", JsonNodeBinaryType.INSTANCE)
                  .getResultStream().findFirst();
      JsonNode fingerprint;
      if (result.isPresent()) {
         fingerprint = result.get();
         if (fingerprint.isObject() && fingerprint.size() == 1) {
            fingerprint = fingerprint.elements().next();
         }
      } else {
         fingerprint = JsonNodeFactory.instance.nullNode();
      }
      boolean testResult = Util.evaluateTest(filter, fingerprint,
            value -> {
               logCalculationMessage(dataset, PersistentLog.ERROR, "Evaluation of fingerprint failed: '%s' is not a boolean", value);
               return false;
            },
            (code, e) -> logCalculationMessage(dataset, PersistentLog.ERROR, "Evaluation of fingerprint filter failed: '%s' Code:<pre>%s</pre>", e.getMessage(), code),
            output -> logCalculationMessage(dataset, PersistentLog.DEBUG, "Output while evaluating fingerprint filter: <pre>%s</pre>", output));
      if (!testResult) {
         logCalculationMessage(dataset, PersistentLog.DEBUG, "Fingerprint %s was filtered out.", fingerprint);
      }
      return testResult;
   }

   public static class VariableData {
      public int variableId;
      public String name;
      public String group;
      public String calculation;
      public int numLabels;
      public JsonNode value;

      public String fullName() {
         return (group == null || group.isEmpty()) ? name : group + "/" + name;
      }
   }

   private void emitDatapoints(DataSet dataset, boolean notify, boolean debug, Recalculation recalculation) {
      Set<String> missingValueVariables = new HashSet<>();
      @SuppressWarnings("unchecked")
      List<VariableData> values = em.createNativeQuery(LOOKUP_VARIABLES)
            .setParameter(1, dataset.testid)
            .setParameter(2, dataset.id)
            .unwrap(NativeQuery.class)
            .addScalar("variableId", IntegerType.INSTANCE)
            .addScalar("name", TextType.INSTANCE)
            .addScalar("group", TextType.INSTANCE)
            .addScalar("calculation", TextType.INSTANCE)
            .addScalar("numLabels", IntegerType.INSTANCE)
            .addScalar("value", JsonNodeBinaryType.INSTANCE)
            .setResultTransformer(new AliasToBeanResultTransformer(VariableData.class))
            .getResultList();
      if (debug) {
         for (VariableData data : values) {
            logCalculationMessage(dataset, PersistentLog.DEBUG, "Fetched value for variable %s: <pre>%s</pre>", data.fullName(), data.value);
         }
      }
      Util.evaluateMany(values, data -> data.calculation, data -> data.value,
            (data, result) -> {
               Double value = Util.toDoubleOrNull(result,
                     error -> logCalculationMessage(dataset, PersistentLog.ERROR, "Evaluation of variable %s failed: %s", data.fullName(), error),
                     info -> logCalculationMessage(dataset, PersistentLog.INFO, "Evaluation of variable %s: %s", data.fullName(), info));
               if (value != null) {
                  createDataPoint(dataset, data.variableId, value, notify);
               } else {
                  if (recalculation != null) {
                     recalculation.datasetsWithoutValue.put(dataset.id, dataset.getInfo());
                  }
                  missingValueVariables.add(data.fullName());
               }
            },
            data -> {
               if (data.numLabels > 1) {
                  logCalculationMessage(dataset, PersistentLog.WARN, "Variable %s has more than one label (%s) but no calculation function.", data.fullName(), data.value.fieldNames());
               }
               if (data.value == null || data.value.isNull()) {
                  logCalculationMessage(dataset, PersistentLog.INFO, "Null value for variable %s - datapoint is not created", data.fullName());
                  if (recalculation != null) {
                     recalculation.datasetsWithoutValue.put(dataset.id, dataset.getInfo());
                  }
                  missingValueVariables.add(data.fullName());
                  return;
               }

               Double value = null;
               if (data.value.isNumber()) {
                  value = data.value.asDouble();
               } else if (data.value.isTextual()) {
                  try {
                     value = Double.parseDouble(data.value.asText());
                  } catch (NumberFormatException e) {
                     // ignore
                  }
               }
               if (value == null) {
                  logCalculationMessage(dataset, PersistentLog.ERROR, "Cannot turn %s into a floating-point value for variable %s", data.value, data.fullName());
                  if (recalculation != null) {
                     recalculation.errors++;
                  }
                  missingValueVariables.add(data.fullName());
               } else {
                  createDataPoint(dataset, data.variableId, value, notify);
               }
            },
            (data, exception, code) -> logCalculationMessage(dataset, PersistentLog.ERROR, "Evaluation of variable %s failed: '%s' Code:<pre>%s</pre>", data.fullName(), exception.getMessage(), code),
            output -> logCalculationMessage(dataset, PersistentLog.DEBUG, "Output while calculating variable: <pre>%s</pre>", output)
      );
      if (!missingValueVariables.isEmpty()) {
         Util.publishLater(tm, eventBus, DataSet.EVENT_MISSING_VALUES, new MissingValuesEvent(dataset.run.id, dataset.id, dataset.ordinal, dataset.testid, missingValueVariables, notify));
      }
      Util.publishLater(tm, eventBus, DataSet.EVENT_DATAPOINTS_CREATED, dataset.getInfo());
   }

   private void createDataPoint(DataSet dataset, int variableId, double value, boolean notify) {
      DataPoint dataPoint = new DataPoint();
      dataPoint.variable = em.getReference(Variable.class, variableId);
      dataPoint.dataset = dataset;
      dataPoint.timestamp = dataset.start;
      dataPoint.value = value;
      dataPoint.persist();
      Util.publishLater(tm, eventBus, DataPoint.EVENT_NEW, new DataPoint.Event(dataPoint, dataset.testid, notify));
   }

   private void logCalculationMessage(DataSet dataSet, int level, String format, Object... args) {
      logCalculationMessage(dataSet.testid, dataSet.id, level, format, args);
   }

   private void logCalculationMessage(int testId, int datasetId, int level, String format, Object... args) {
      String msg = args.length == 0 ? format : String.format(format, args);
      log.tracef("Logging %s for test %d, dataset %d: %s", PersistentLog.logLevel(level), testId, datasetId, msg);
      new DatasetLog(em.getReference(Test.class, testId), em.getReference(DataSet.class, datasetId),
            level, "variables", msg).persist();
   }

   private void logMissingDataMessage(DataSet dataSet, int level, String format, Object... args) {
      logMissingDataMessage(dataSet.testid, dataSet.id, level, format, args);
   }

   private void logMissingDataMessage(int testId, int datasetId, int level, String format, Object... args) {
      String msg = args.length == 0 ? format : String.format(format, args);
      log.tracef("Logging %s for test %d, dataset %d: %s", PersistentLog.logLevel(level), testId, datasetId, msg);
      new DatasetLog(em.getReference(Test.class, testId), em.getReference(DataSet.class, datasetId),
            level, "missingdata", msg).persist();
   }

   private void logChangeDetectionMessage(int testId, int datasetId, int level, String format, Object... args) {
      String msg = args.length == 0 ? format : String.format(format, args);
      log.tracef("Logging %s for test %d, dataset %d: %s", PersistentLog.logLevel(level), testId, datasetId, msg);
      new DatasetLog(em.getReference(Test.class, testId), em.getReference(DataSet.class, datasetId),
            level, "changes", msg).persist();
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   @ConsumeEvent(value = DataPoint.EVENT_NEW, blocking = true, ordered = true)
   public void onNewDataPoint(DataPoint.Event event) {
      DataPoint dataPoint = event.dataPoint;
      // The variable referenced by datapoint is a fake
      Variable variable = Variable.findById(dataPoint.variable.id);
      log.debugf("Processing new datapoint for run %d, variable %d (%s), value %f", dataPoint.dataset.id,
            dataPoint.variable.id, variable != null ? variable.name : "<unknown>", dataPoint.value);
      // We'll simply ignore newer changes as this recalculation might be a result of re-transformation of runs.
      // The changes that are about to come are going to be removed soon.
      Change lastChange = em.createQuery("SELECT c FROM Change c LEFT JOIN Fingerprint fp ON c.dataset.id = fp.dataset.id " +
            "WHERE c.variable = ?1 AND c.timestamp < ?2 AND " +
            "TRUE = function('json_equals', fp.fingerprint, (SELECT fp2.fingerprint FROM Fingerprint fp2 WHERE dataset.id = ?3)) " +
            "ORDER by c.timestamp DESC", Change.class)
            .setParameter(1, variable).setParameter(2, event.dataPoint.timestamp)
            .setParameter(3, event.dataPoint.dataset.id).setMaxResults(1)
            .getResultStream().findFirst().orElse(null);
      Instant changeTimestamp = LONG_TIME_AGO;
      if (lastChange != null) {
         log.debugf("Filtering datapoints newer than %s (change %d)", lastChange.timestamp, lastChange.id);
         changeTimestamp = lastChange.timestamp;
      }
      List<DataPoint> dataPoints = em.createQuery("SELECT dp FROM DataPoint dp LEFT JOIN Fingerprint fp ON dp.dataset.id = fp.dataset.id " +
            "JOIN dp.dataset " + // ignore datapoints (that were not deleted yet) from deleted datasets
            "WHERE dp.variable = ?1 AND dp.timestamp BETWEEN ?2 AND ?3 " +
            "AND TRUE = function('json_equals', fp.fingerprint, (SELECT fp2.fingerprint FROM Fingerprint fp2 WHERE dataset.id = ?4)) " +
            "ORDER BY dp.timestamp DESC", DataPoint.class)
            .setParameter(1, variable).setParameter(2, changeTimestamp)
            .setParameter(3, dataPoint.timestamp).setParameter(4, dataPoint.dataset.id).getResultList();
      // Last datapoint is already in the list
      if (dataPoints.isEmpty()) {
         log.error("The published datapoint should be already in the list");
         return;
      }
      DataPoint lastDatapoint = dataPoints.get(0);
      if (!lastDatapoint.id.equals(dataPoint.id)) {
         logChangeDetectionMessage(event.testId, dataPoint.getDatasetId(), PersistentLog.DEBUG,
               "Ignoring datapoint %d from %s - it is not the last datapoint in %s",
               dataPoint.id, dataPoint.timestamp, reversedAndLimited(dataPoints));
         return;
      }

      for (ChangeDetection detection : ChangeDetection.<ChangeDetection>find("variable", variable).list()) {
         ChangeDetectionModel model = MODELS.get(detection.model);
         if (model == null) {
            logChangeDetectionMessage(event.testId, dataPoint.id, PersistentLog.ERROR, "Cannot find change detection model %s", detection.model);
            continue;
         }
         model.analyze(dataPoints, detection.config, change -> {
            logChangeDetectionMessage(event.testId, dataPoint.id, PersistentLog.DEBUG,
                  "Change %s detected using datapoints %s", change, reversedAndLimited(dataPoints));
            Query datasetQuery = em.createNativeQuery("SELECT id, runid as \"runId\", ordinal FROM dataset WHERE id = ?1");
            SqlServiceImpl.setResultTransformer(datasetQuery, Transformers.aliasToBean(DataSet.Info.class));
            DataSet.Info info = (DataSet.Info) datasetQuery.setParameter(1, change.dataset.id).getSingleResult();
            em.persist(change);
            Hibernate.initialize(change.dataset.run.id);
            String testName = Test.<Test>findByIdOptional(event.testId).map(test -> test.name).orElse("<unknown>");
            Util.publishLater(tm, eventBus, Change.EVENT_NEW, new Change.Event(change, testName, info, event.notify));
         });
      }
   }

   private String reversedAndLimited(List<DataPoint> list) {
      int maxIndex = Math.min(list.size() - 1, 20);
      StringBuilder sb = new StringBuilder("[");
      if (maxIndex < list.size() - 1) {
         sb.append("..., ");
      }
      for (int i = maxIndex; i >= 0; --i) {
         sb.append(list.get(i));
         if (i != 0) sb.append(", ");
      }
      return sb.append("]").toString();
   }

   @Override
   @WithRoles
   @PermitAll
   public List<Variable> variables(Integer testId) {
      if (testId != null) {
         return Variable.list("testid", testId);
      } else {
         return Variable.listAll();
      }
   }

   @Override
   @WithRoles
   @RolesAllowed("tester")
   @Transactional
   public void updateVariables(int testId, List<Variable> variables) {
      for (Variable v : variables) {
         if (v.name == null || v.name.isBlank()) {
            throw ServiceException.badRequest("Variable name is mandatory!");
         } else if (v.labels == null || !v.labels.isArray() ||
               StreamSupport.stream(v.labels.spliterator(), false).anyMatch(l -> !l.isTextual())) {
            throw ServiceException.badRequest("Variable labels must be an array of label names");
         }
      }
      try {
         List<Variable> currentVariables = Variable.list("testid", testId);
         updateCollection(currentVariables, variables, v -> v.id, item -> {
            if (item.id != null && item.id <= 0) {
               item.id = null;
            }
            if (item.changeDetection != null) {
               ensureDefaults(item.changeDetection);
               item.changeDetection.forEach(rd -> rd.variable = item);
            }
            item.testId = testId;
                item.persist(); // insert
         }, (current, matching) -> {
            current.name = matching.name;
            current.group = matching.group;
            current.labels = matching.labels;
            current.calculation = matching.calculation;
            if (matching.changeDetection != null) {
               ensureDefaults(matching.changeDetection);
            }
            updateCollection(current.changeDetection, matching.changeDetection, rd -> rd.id, item -> {
               if (item.id != null && item.id <= 0) {
                  item.id = null;
               }
               item.variable = current;
               item.persist();
               current.changeDetection.add(item);
            }, (crd, mrd) -> {
               crd.model = mrd.model;
               crd.config = mrd.config;
            }, PanacheEntityBase::delete);
            current.persist();
         }, current -> {
            DataPoint.delete("variable_id", current.id);
            Change.delete("variable_id", current.id);
            current.delete();
         });

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

   private void ensureDefaults(Set<ChangeDetection> rds) {
      rds.forEach(rd -> {
         ChangeDetectionModel model = MODELS.get(rd.model);
         if (model == null) {
            throw ServiceException.badRequest("Unknown model " + rd.model);
         }
         if (!(rd.config instanceof ObjectNode)) {
            throw ServiceException.badRequest("Invalid config for model " + rd.model + " - not an object: " + rd.config);
         }
         for (var entry : model.config().defaults.entrySet()) {
            JsonNode property = rd.config.get(entry.getKey());
            if (property == null || property.isNull()) {
               ((ObjectNode) rd.config).set(entry.getKey(), entry.getValue());
            }
         }
      });
   }

   private <T> void updateCollection(Collection<T> currentList, Collection<T> newList, Function<T, Object> idSelector, Consumer<T> create, BiConsumer<T, T> update, Consumer<T> delete) {
      for (Iterator<T> iterator = currentList.iterator(); iterator.hasNext(); ) {
         T current = iterator.next();
         T matching = newList.stream().filter(v -> idSelector.apply(current).equals(idSelector.apply(v))).findFirst().orElse(null);
         if (matching == null) {
            delete.accept(current);
            iterator.remove();
         } else {
            update.accept(current, matching);
         }
      }
      for (T item : newList) {
         if (currentList.stream().noneMatch(v -> idSelector.apply(v).equals(idSelector.apply(item)))) {
            create.accept(item);
         }
      }
   }

   private GrafanaClient.GetDashboardResponse findDashboard(int testId, String fingerprint) {
      try {
         List<GrafanaClient.DashboardSummary> list = grafana.searchDashboard("", testId + ":" + fingerprint);
         if (list.isEmpty()) {
            return null;
         } else {
            return grafana.getDashboard(list.get(0).uid);
         }
      } catch (ProcessingException | WebApplicationException e) {
         log.debugf(e, "Error looking up dashboard for test %d, fingerprint %s", testId, fingerprint);
         return null;
      }
   }

   private DashboardInfo createDashboard(int testId, String fingerprint, List<Variable> variables) {
      DashboardInfo info = new DashboardInfo();
      info.testId = testId;
      Dashboard dashboard = new Dashboard();
      dashboard.title = Test.<Test>findByIdOptional(testId).map(t -> t.name).orElse("Test " + testId)
            + (fingerprint.isEmpty() ? "" : ", " + fingerprint);
      dashboard.tags.add(testId + ";" + fingerprint);
      dashboard.tags.add("testId=" + testId);
      int i = 0;
      Map<String, List<Variable>> byGroup = groupedVariables(variables);
      for (Variable variable : variables) {
         dashboard.annotations.list.add(new Dashboard.Annotation(variable.name, variable.id + ";" + fingerprint));
      }
      for (Map.Entry<String, List<Variable>> entry : byGroup.entrySet()) {
         entry.getValue().sort(Comparator.comparing(v -> v.name));
         Dashboard.Panel panel = new Dashboard.Panel(entry.getKey(), new Dashboard.GridPos(12 * (i % 2), 9 * (i / 2), 12, 9));
         info.panels.add(new PanelInfo(entry.getKey(), entry.getValue()));
         for (Variable variable : entry.getValue()) {
            panel.targets.add(new Target(variable.id + ";" + fingerprint, "timeseries", "T" + i));
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
   @WithRoles
   @PermitAll
   @Transactional
   public DashboardInfo dashboard(int testId, String fingerprint) {
      if (fingerprint == null) {
         fingerprint = "";
      }
      GrafanaClient.GetDashboardResponse response = findDashboard(testId, fingerprint);
      List<Variable> variables = Variable.list("testid", testId);
      DashboardInfo dashboard;
      if (response == null) {
         dashboard = createDashboard(testId, fingerprint, variables);
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

   @Override
   @WithRoles
   @PermitAll
   public List<Change> changes(int varId, String fingerprint) {
      Variable v = Variable.findById(varId);
      if (v == null) {
         throw ServiceException.notFound("Variable " + varId + " not found");
      }
      JsonNode fp = Util.parseFingerprint(fingerprint);
      if (fp == null) {
         return Change.list("variable", v);
      }
      //noinspection unchecked
      return em.createNativeQuery("SELECT change.* FROM change JOIN fingerprint fp ON change.dataset_id = fp.dataset_id " +
            "WHERE variable_id = ?1 AND json_equals(fp.fingerprint, ?2)", Change.class)
            .setParameter(1, varId).unwrap(NativeQuery.class)
            .setParameter(2, fp, JsonNodeBinaryType.INSTANCE)
            .getResultList();
   }

   @Override
   @WithRoles
   @RolesAllowed(Roles.TESTER)
   @Transactional
   public void updateChange(int id, Change change) {
      try {
         if (id != change.id) {
            throw ServiceException.badRequest("Path ID and entity don't match");
         }
         em.merge(change);
      } catch (PersistenceException e) {
         throw new WebApplicationException(e, Response.serverError().build());
      }
   }

   @Override
   @WithRoles
   @RolesAllowed(Roles.TESTER)
   @Transactional
   public void deleteChange(int id) {
      if (!Change.deleteById(id)) {
         throw ServiceException.notFound("Change not found");
      }
   }

   @Override
   @RolesAllowed(Roles.TESTER)
   public void recalculateDatapoints(int testId, boolean notify,
                                     boolean debug, Long from, Long to) {
      // We cannot use resteasy propagation because when the request completes the request data
      // are terminated anyway (it's not reference counter) - therefore we need to manually copy the identity
      // to the new context in a different thread.
      // CDI needs to be propagated - without that the interceptors wouldn't run.
      // Without thread context propagation we would get an exception in Run.findById, though the interceptors would be invoked correctly.
      Util.executeBlocking(vertx, new CachedSecurityIdentity(identity), () -> {
         Recalculation recalculation = new Recalculation();
         if (recalcProgress.putIfAbsent(testId, recalculation) != null) {
            log.infof("Already started recalculation on test %d, ignoring.", testId);
            return;
         }
         try {
            recalculation.datasets = getDatasetsForRecalculation(testId, from, to);
            int numRuns = recalculation.datasets.size();
            log.infof("Starting recalculation of test %d, %d runs", testId, numRuns);
            int completed = 0;
            recalcProgress.put(testId, recalculation);
            for (int datasetId : recalculation.datasets) {
               // Since the evaluation might take few moments and we're dealing potentially with thousands
               // of runs we'll process each run in a separate transaction
               recalulateForDataset(datasetId, notify, debug, recalculation);
               recalculation.progress = 100 * ++completed / numRuns;
            }

         } catch (Throwable t) {
            log.error("Recalculation failed", t);
            throw t;
         } finally {
            recalculation.done = true;
            vertx.setTimer(30_000, timerId -> recalcProgress.remove(testId, recalculation));
         }
      });
   }

   @WithRoles
   @Transactional
   List<Integer> getDatasetsForRecalculation(Integer testId, Long from, Long to) {
      Query query = em.createNativeQuery("SELECT id FROM dataset WHERE testid = ?1 AND (EXTRACT(EPOCH FROM start) * 1000 BETWEEN ?2 AND ?3) ORDER BY start")
            .setParameter(1, testId)
            .setParameter(2, from == null ? Long.MIN_VALUE : from)
            .setParameter(3, to == null ? Long.MAX_VALUE : to);
      @SuppressWarnings("unchecked")
      List<Integer> ids = query.getResultList();
      DataPoint.delete("dataset_id in ?1", ids);
      Change.delete("dataset_id in ?1 AND confirmed = false", ids);
      if (ids.size() > 0) {
         // Due to RLS policies we cannot add a record to a dataset we don't own
         logCalculationMessage(testId, ids.get(0), PersistentLog.INFO, "Starting recalculation of %d runs.", ids.size());
      }
      return ids;
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional(Transactional.TxType.REQUIRES_NEW)
   void recalulateForDataset(Integer datasetId, boolean notify, boolean debug, Recalculation recalculation) {
      DataSet dataset = DataSet.findById(datasetId);
      recalculateDatapointsForDataset(dataset, notify, debug, recalculation);
   }

   @Override
   @RolesAllowed(Roles.TESTER)
   public DatapointRecalculationStatus getRecalculationStatus(int testId) {
      Recalculation recalculation = recalcProgress.get(testId);
      DatapointRecalculationStatus status = new DatapointRecalculationStatus();
      status.percentage = recalculation == null ? 100 : recalculation.progress;
      status.done = recalculation == null || recalculation.done;
      if (recalculation != null) {
         status.totalDatasets = recalculation.datasets.size();
         status.errors = recalculation.errors;
         status.datasetsWithoutValue = recalculation.datasetsWithoutValue.values();
      }
      return status;
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   @Scheduled(every = "{horreum.alerting.missing.dataset.check}")
   public void checkMissingDataset() {
      @SuppressWarnings("unchecked")
      List<Object[]> results = em.createNativeQuery(LOOKUP_RECENT).getResultList();
      for (Object[] row : results) {
         int ruleId = (int) row[0];
         int testId = (int) row[1];
         String ruleName = (String) row[2];
         long maxStaleness = ((BigInteger) row[3]).longValue();
         Timestamp ts = (Timestamp) row[4];
         Instant timestamp = ts == null ? null : ts.toInstant();
         if (timestamp == null || timestamp.isBefore(Instant.now().minusMillis(maxStaleness))) {
            if (ruleName == null) {
               ruleName = "rule #" + ruleId;
            }
            notificationService.notifyMissingDataset(testId, ruleName, maxStaleness, timestamp);
            int numUpdated = em.createNativeQuery("UPDATE missingdata_rule SET last_notification = ?1 WHERE id = ?2")
                  .setParameter(1, Instant.now()).setParameter(2, ruleId).executeUpdate();
            if (numUpdated != 1) {
               log.errorf("Missing data rules update for rule %d (test %d) didn't work: updated: %d", ruleId, testId, numUpdated);
            }
         }
      }
   }

   @Override
   @WithRoles
   @PermitAll
   public List<DatapointLastTimestamp> findLastDatapoints(LastDatapointsParams params) {
      Query query = em.createNativeQuery(FIND_LAST_DATAPOINTS)
            .unwrap(NativeQuery.class)
            .setParameter(1, Util.parseFingerprint(params.fingerprint), JsonNodeBinaryType.INSTANCE)
            .setParameter(2, params.variables, IntArrayType.INSTANCE);
      SqlServiceImpl.setResultTransformer(query, Transformers.aliasToBean(DatapointLastTimestamp.class));
      //noinspection unchecked
      return query.getResultList();
   }

   @Override
   @RolesAllowed(Roles.UPLOADER)
   @WithRoles
   @Transactional
   public void expectRun(String testNameOrId, Long timeoutSeconds, String expectedBy, String backlink) {
      if (timeoutSeconds == null) {
         throw ServiceException.badRequest("No timeout set.");
      } else if (timeoutSeconds <= 0) {
         throw ServiceException.badRequest("Timeout must be positive (unit: seconds)");
      }
      Test test = testService.ensureTestExists(testNameOrId, null);
      RunExpectation runExpectation = new RunExpectation();
      runExpectation.testId = test.id;
      runExpectation.expectedBefore = Instant.now().plusSeconds(timeoutSeconds);
      runExpectation.expectedBy = expectedBy != null ? expectedBy : identity.getPrincipal().getName();
      runExpectation.backlink = backlink;
      runExpectation.persist();
   }

   @PermitAll
   @WithRoles(extras = Roles.HORREUM_ALERTING)
   @Override
   public List<RunExpectation> expectations() {
      if (!isTest.orElse(false)) {
         throw ServiceException.notFound("Not available without test mode.");
      }
      return RunExpectation.listAll();
   }

   @PermitAll
   @Override
   public List<ConditionConfig> changeDetectionModels() {
      return MODELS.values().stream().map(ChangeDetectionModel::config).collect(Collectors.toList());
   }

   @PermitAll
   @Override
   public List<ChangeDetection> defaultChangeDetectionConfigs() {
      ChangeDetection lastDatapoint = new ChangeDetection();
      lastDatapoint.model = RelativeDifferenceChangeDetectionModel.NAME;
      lastDatapoint.config = JsonNodeFactory.instance.objectNode()
            .put("window", 1).put("filter", "mean").put("threshold", 0.2).put("minPrevious", 5);
      ChangeDetection floatingWindow = new ChangeDetection();
      floatingWindow.model = RelativeDifferenceChangeDetectionModel.NAME;
      floatingWindow.config = JsonNodeFactory.instance.objectNode()
            .put("window", 5).put("filter", "mean").put("threshold", 0.1).put("minPrevious", 5);
      return Arrays.asList(lastDatapoint, floatingWindow);
   }

   @WithRoles
   @Override
   public List<MissingDataRule> missingDataRules(int testId) {
      if (testId <= 0) {
         throw ServiceException.badRequest("Invalid test ID: " + testId);
      }
      return MissingDataRule.list("test.id", testId);
   }

   @WithRoles
   @Transactional
   @Override
   public int updateMissingDataRule(int testId, MissingDataRule rule) {
      if (testId <= 0) {
         throw ServiceException.badRequest("Invalid test ID: " + testId);
      }
      if (rule.id != null && rule.id <= 0) {
         rule.id = null;
      }
      // drop any info about last notification
      rule.lastNotification = null;
      if (rule.maxStaleness <= 0) {
         throw ServiceException.badRequest("Invalid max staleness in rule " + rule.name + ": " + rule.maxStaleness);
      }

      if (rule.id == null) {
         rule.test = em.getReference(Test.class, testId);
         rule.persistAndFlush();
      } else {
         MissingDataRule existing = MissingDataRule.findById(rule.id);
         if (existing == null) {
            throw ServiceException.badRequest("Rule does not exist.");
         } else if (existing.test.id != testId) {
            throw ServiceException.badRequest("Rule belongs to a different test");
         }
         rule.test = existing.test;
         em.merge(rule);
         em.flush();
      }
      // The recalculations are executed in independent transactions, therefore we need to make sure that
      // this rule is committed in DB before starting to reevaluate it.
      Util.doAfterCommit(tm, () -> Util.executeBlocking(vertx, new CachedSecurityIdentity(identity), () -> {
         @SuppressWarnings("unchecked") List<Object[]> idsAndTimestamps = Util.withTx(tm,
               () -> em.createNativeQuery("SELECT id, start FROM dataset WHERE testid = ?1"
               ).setParameter(1, testId).getResultList());
         for (Object[] row : idsAndTimestamps) {
            recalculateMissingDataRule((int) row[0], ((Timestamp) row[1]).toInstant(), rule);
         }
      }));
      return rule.id;
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional(Transactional.TxType.REQUIRES_NEW)
   void recalculateMissingDataRule(int datasetId, Instant timestamp, MissingDataRule rule) {
      JsonNode value = (JsonNode) em.createNativeQuery(LOOKUP_LABEL_VALUE_FOR_RULE)
            .setParameter(1, datasetId).setParameter(2, rule.id)
            .unwrap(NativeQuery.class)
            .addScalar("value", JsonNodeBinaryType.INSTANCE)
            .getSingleResult();
      boolean match = true;
      if (rule.condition != null && !rule.condition.isBlank()) {
         String ruleName = rule.name == null ? "#" + rule.id : rule.name;
         match = Util.evaluateTest(rule.condition, value, notBoolean -> {
            logMissingDataMessage(rule.testId(), datasetId, PersistentLog.ERROR,
                  "Missing data rule %s result is not a boolean: %s", ruleName, notBoolean);
            return true;
         },
            (code, exception) -> logMissingDataMessage(rule.testId(), datasetId, PersistentLog.ERROR,
               "Error evaluating missing data rule %s: '%s' Code:<pre>%s</pre>", ruleName, exception.getMessage(), code),
            output -> logMissingDataMessage(rule.testId(), datasetId, PersistentLog.DEBUG,
                  "Output while evaluating missing data rule %s: '%s'", ruleName, output)
         );
      }
      if (match) {
         new MissingDataRuleResult(rule.id, datasetId, timestamp).persist();
      }
   }

   @WithRoles
   @Transactional
   @Override
   public void deleteMissingDataRule(int id) {
      MissingDataRule.deleteById(id);
   }

   @Override
   public String grafanaStatus() {
      try {
         grafana.listDatasources();
         return "OK";
      } catch (WebApplicationException e) {
         return "KO";
      }
   }

   @ConsumeEvent(value = Run.EVENT_NEW, blocking = true)
   @WithRoles(extras = Roles.HORREUM_ALERTING)
   @Transactional
   public void removeExpected(Run run) {
      // delete at most one expectation
      Query query = em.createNativeQuery("DELETE FROM run_expectation WHERE id = (SELECT id FROM run_expectation WHERE testid = (SELECT testid FROM run WHERE id = ?1) LIMIT 1)");
      query.setParameter(1, run.id);
      int updated = query.executeUpdate();
      if (updated > 0) {
         log.infof("Removed %d run expectations as run %d was added.", updated, run.id);
      }
   }

   @ConsumeEvent(value = DataSet.EVENT_DELETED, blocking = true)
   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   public void onDatasetDeleted(DataSet.Info info) {
      log.infof("Removing datasets and changes for dataset %d (%d/%d, test %d)", info.id, info.runId, info.ordinal, info.testId);
      for (DataPoint dp : DataPoint.<DataPoint>list("dataset_id", info.id)) {
         Util.publishLater(tm, eventBus, DataPoint.EVENT_DELETED, new DataPoint.Event(dp, info.testId, false));
         dp.delete();
      }
      for (Change c: Change.<Change>list("dataset_id = ?1 AND confirmed = false", info.id)) {
         c.delete();
      }
   }

   @Transactional
   @WithRoles(extras = Roles.HORREUM_ALERTING)
   @Scheduled(every = "{horreum.alerting.expected.run.check}")
   public void checkExpectedRuns() {
      for (RunExpectation expectation : RunExpectation.<RunExpectation>find("expectedbefore < ?1", Instant.now()).list()) {
         boolean sendNotifications = (Boolean) em.createNativeQuery("SELECT notificationsenabled FROM test WHERE id = ?")
               .setParameter(1, expectation.testId).getSingleResult();
         if (sendNotifications) {
            // We will perform this only if this transaction succeeds, to allow no-op retries
            Util.doAfterCommit(tm, () -> notificationService.notifyExpectedRun(expectation.testId,
                  expectation.expectedBefore.toEpochMilli(), expectation.expectedBy, expectation.backlink));
         } else {
            log.debugf("Skipping expected run notification on test %d since it is disabled.", expectation.testId);
         }
         expectation.delete();
      }
   }

   // Note: this class must be public - otherwise when this is used as a parameter to
   // a method in AlertingServiceImpl the interceptors would not be invoked.
   public static class Recalculation {
      List<Integer> datasets = Collections.emptyList();
      int progress;
      boolean done;
      public int errors;
      Map<Integer, DataSet.Info> datasetsWithoutValue = new HashMap<>();
   }
}
