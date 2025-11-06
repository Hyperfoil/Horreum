package io.hyperfoil.tools.horreum.svc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
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

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.graalvm.polyglot.Value;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.alerting.*;
import io.hyperfoil.tools.horreum.api.changes.Dashboard;
import io.hyperfoil.tools.horreum.api.changes.Target;
import io.hyperfoil.tools.horreum.api.data.*;
import io.hyperfoil.tools.horreum.api.data.changeDetection.ChangeDetectionModelType;
import io.hyperfoil.tools.horreum.api.internal.services.AlertingService;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.changedetection.ChangeDetectionException;
import io.hyperfoil.tools.horreum.changedetection.ChangeDetectionModel;
import io.hyperfoil.tools.horreum.changedetection.ChangeDetectionModelResolver;
import io.hyperfoil.tools.horreum.changedetection.ModelType;
import io.hyperfoil.tools.horreum.entity.FingerprintDAO;
import io.hyperfoil.tools.horreum.entity.PersistentLogDAO;
import io.hyperfoil.tools.horreum.entity.alerting.*;
import io.hyperfoil.tools.horreum.entity.changeDetection.ChangeDetectionLogDAO;
import io.hyperfoil.tools.horreum.entity.data.DatasetDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.hibernate.IntArrayType;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.hyperfoil.tools.horreum.mapper.*;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Parameters;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.Vertx;

@ApplicationScoped
@Startup
public class AlertingServiceImpl implements AlertingService {

    //@formatter:off
    private static final String LOOKUP_TIMESTAMP =
        """
        SELECT timeline_function,
           (CASE
              WHEN jsonb_array_length(timeline_labels) = 1 THEN jsonb_agg(lv.value)->0
              ELSE COALESCE(jsonb_object_agg(label.name, lv.value) FILTER (WHERE label.name IS NOT NULL), '{}'::jsonb)
           END) as value
        FROM test
        JOIN label ON json_contains(timeline_labels, label.name)
        JOIN label_values lv ON label.id = lv.label_id
        WHERE test.id = ?1 AND lv.dataset_id = ?2
           AND timeline_labels IS NOT NULL
           AND jsonb_typeof(timeline_labels) = 'array'
           AND jsonb_array_length(timeline_labels) > 0
        GROUP BY timeline_function, timeline_labels
        """;

    private static final String LOOKUP_VARIABLES =
        """
        SELECT
           var.id as variableId,
           var.name,
           var.\"group\",
           var.calculation,
           jsonb_array_length(var.labels) AS numLabels,
           (CASE
              WHEN jsonb_array_length(var.labels) = 1 THEN jsonb_agg(lv.value)->0
              ELSE COALESCE(jsonb_object_agg(label.name, lv.value) FILTER (WHERE label.name IS NOT NULL), '{}'::jsonb)
              END) AS value
        FROM variable var
        LEFT JOIN label ON json_contains(var.labels, label.name)
        LEFT JOIN label_values lv ON label.id = lv.label_id
        WHERE var.testid = ?1
           AND lv.dataset_id = ?2
        GROUP BY var.id, var.name, var.\"group\", var.calculation
        """;

    private static final String LOOKUP_RULE_LABEL_VALUES =
        """
        SELECT
        mdr.id AS rule_id,
        mdr.condition,
        (CASE
           WHEN mdr.labels IS NULL OR jsonb_array_length(mdr.labels) = 0 THEN NULL
           WHEN jsonb_array_length(mdr.labels) = 1 THEN jsonb_agg(lv.value)->0
           ELSE COALESCE(jsonb_object_agg(label.name, lv.value) FILTER (WHERE label.name IS NOT NULL), '{}'::jsonb)
        END) as value
        FROM missingdata_rule mdr
        LEFT JOIN label ON json_contains(mdr.labels, label.name)
        LEFT JOIN label_values lv ON label.id = lv.label_id AND lv.dataset_id = ?1
        WHERE mdr.test_id = ?2
        GROUP BY rule_id, mdr.condition
        """;

    private static final String LOOKUP_LABEL_VALUE_FOR_RULE =
        """
        SELECT
         (CASE
           WHEN mdr.labels IS NULL OR jsonb_array_length(mdr.labels) = 0 THEN NULL
           WHEN jsonb_array_length(mdr.labels) = 1 THEN jsonb_agg(lv.value)->0
           ELSE COALESCE(jsonb_object_agg(label.name, lv.value) FILTER (WHERE label.name IS NOT NULL), '{}'::jsonb)
         END) as value
        FROM missingdata_rule mdr
        LEFT JOIN label ON json_contains(mdr.labels, label.name)
        LEFT JOIN label_values lv ON label.id = lv.label_id AND lv.dataset_id = ?1
        WHERE mdr.id = ?2
        GROUP BY mdr.labels
        """;

    private static final String LOOKUP_RECENT =
        """
        SELECT
        DISTINCT ON(mdr.id) mdr.id,
        mdr.test_id,
        mdr.name,
        mdr.maxstaleness,
        rr.timestamp
        FROM missingdata_rule mdr
        LEFT JOIN missingdata_ruleresult rr ON mdr.id = rr.rule_id
        WHERE last_notification IS NULL
        OR EXTRACT(EPOCH FROM last_notification) * 1000 < EXTRACT(EPOCH FROM current_timestamp) * 1000 - mdr.maxstaleness
        ORDER BY mdr.id, timestamp DESC
        """;

    private static final String FIND_LAST_DATAPOINTS =
        """
        SELECT
        DISTINCT ON(variable_id) variable_id AS variable,
        EXTRACT(EPOCH FROM timestamp) * 1000 AS timestamp
        FROM datapoint dp
        LEFT JOIN fingerprint fp ON fp.dataset_id = dp.dataset_id
        WHERE
        ((fp.fingerprint IS NULL AND (?1)::jsonb IS NULL) OR json_equals(fp.fingerprint, (?1)::jsonb))
        AND variable_id = ANY(?2)
        ORDER BY variable_id, timestamp DESC
        """;

    private static final String DELETE_CHANGES_BY_TIMEFRAME =
        """
        DELETE FROM change cc
        WHERE NOT cc.confirmed
          AND cc.variable_id = :variableId
          AND (cc.timestamp > :validTimestamp OR (cc.timestamp = :validTimestamp AND :exclusive))
          AND EXISTS (
              SELECT 1 FROM fingerprint fp
              WHERE fp.dataset_id = cc.dataset_id
                AND fp.fp_hash = :fpHash
          );
        """;

    //@formatter:on
    private static final Instant LONG_TIME_AGO = Instant.ofEpochSecond(0);
    private static final Instant VERY_DISTANT_FUTURE = Instant.parse("2666-06-06T06:06:06.00Z");

    @Inject
    TestServiceImpl testService;

    @Inject
    EntityManager em;

    @Inject
    SecurityIdentity identity;

    @ConfigProperty(name = "horreum.alerting.updateLabel.retries", defaultValue = "5")
    Integer labelCalcRetries;

    @Inject
    TransactionManager tm;

    @Inject
    Vertx vertx;

    @Inject
    NotificationServiceImpl notificationService;

    @Inject
    TimeService timeService;

    @Inject
    ServiceMediator mediator;

    @Inject
    Session session;

    @Inject
    ChangeDetectionModelResolver modelResolver;

    // entries can be removed from timer thread while normally this is updated from one of blocking threads
    private final ConcurrentMap<Integer, Recalculation> recalcProgress = new ConcurrentHashMap<>();

    static {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    }

    public void onLabelValuesCalculation(Dataset.LabelsUpdatedEvent event) {
        boolean sendNotifications;
        DatasetDAO dataset = DatasetDAO.findById(event.datasetId);
        if (dataset == null) {
            // if this happens, means the dataset has been removed before processing the event
            // no need to retry as the same id will not appear anymore
            throw new RuntimeException("Cannot find  dataset with id " + event.datasetId);
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

        recalculateDatapointsForDataset(dataset, sendNotifications, false, new Recalculation(true, true));
        recalculateMissingDataRules(dataset);

        if (mediator.testMode())
            Util.registerTxSynchronization(tm, txStatus -> mediator.publishEvent(AsyncEventChannels.DATASET_UPDATED_LABELS,
                    event.testId, new Dataset.LabelsUpdatedEvent(event.testId, event.datasetId, event.isRecalculation)));
    }

    @Transactional
    void recalculateMissingDataRules(DatasetDAO dataset) {
        MissingDataRuleResultDAO.deleteForDataset(dataset.id);
        List<Object[]> ruleValues = session
                .createNativeQuery(LOOKUP_RULE_LABEL_VALUES, Object[].class)
                .setParameter(1, dataset.id).setParameter(2, dataset.testid)
                .addScalar("rule_id", StandardBasicTypes.INTEGER)
                .addScalar("condition", StandardBasicTypes.TEXT)
                .addScalar("value", JsonBinaryType.INSTANCE)
                .getResultList();
        Util.evaluateWithCombinationFunction(ruleValues, row -> (String) row[1], row -> (JsonNode) row[2],
                (row, result) -> {
                    int ruleId = (int) row[0];
                    if (result.isBoolean()) {
                        if (result.asBoolean()) {
                            createMissingDataRuleResult(dataset, ruleId);
                        }
                    } else {
                        logMissingDataMessage(dataset, PersistentLogDAO.ERROR,
                                "Result for missing data rule %d, dataset %d is not a boolean: %s", ruleId, dataset.id, result);
                    }
                },
                // Absence of condition means that this dataset is taken into account. This happens e.g. when value == NULL
                row -> createMissingDataRuleResult(dataset, (int) row[0]),
                (row, exception, code) -> logMissingDataMessage(dataset, PersistentLogDAO.ERROR,
                        "Exception evaluating missing data rule %d, dataset %d: '%s' Code: <pre>%s</pre>", row[0], dataset.id,
                        exception.getMessage(), code),
                output -> logMissingDataMessage(dataset, PersistentLogDAO.DEBUG,
                        "Output while evaluating missing data rules for dataset %d: '%s'", dataset.id, output));
    }

    private void createMissingDataRuleResult(DatasetDAO dataset, int ruleId) {
        new MissingDataRuleResultDAO(ruleId, dataset.id, dataset.start).persist();
    }

    @Transactional
    void recalculateDatapointsForDataset(DatasetDAO dataset, boolean notify, boolean debug,
            Recalculation recalculation) {
        Log.debugf("Analyzing dataset %d (%d/%d)", (long) dataset.id, (long) dataset.runId, dataset.ordinal);
        TestDAO test = TestDAO.findById(dataset.testid);
        if (test == null) {
            Log.errorf("Cannot load test ID %d", dataset.testid);
            return;
        }
        if (!testFingerprint(dataset, test.fingerprintFilter)) {
            return;
        }

        calculateDatapoints(dataset, notify, debug, recalculation);
    }

    private boolean testFingerprint(DatasetDAO dataset, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        Optional<JsonNode> result = session
                .createNativeQuery("SELECT fp.fingerprint FROM fingerprint fp WHERE dataset_id = ?1")
                .setParameter(1, dataset.id)
                .addScalar("fingerprint", JsonBinaryType.INSTANCE)
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
                    logCalculationMessage(dataset, PersistentLogDAO.ERROR,
                            "Evaluation of fingerprint failed: '%s' is not a boolean", value);
                    return false;
                },
                (code, e) -> logCalculationMessage(dataset, PersistentLogDAO.ERROR,
                        "Evaluation of fingerprint filter failed: '%s' Code:<pre>%s</pre>", e.getMessage(), code),
                output -> logCalculationMessage(dataset, PersistentLogDAO.DEBUG,
                        "Output while evaluating fingerprint filter: <pre>%s</pre>", output));
        if (!testResult) {
            logCalculationMessage(dataset, PersistentLogDAO.DEBUG, "Fingerprint %s was filtered out.", fingerprint);
        }
        return testResult;
    }

    void exportTest(TestExport test) {
        test.variables = VariableDAO.<VariableDAO> list("testId", test.id).stream().map(VariableMapper::from)
                .collect(Collectors.toList());
        test.missingDataRules = MissingDataRuleDAO.<MissingDataRuleDAO> list("test.id", test.id).stream()
                .map(MissingDataRuleMapper::from).collect(Collectors.toList());
    }

    void importVariables(TestExport test) {
        for (var v : test.variables) {
            VariableDAO variable = VariableMapper.to(v);
            variable.ensureLinked();
            if (variable.id == null || VariableDAO.findById(variable.id) == null) {
                // ensure ids are cleaned up
                variable.flushIds();
                variable.persist();
                test.updateExperimentsVariableId(v.name, variable.id);
            } else
                em.merge(variable);
        }
    }

    void importMissingDataRules(TestExport test) {
        for (var rule : test.missingDataRules) {
            if (rule.id != null && rule.id > 0 && MissingDataRuleDAO.findById(rule.id) != null) {
                em.merge(MissingDataRuleMapper.to(rule));
            } else {
                rule.id = null;
                em.persist(MissingDataRuleMapper.to(rule));
            }
        }
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

    @Transactional
    void calculateDatapoints(DatasetDAO dataset, boolean notify, boolean debug, Recalculation recalculation) {
        Set<VariableData> missingValueVariables = new HashSet<>();

        List<VariableData> values = fetchVariableValues(dataset);
        if (debug) {
            logFetchedValuesIfDebug(dataset, values);
        }
        Instant timestamp = resolveTimestamp(dataset);

        Util.evaluateWithCombinationFunction(values,
                data -> data.calculation,
                data -> data.value,
                (data, result) -> handleCalculatedValue(dataset, timestamp, data, result, notify, recalculation,
                        missingValueVariables),
                data -> handleNonCalculatedValue(dataset, timestamp, data, notify, recalculation, missingValueVariables),
                (data, exception, code) -> logCalculationMessage(dataset, PersistentLogDAO.ERROR,
                        "Evaluation of variable %s failed: '%s' Code:<pre>%s</pre>", data.fullName(), exception.getMessage(),
                        code),
                output -> logCalculationMessage(dataset, PersistentLogDAO.DEBUG,
                        "Output while calculating variable: <pre>%s</pre>", output));

        if (!missingValueVariables.isEmpty()) {
            var variableNames = missingValueVariables.stream().map(VariableData::fullName).collect(Collectors.toSet());
            MissingValuesEvent event = new MissingValuesEvent(dataset.getInfo(), variableNames, notify);
            if (mediator.testMode()) {
                mediator.publishEvent(AsyncEventChannels.DATASET_MISSING_VALUES, dataset.testid, event);
            }
            mediator.missingValuesDataset(event);
        }
        DataPoint.DatasetProcessedEvent event = new DataPoint.DatasetProcessedEvent(DatasetMapper.fromInfo(dataset.getInfo()),
                notify);
        if (mediator.testMode()) {
            Util.registerTxSynchronization(tm,
                    txStatus -> mediator.publishEvent(AsyncEventChannels.DATAPOINT_PROCESSED, dataset.testid, event));
        }
        mediator.dataPointsProcessed(event);

        // queue change detection for variables not missing
        values.stream().filter(v -> !missingValueVariables.contains(v))
                .map(v -> new ServiceMediator.ChangeDetectionEvent(dataset.testid, dataset.id, v.variableId, timestamp, notify))
                .forEach(mediator::queueChangeDetectionEvent);
    }

    private List<VariableData> fetchVariableValues(DatasetDAO dataset) {
        return session.createNativeQuery(LOOKUP_VARIABLES, Tuple.class)
                .setParameter(1, dataset.testid)
                .setParameter(2, dataset.id)
                .addScalar("variableId", StandardBasicTypes.INTEGER)
                .addScalar("name", StandardBasicTypes.TEXT)
                .addScalar("group", StandardBasicTypes.TEXT)
                .addScalar("calculation", StandardBasicTypes.TEXT)
                .addScalar("numLabels", StandardBasicTypes.INTEGER)
                .addScalar("value", JsonBinaryType.INSTANCE)
                .setTupleTransformer((tuples, aliases) -> {
                    VariableData data = new VariableData();
                    data.variableId = (int) tuples[0];
                    data.name = (String) tuples[1];
                    data.group = (String) tuples[2];
                    data.calculation = (String) tuples[3];
                    data.numLabels = (int) tuples[4];
                    data.value = (JsonNode) tuples[5];
                    return data;
                })
                .getResultList();
    }

    private void logFetchedValuesIfDebug(DatasetDAO dataset, List<VariableData> values) {
        for (VariableData data : values) {
            logCalculationMessage(dataset, PersistentLogDAO.DEBUG,
                    "Fetched value for variable %s: <pre>%s</pre>", data.fullName(), data.value);
        }
    }

    private Instant resolveTimestamp(DatasetDAO dataset) {
        List<Object[]> timestampList = session
                .createNativeQuery(LOOKUP_TIMESTAMP, Object[].class)
                .setParameter(1, dataset.testid)
                .setParameter(2, dataset.id)
                .addScalar("timeline_function", StandardBasicTypes.TEXT)
                .addScalar("value", JsonBinaryType.INSTANCE)
                .getResultList();

        if (timestampList.isEmpty()) {
            return dataset.start;
        }

        String timestampFunction = (String) timestampList.get(0)[0];
        JsonNode value = (JsonNode) timestampList.get(0)[1];

        if (timestampFunction != null && !timestampFunction.isBlank()) {
            value = Util.evaluateOnce(
                    timestampFunction,
                    value,
                    Util::convertToJson,
                    (code, throwable) -> logCalculationMessage(dataset, PersistentLogDAO.ERROR,
                            "Evaluation of timestamp failed: '%s' Code: <code><pre>%s</pre></code>", throwable.getMessage(),
                            code),
                    output -> logCalculationMessage(dataset, PersistentLogDAO.DEBUG,
                            "Output while calculating timestamp: <pre>%s</pre>", output));
        }

        Instant ts = Util.toInstant(value);
        if (ts == null) {
            logCalculationMessage(dataset, PersistentLogDAO.ERROR,
                    "Cannot parse timestamp, must be number or ISO-8601 timestamp: %s", value);
            ts = dataset.start;
        }
        return ts;
    }

    private void handleCalculatedValue(DatasetDAO dataset, Instant timestamp, VariableData data,
            Value result, boolean notify, Recalculation recalculation, Set<VariableData> missingValueVariables) {
        Double value = Util.toDoubleOrNull(result,
                error -> logCalculationMessage(dataset, PersistentLogDAO.ERROR,
                        "Evaluation of variable %s failed: %s", data.fullName(), error),
                info -> logCalculationMessage(dataset, PersistentLogDAO.INFO,
                        "Evaluation of variable %s: %s", data.fullName(), info));

        if (value != null) {
            createDataPoint(dataset, timestamp, data.variableId, value, notify, recalculation);
            return;
        }

        if (recalculation != null) {
            recalculation.datasetsWithoutValue.put(dataset.id, dataset.getInfo());
        }
        missingValueVariables.add(data);
    }

    private void handleNonCalculatedValue(DatasetDAO dataset, Instant timestamp, VariableData data, boolean notify,
            Recalculation recalculation, Set<VariableData> missingValueVariables) {
        if (data.numLabels > 1) {
            logCalculationMessage(dataset, PersistentLogDAO.WARN,
                    "Variable %s has more than one label (%s) but no calculation function.", data.fullName(),
                    data.value.fieldNames());
        }

        if (data.value == null || data.value.isNull()) {
            logCalculationMessage(dataset, PersistentLogDAO.INFO,
                    "Null value for variable %s - datapoint is not created", data.fullName());
            if (recalculation != null) {
                recalculation.datasetsWithoutValue.put(dataset.id, dataset.getInfo());
            }
            missingValueVariables.add(data);
            return;
        }

        Double value = null;
        if (data.value.isNumber()) {
            value = data.value.asDouble();
        } else if (data.value.isTextual()) {
            try {
                value = Double.parseDouble(data.value.asText());
            } catch (NumberFormatException ignored) {
                // keep value as null
            }
        }

        if (value == null) {
            logCalculationMessage(dataset, PersistentLogDAO.ERROR,
                    "Cannot turn %s into a floating-point value for variable %s", data.value, data.fullName());
            if (recalculation != null) {
                recalculation.errors++;
            }
            missingValueVariables.add(data);
            return;
        }

        createDataPoint(dataset, timestamp, data.variableId, value, notify, recalculation);
    }

    @Transactional
    void createDataPoint(DatasetDAO dataset, Instant timestamp, int variableId, double value, boolean notify,
            Recalculation recalculation) {
        DataPointDAO dataPoint;
        VariableDAO variableDAO = VariableDAO.findById(variableId);
        if (recalculation.clearDatapoints) {
            dataPoint = new DataPointDAO();
            dataPoint.variable = variableDAO;
            dataPoint.dataset = dataset;
            dataPoint.timestamp = timestamp;
            dataPoint.value = value;
            dataPoint.persistAndFlush();
        } else {
            dataPoint = DataPointDAO.find("dataset = :dataset and variable = :variable",
                    Parameters.with("dataset", dataset).and("variable", variableDAO)).firstResult();
        }
        if (dataPoint != null) {
            if (mediator.testMode()) {
                DataPoint.Event event = new DataPoint.Event(dataPoint.id, dataset.id, notify);
                Util.registerTxSynchronization(tm,
                        txStatus -> mediator.publishEvent(AsyncEventChannels.DATAPOINT_NEW, dataset.testid, event));
            }
        } else {
            Log.debugf("DataPoint for dataset %d, variable %d, timestamp %s, value %f not found", dataset.id, variableId,
                    timestamp, value);
        }
    }

    @Transactional
    private void logCalculationMessage(DatasetDAO dataSet, int level, String format, Object... args) {
        logCalculationMessage(dataSet.testid, dataSet.id, level, format, args);
    }

    @Transactional
    private void logCalculationMessage(int testId, int datasetId, int level, String format, Object... args) {
        String msg = args.length == 0 ? format : format.formatted(args);
        Log.tracef("Logging %s for test %d, dataset %d: %s", PersistentLogDAO.logLevel(level), testId, datasetId, msg);
        new DatasetLogDAO(em.getReference(TestDAO.class, testId), em.getReference(DatasetDAO.class, datasetId),
                level, "variables", msg).persist();
    }

    private void logMissingDataMessage(DatasetDAO dataSet, int level, String format, Object... args) {
        logMissingDataMessage(dataSet.testid, dataSet.id, level, format, args);
    }

    private void logMissingDataMessage(int testId, int datasetId, int level, String format, Object... args) {
        String msg = args.length == 0 ? format : format.formatted(args);
        Log.tracef("Logging %s for test %d, dataset %d: %s", PersistentLogDAO.logLevel(level), testId, datasetId, msg);
        new DatasetLogDAO(em.getReference(TestDAO.class, testId), em.getReference(DatasetDAO.class, datasetId),
                level, "missingdata", msg).persist();
    }

    private void logChangeDetectionMessage(int testId, int datasetId, int level, String format, Object... args) {
        String msg = args.length == 0 ? format : format.formatted(args);
        Log.tracef("Logging %s for test %d, dataset %d: %s", PersistentLogDAO.logLevel(level), testId, datasetId, msg);
        new DatasetLogDAO(em.getReference(TestDAO.class, testId), em.getReference(DatasetDAO.class, datasetId),
                level, "changes", msg).persist();
    }

    @Transactional
    void runChangeDetection(int testId, int datasetId, int variableId, Instant timestamp, boolean notify,
            boolean expectExists, boolean lastDatapoint) {

        Log.debugf("Performing change detection on test {%d} and variable {%d]", testId, variableId);

        var fingerprint = FingerprintDAO.<FingerprintDAO> findByIdOptional(datasetId);
        JsonNode fpNode = fingerprint.map(f -> f.fingerprint).orElse(null);
        Integer fpHash = fingerprint.map(f -> f.fpHash).orElse(null);

        deleteChangesByTimeframe(variableId, fpHash, timestamp);

        Instant changeTimestamp = fetchLastChangeTimestamp(variableId, fpHash, timestamp);
        List<DataPointDAO> dataPoints = fetchDataPoints(variableId, fpHash, changeTimestamp, timestamp);

        if (dataPoints.isEmpty()) {
            if (expectExists) {
                Log.warn("The published datapoint should be already in the list");
            }
        } else {
            processChangeDetection(variableId, testId, fpNode, notify, lastDatapoint, dataPoints);
        }
    }

    private void deleteChangesByTimeframe(int variableId, Integer fpHash, Instant timestamp) {
        int numDeleted = session.createNativeQuery(DELETE_CHANGES_BY_TIMEFRAME, int.class)
                .setParameter("variableId", variableId)
                .setParameter("validTimestamp", timestamp, StandardBasicTypes.INSTANT)
                .setParameter("exclusive", false)
                .setParameter("fpHash", fpHash)
                .executeUpdate();
        Log.debugf("Deleted %d changes > %s for variable %d, fingerprint %s", numDeleted, timestamp, variableId, fpHash);
    }

    private Instant fetchLastChangeTimestamp(int variableId, Integer fpHash, Instant timestamp) {
        return session.createQuery("""
                SELECT c.timestamp FROM Change c LEFT JOIN Fingerprint fp ON c.dataset.id = fp.datasetId
                WHERE c.variable.id = :variableId AND fp.fpHash = :fpHash
                AND (c.timestamp < :validTimestamp OR (c.timestamp = :validTimestamp AND :exclusive = TRUE))
                ORDER by c.timestamp DESC
                """, Instant.class)
                .setParameter("variableId", variableId)
                .setParameter("validTimestamp", timestamp != null ? timestamp : VERY_DISTANT_FUTURE)
                .setParameter("exclusive", false)
                .setParameter("fpHash", fpHash)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElse(LONG_TIME_AGO);
    }

    private List<DataPointDAO> fetchDataPoints(int variableId, Integer fpHash, Instant changeTimestamp, Instant nextTimestamp) {
        return session.createQuery(
                "SELECT dp FROM DataPoint dp LEFT JOIN Fingerprint fp ON dp.dataset.id = fp.datasetId " +
                        "JOIN dp.dataset " +
                        "WHERE dp.variable.id = :variableId AND dp.timestamp BETWEEN :changeTimestamp AND :nextTimestamp " +
                        "AND fp.fpHash = :fpHash " +
                        "ORDER BY dp.timestamp DESC, dp.dataset.id DESC",
                DataPointDAO.class)
                .setParameter("variableId", variableId)
                .setParameter("changeTimestamp", changeTimestamp)
                .setParameter("nextTimestamp", nextTimestamp)
                .setParameter("fpHash", fpHash)
                .getResultList();
    }

    private void processChangeDetection(int variableId, int testId, JsonNode fingerprint, boolean notify,
            boolean lastDatapoint, List<DataPointDAO> dataPoints) {
        int datasetId = dataPoints.get(0).getDatasetId();
        for (ChangeDetectionDAO detection : ChangeDetectionDAO.<ChangeDetectionDAO> find("variable.id", variableId).list()) {
            ChangeDetectionModel model = modelResolver.getModel(ChangeDetectionModelType.fromString(detection.model));
            if (model == null) {
                logChangeDetectionMessage(variableId, datasetId, PersistentLogDAO.ERROR,
                        "Cannot find change detection model %s", detection.model);
                continue;
            }
            if (model.getType() == ModelType.CONTINOUS || (model.getType() == ModelType.BULK && lastDatapoint)) {
                try {
                    model.analyze(dataPoints, detection.config, change -> {
                        logChangeDetectionMessage(testId, datasetId, PersistentLogDAO.DEBUG,
                                "Change %s detected using datapoints %s", change, reversedAndLimited(dataPoints));
                        em.persist(change);
                        String testName = TestDAO.<TestDAO> findByIdOptional(testId).map(test -> test.name).orElse("<unknown>");
                        Change.Event event = new Change.Event(ChangeMapper.from(change), testId, testName, notify);
                        if (mediator.testMode()) {
                            Util.registerTxSynchronization(tm, txStatus -> mediator
                                    .publishEvent(AsyncEventChannels.CHANGE_NEW, change.dataset.testid, event));
                        }
                        mediator.newChange(event);
                    });
                } catch (ChangeDetectionException e) {
                    new ChangeDetectionLogDAO(variableId, fingerprint, PersistentLogDAO.ERROR, e.getLocalizedMessage())
                            .persist();
                    Log.error("An error occurred while running change detection!", e);
                }
            }
        }
    }

    private String reversedAndLimited(List<DataPointDAO> list) {
        int maxIndex = Math.min(list.size() - 1, 20);
        StringBuilder sb = new StringBuilder("[");
        if (maxIndex < list.size() - 1) {
            sb.append("..., ");
        }
        for (int i = maxIndex; i >= 0; --i) {
            sb.append(list.get(i));
            if (i != 0)
                sb.append(", ");
        }
        return sb.append("]").toString();
    }

    @Override
    @WithRoles
    @PermitAll
    public List<Variable> variables(Integer testId) {
        List<VariableDAO> variables;
        if (testId != null) {
            variables = VariableDAO.list("testId", testId);
        } else {
            variables = VariableDAO.listAll();
        }
        return variables.stream().map(VariableMapper::from).collect(Collectors.toList());
    }

    @Override
    @WithRoles
    @RolesAllowed("tester")
    @Transactional
    public void updateVariables(int testId, List<Variable> variablesDTO) {
        for (Variable v : variablesDTO) {
            if (v.name == null || v.name.isBlank()) {
                throw ServiceException.badRequest("Variable name is mandatory!");
            }
        }
        try {
            List<VariableDAO> variables = variablesDTO.stream().map(VariableMapper::to).collect(Collectors.toList());
            List<VariableDAO> currentVariables = VariableDAO.list("testId", testId);
            updateCollection(currentVariables, variables, v -> v.id, item -> {
                if (item.id != null && item.id < 0) {
                    item.id = null;
                }
                if (item.changeDetection != null) {
                    ensureDefaults(item.changeDetection);
                    item.changeDetection.forEach(rd -> rd.variable = item);
                    item.changeDetection.stream().filter(rd -> rd.id != null && rd.id == -1).forEach(rd -> rd.id = null);
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
                    if (item.id != null && item.id < 0) {
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
                DataPointDAO.delete("variable.id", current.id);
                ChangeDAO.delete("variable.id", current.id);
                current.delete();
            });

            em.flush();
        } catch (PersistenceException e) {
            Log.error("Failed to update variables", e);
            throw new WebApplicationException(e, Response.serverError().build());
        }
        Log.debug("Variables updated, everything is fine, returning");
    }

    private void ensureDefaults(Set<ChangeDetectionDAO> rds) {
        rds.forEach(rd -> {
            ChangeDetectionModel model = modelResolver.getModel(ChangeDetectionModelType.fromString(rd.model));
            if (model == null) {
                throw ServiceException.badRequest("Unknown model " + rd.model);
            }
            if (rd.config == null || rd.config.isNull() || rd.config.isMissingNode()) {
                rd.config = JsonNodeFactory.instance.objectNode();
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

    private <T> void updateCollection(Collection<T> currentList, Collection<T> newList, Function<T, Object> idSelector,
            Consumer<T> create, BiConsumer<T, T> update, Consumer<T> delete) {
        for (Iterator<T> iterator = currentList.iterator(); iterator.hasNext();) {
            T current = iterator.next();
            T matching = newList.stream().filter(v -> idSelector.apply(current).equals(idSelector.apply(v))).findFirst()
                    .orElse(null);
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

    private DashboardInfo createChangesDashboard(int testId, String fingerprint, List<VariableDAO> variables) {
        DashboardInfo info = new DashboardInfo();
        info.testId = testId;
        Dashboard dashboard = new Dashboard();
        dashboard.title = TestDAO.<TestDAO> findByIdOptional(testId).map(t -> t.name).orElse("Test " + testId)
                + (fingerprint.isEmpty() ? "" : ", " + fingerprint);
        dashboard.tags.add(testId + ";" + fingerprint);
        dashboard.tags.add("testId=" + testId);
        int i = 0;
        Map<String, List<VariableDAO>> byGroup = groupedVariables(variables);
        for (VariableDAO variable : variables) {
            dashboard.annotations.list.add(new Dashboard.Annotation(variable.name, variable.id + ";" + fingerprint));
        }
        for (Map.Entry<String, List<VariableDAO>> entry : byGroup.entrySet()) {
            entry.getValue().sort(Comparator.comparing(v -> v.name));
            Dashboard.Panel panel = new Dashboard.Panel(entry.getKey(),
                    new Dashboard.GridPos(12 * (i % 2), 9 * (i / 2), 12, 9));
            info.panels.add(new PanelInfo(entry.getKey(),
                    entry.getValue().stream().map(VariableMapper::from).collect(Collectors.toList())));
            for (VariableDAO variable : entry.getValue()) {
                panel.targets.add(new Target(variable.id + ";" + fingerprint, "timeseries", "T" + i));
            }
            dashboard.panels.add(panel);
            ++i;
        }
        return info;
    }

    private Map<String, List<VariableDAO>> groupedVariables(List<VariableDAO> variables) {
        Map<String, List<VariableDAO>> byGroup = new TreeMap<>();
        for (VariableDAO variable : variables) {
            byGroup.computeIfAbsent(variable.group == null || variable.group.isEmpty() ? variable.name : variable.group,
                    g -> new ArrayList<>()).add(variable);
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
        List<VariableDAO> variables = VariableDAO.list("testId", testId);
        return createChangesDashboard(testId, fingerprint, variables);
    }

    @Override
    @WithRoles
    @PermitAll
    public List<Change> changes(int varId, String fingerprint) {
        VariableDAO v = VariableDAO.findById(varId);
        if (v == null) {
            throw ServiceException.notFound("Variable " + varId + " not found");
        }
        JsonNode fp = Util.parseFingerprint(fingerprint);
        if (fp == null) {
            List<ChangeDAO> changes = ChangeDAO.list("variable", v);
            return changes.stream().map(ChangeMapper::from).collect(Collectors.toList());
        }
        List<ChangeDAO> changes = session.createNativeQuery("""
                SELECT change.*
                FROM change
                JOIN fingerprint fp ON change.dataset_id = fp.dataset_id
                WHERE variable_id = ?1
                   AND json_equals(fp.fingerprint, ?2)
                """, ChangeDAO.class)
                .setParameter(1, varId)
                .setParameter(2, fp, JsonBinaryType.INSTANCE)
                .getResultList();
        return changes.stream().map(ChangeMapper::from).collect(Collectors.toList());
    }

    @Override
    @WithRoles
    @RolesAllowed(Roles.TESTER)
    @Transactional
    public void updateChange(int changeId, Change changeDTO) {
        if (changeId != changeDTO.id) {
            throw ServiceException.badRequest("Path ID and entity ID don't match");
        }
        ChangeDAO change = ChangeDAO.<ChangeDAO> findByIdOptional(changeId)
                .orElseThrow(() -> ServiceException.notFound("Change with id " + changeDTO.id + " not found"));
        change.confirmed = changeDTO.confirmed;
    }

    @Override
    @WithRoles
    @RolesAllowed(Roles.TESTER)
    @Transactional
    public void deleteChange(int id) {
        if (!ChangeDAO.deleteById(id)) {
            throw ServiceException.notFound("Change not found");
        }
    }

    @Override
    @RolesAllowed(Roles.TESTER)
    @WithRoles
    public void recalculateDatapoints(int testId, boolean notify,
            boolean debug, Boolean clearDatapoints, Long from, Long to) {
        TestDAO test = TestDAO.findById(testId);
        if (test == null) {
            throw ServiceException.notFound("Test " + testId + " does not exist or is not available.");
        } else if (!Roles.hasRoleWithSuffix(identity, test.owner, "-tester")) {
            throw ServiceException.forbidden("This user cannot trigger the recalculation");
        }

        startRecalculation(testId, notify, debug, clearDatapoints == null || clearDatapoints, from, to);
    }

    // It doesn't make sense to limit access to particular user when doing the recalculation,
    // normally the calculation happens with system privileges anyway.
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    void startRecalculation(int testId, boolean notify, boolean debug, boolean clearDatapoints, Long from, Long to) {
        Recalculation recalculation = new Recalculation();
        Recalculation previous = recalcProgress.putIfAbsent(testId, recalculation);
        while (previous != null) {
            if (!previous.done) {
                Log.debugf("Already started recalculation on test %d, ignoring.", testId);
                return;
            }
            if (recalcProgress.replace(testId, previous, recalculation)) {
                break;
            }
            previous = recalcProgress.putIfAbsent(testId, recalculation);
        }
        recalculation.clearDatapoints = clearDatapoints;

        try {
            //update fingerprints before starting recalculation
            //TODO: check if we need to update fingerprints for all datasets
            mediator.updateFingerprints(testId);
            Log.infof("About to recalculate datapoints in test %d between %s and %s", testId, from, to);
            //TODO:: determine if we should clear datapoints
            recalculation.datasets = getDatasetsForRecalculation(testId, from, to, clearDatapoints);
            int numRuns = recalculation.datasets.size();

            Log.infof("Starting recalculation of test %d, %d runs", testId, numRuns);
            int completed = 0;
            recalcProgress.put(testId, recalculation);
            //TODO:: this could be more streamlined
            Map<String, Integer> lastDatapoints = new HashMap<>();
            // recalculation.datasets contains an ordered list of <dataset, fingerprint>
            // grouping by fingerprint will guarantee that we have the last datapoint for each fingerprint
            recalculation.datasets.forEach((key, value) -> lastDatapoints.put(value, key));
            Set<Integer> lastDatapointSet = new HashSet<>(lastDatapoints.values());
            for (int datasetId : recalculation.datasets.keySet()) {
                // Since the evaluation might take few moments and we're dealing potentially with thousands
                // of runs we'll process each run in a separate transaction
                recalculation.lastDatapoint = lastDatapointSet.contains(datasetId);
                recalculateForDataset(datasetId, notify, debug, recalculation);
                recalculation.progress = 100 * ++completed / numRuns;
            }

        } catch (Throwable t) {
            Log.error("Recalculation failed", t);
            throw t;
        } finally {
            recalculation.done = true;
            vertx.setTimer(30_000, timerId -> recalcProgress.remove(testId, recalculation));
        }
    }

    @Transactional
    Map<Integer, String> getDatasetsForRecalculation(Integer testId, Long from, Long to, boolean clearDatapoints) {
        Query query = session
                .createNativeQuery(
                        "SELECT id, fingerprint FROM dataset LEFT JOIN fingerprint ON dataset.id = fingerprint.dataset_id WHERE testid = ?1 AND (EXTRACT(EPOCH FROM start) * 1000 BETWEEN ?2 AND ?3) ORDER BY start",
                        Tuple.class)
                .setParameter(1, testId)
                .setParameter(2, from == null ? Long.MIN_VALUE : from)
                .setParameter(3, to == null ? Long.MAX_VALUE : to);
        //use LinkedHashMap to preserve ordering of ID insertion - query is ordered on start
        Map<Integer, String> ids = new LinkedHashMap<>();
        query.getResultList().forEach(tuple -> ids.put(
                (Integer) ((Tuple) tuple).get("id"),
                ((Tuple) tuple).get("fingerprint") == null ? "" : ((String) ((Tuple) tuple).get("fingerprint"))));
        List<Integer> datasetIDs = new ArrayList<>(ids.keySet());
        if (clearDatapoints) {
            DataPointDAO.delete("dataset.id in ?1", datasetIDs);
        }
        ChangeDAO.delete("dataset.id in ?1 AND confirmed = false", datasetIDs);
        if (!ids.isEmpty()) {
            // Due to RLS policies we cannot add a record to a dataset we don't own
            logCalculationMessage(testId, datasetIDs.get(0), PersistentLogDAO.INFO, "Starting recalculation of %d runs.",
                    ids.size());
        }
        return ids;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void recalculateForDataset(Integer datasetId, boolean notify, boolean debug, Recalculation recalculation) {
        DatasetDAO dataset = DatasetDAO.findById(datasetId);
        if (dataset != null) {
            recalculateDatapointsForDataset(dataset, notify, debug, recalculation);
        } else {
            Log.debugf("Could not find dataset with id: %d", datasetId);
        }
    }

    @Override
    @RolesAllowed(Roles.TESTER)
    public DatapointRecalculationStatus getDatapointRecalculationStatus(int testId) {
        Recalculation recalculation = recalcProgress.get(testId);
        DatapointRecalculationStatus status = new DatapointRecalculationStatus();
        status.percentage = recalculation == null ? 100 : recalculation.progress;
        status.done = recalculation == null || recalculation.done;
        if (recalculation != null) {
            status.totalDatasets = recalculation.datasets.size();
            status.errors = recalculation.errors;
            status.datasetsWithoutValue = recalculation.datasetsWithoutValue.values().stream().map(DatasetMapper::fromInfo)
                    .collect(Collectors.toList());
        }
        return status;
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    @Scheduled(every = "{horreum.alerting.missing.dataset.check}")
    public void checkMissingDataset() {
        List<Object[]> results = session.createNativeQuery(LOOKUP_RECENT, Object[].class).getResultList();
        for (Object[] row : results) {
            int ruleId = (int) row[0];
            int testId = (int) row[1];
            String ruleName = (String) row[2];
            long maxStaleness = (long) row[3];
            Instant timestamp = (Instant) row[4];
            if (timestamp == null || timestamp.isBefore(timeService.now().minusMillis(maxStaleness))) {
                if (ruleName == null) {
                    ruleName = "rule #" + ruleId;
                }
                notificationService.notifyMissingDataset(testId, ruleName, maxStaleness, timestamp);
                int numUpdated = em.createNativeQuery("UPDATE missingdata_rule SET last_notification = ?1 WHERE id = ?2")
                        .setParameter(1, timeService.now()).setParameter(2, ruleId).executeUpdate();
                if (numUpdated != 1) {
                    Log.errorf("Missing data rules update for rule %d (test %d) didn't work: updated: %d", ruleId, testId,
                            numUpdated);
                }
            }
        }
    }

    @Override
    @WithRoles
    @PermitAll
    public List<DatapointLastTimestamp> findLastDatapoints(LastDatapointsParams params) {
        //noinspection unchecked
        return em.createNativeQuery(FIND_LAST_DATAPOINTS)
                .unwrap(NativeQuery.class)
                .setParameter(1, Util.parseFingerprint(params.fingerprint), JsonBinaryType.INSTANCE)
                .setParameter(2, params.variables, IntArrayType.INSTANCE)
                .setTupleTransformer((tuples, aliases) -> {
                    return new DatapointLastTimestamp((int) tuples[0], (Number) tuples[1]);
                }).getResultList();
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
        TestDAO test = testService.ensureTestExists(testNameOrId);
        RunExpectationDAO runExpectation = new RunExpectationDAO();
        runExpectation.testId = test.id;
        runExpectation.expectedBefore = timeService.now().plusSeconds(timeoutSeconds);
        runExpectation.expectedBy = expectedBy != null ? expectedBy : identity.getPrincipal().getName();
        runExpectation.backlink = backlink;
        runExpectation.persist();
    }

    @PermitAll
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Override
    public List<RunExpectation> expectations() {
        List<RunExpectationDAO> expectations = RunExpectationDAO.listAll();
        return expectations.stream().map(RunExpectationMapper::from).collect(Collectors.toList());
    }

    @WithRoles
    @Transactional
    @Override
    public void updateChangeDetection(int testId, AlertingService.ChangeDetectionUpdate update) {
        TestDAO test = testService.getTestForUpdate(testId);
        test.timelineLabels = toJsonArray(update.timelineLabels);
        test.timelineFunction = "";
        test.timelineFunction = update.timelineFunction;
        test.fingerprintLabels = toJsonArray(update.fingerprintLabels);
        // In case the filter is null we need to force the property to be dirty
        test.fingerprintFilter = "";
        test.fingerprintFilter = update.fingerprintFilter;
        test.persistAndFlush();
    }

    private ArrayNode toJsonArray(List<String> labels) {
        if (labels == null) {
            return null;
        }
        return labels.stream().reduce(JsonNodeFactory.instance.arrayNode(), ArrayNode::add, ArrayNode::addAll);
    }

    @PermitAll
    @Override
    public List<ConditionConfig> changeDetectionModels() {
        return modelResolver.getModels().values().stream().map(ChangeDetectionModel::config).collect(Collectors.toList());
    }

    @PermitAll
    @Override
    public List<ChangeDetection> defaultChangeDetectionConfigs() {
        ChangeDetectionDAO lastDatapoint = new ChangeDetectionDAO();
        lastDatapoint.model = ChangeDetectionModelType.names.RELATIVE_DIFFERENCE;
        lastDatapoint.config = JsonNodeFactory.instance.objectNode()
                .put("window", 1).put("model", ChangeDetectionModelType.names.RELATIVE_DIFFERENCE).put("filter", "mean")
                .put("threshold", 0.2).put("minPrevious", 5);
        ChangeDetectionDAO floatingWindow = new ChangeDetectionDAO();
        floatingWindow.model = ChangeDetectionModelType.names.RELATIVE_DIFFERENCE;
        floatingWindow.config = JsonNodeFactory.instance.objectNode()
                .put("window", 5).put("model", ChangeDetectionModelType.names.RELATIVE_DIFFERENCE).put("filter", "mean")
                .put("threshold", 0.1).put("minPrevious", 5);
        return Arrays.asList(lastDatapoint, floatingWindow).stream().map(ChangeDetectionMapper::from)
                .collect(Collectors.toList());
    }

    @WithRoles
    @Override
    public List<MissingDataRule> missingDataRules(int testId) {
        if (testId <= 0) {
            throw ServiceException.badRequest("Invalid test ID: " + testId);
        }
        List<MissingDataRuleDAO> rules = MissingDataRuleDAO.list("test.id", testId);
        return rules.stream().map(MissingDataRuleMapper::from).collect(Collectors.toList());
    }

    @WithRoles
    @Transactional
    @Override
    public int updateMissingDataRule(int testId, MissingDataRule dto) {
        MissingDataRuleDAO rule = MissingDataRuleMapper.to(dto);
        // check test existence and ownership
        testService.getTestForUpdate(testId);
        if (rule.id != null && rule.id <= 0) {
            rule.id = null;
        }
        // drop any info about last notification
        rule.lastNotification = null;
        if (rule.maxStaleness <= 0) {
            throw ServiceException.badRequest("Invalid max staleness in rule " + rule.name + ": " + rule.maxStaleness);
        }

        if (rule.id == null) {
            rule.test = em.getReference(TestDAO.class, testId);
            rule.persistAndFlush();
        } else {
            MissingDataRuleDAO existing = MissingDataRuleDAO.findById(rule.id);
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
        recalculateMissingDataRules(testId, rule);
        return rule.id;
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    void recalculateMissingDataRules(int testId, MissingDataRuleDAO rule) {
        List<Object[]> idsAndTimestamps = session
                .createNativeQuery("SELECT id, start FROM dataset WHERE testid = ?1", Object[].class)
                .setParameter(1, testId).getResultList();
        for (Object[] row : idsAndTimestamps) {
            recalculateMissingDataRule((int) row[0], (Instant) row[1], rule);
        }
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    void recalculateMissingDataRule(int datasetId, Instant timestamp, MissingDataRuleDAO rule) {
        JsonNode value = (JsonNode) em.createNativeQuery(LOOKUP_LABEL_VALUE_FOR_RULE)
                .setParameter(1, datasetId).setParameter(2, rule.id)
                .unwrap(NativeQuery.class)
                .addScalar("value", JsonBinaryType.INSTANCE)
                .getSingleResult();
        boolean match = true;
        if (rule.condition != null && !rule.condition.isBlank()) {
            String ruleName = rule.name == null ? "#" + rule.id : rule.name;
            match = Util.evaluateTest(rule.condition, value, notBoolean -> {
                logMissingDataMessage(rule.testId(), datasetId, PersistentLogDAO.ERROR,
                        "Missing data rule %s result is not a boolean: %s", ruleName, notBoolean);
                return true;
            },
                    (code, exception) -> logMissingDataMessage(rule.testId(), datasetId, PersistentLogDAO.ERROR,
                            "Error evaluating missing data rule %s: '%s' Code:<pre>%s</pre>", ruleName, exception.getMessage(),
                            code),
                    output -> logMissingDataMessage(rule.testId(), datasetId, PersistentLogDAO.DEBUG,
                            "Output while evaluating missing data rule %s: '%s'", ruleName, output));
        }
        if (match) {
            new MissingDataRuleResultDAO(rule.id, datasetId, timestamp).persist();
        }
    }

    @WithRoles
    @Transactional
    @Override
    public void deleteMissingDataRule(int id) {
        MissingDataRuleResultDAO.deleteForDataRule(id);
        MissingDataRuleDAO.deleteById(id);
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    public void removeExpected(Run run) {
        // delete at most one expectation
        Query query = em.createNativeQuery("""
                DELETE FROM run_expectation
                WHERE id = (
                   SELECT id
                   FROM run_expectation
                   WHERE testid = (
                      SELECT testid
                      FROM run
                      WHERE id = ?1
                   )
                LIMIT 1)
                """);
        query.setParameter(1, run.id);
        int updated = query.executeUpdate();
        if (updated > 0) {
            Log.debugf("Removed %d run expectations as run %d was added", updated, run.id);
        }
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    void onDatasetDeleted(int datasetId) {
        Log.debugf("Removing changes for dataset %d", datasetId);
        ChangeDAO.delete("dataset.id = ?1 AND confirmed = false", datasetId);
        DataPointDAO.delete("dataset.id", datasetId);
        //Need to make sure we delete MissingDataRuleResults when datasets are removed
        MissingDataRuleResultDAO.deleteForDataset(datasetId);
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    void onTestDeleted(int testId) {
        // We need to delete in a loop to cascade this to ChangeDetection
        List<VariableDAO> variables = VariableDAO.list("testId", testId);
        Log.debugf("Deleting %d variables for test (%d)", variables.size(), testId);
        for (var variable : variables) {
            variable.delete();
        }
        MissingDataRuleDAO.delete("test.id", testId);
        em.flush();
    }

    @Transactional
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Scheduled(every = "{horreum.alerting.expected.run.check}")
    public void checkExpectedRuns() {
        for (RunExpectationDAO expectation : RunExpectationDAO
                .<RunExpectationDAO> find("expectedBefore < ?1", timeService.now()).list()) {
            boolean sendNotifications = (Boolean) em.createNativeQuery("SELECT notificationsenabled FROM test WHERE id = ?")
                    .setParameter(1, expectation.testId).getSingleResult();
            if (sendNotifications) {
                // We will perform this only if this transaction succeeds, to allow no-op retries
                Util.doAfterCommit(tm, () -> notificationService.notifyExpectedRun(expectation.testId,
                        expectation.expectedBefore.toEpochMilli(), expectation.expectedBy, expectation.backlink));
            } else {
                Log.debugf("Skipping expected run notification on test %d since it is disabled", expectation.testId);
            }
            expectation.delete();
        }
    }

    // Note: this class must be public - otherwise when this is used as a parameter to
    // a method in AlertingServiceImpl the interceptors would not be invoked.
    public static class Recalculation {
        Map<Integer, String> datasets = Collections.emptyMap();
        int progress;
        boolean done;
        public int errors;

        boolean lastDatapoint;
        boolean clearDatapoints;

        Map<Integer, DatasetDAO.Info> datasetsWithoutValue = new HashMap<>();

        public Recalculation() {
        }

        public Recalculation(boolean lastDatapoint, boolean clearDatapoints) {
            this.lastDatapoint = lastDatapoint;
            this.clearDatapoints = clearDatapoints;
        }
    }
}
