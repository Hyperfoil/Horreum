package io.hyperfoil.tools.horreum.svc;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Tuple;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DefaultValue;

import org.hibernate.Hibernate;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.*;
import io.hyperfoil.tools.horreum.api.services.DatasetService;
import io.hyperfoil.tools.horreum.api.services.SchemaService;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.entity.FingerprintDAO;
import io.hyperfoil.tools.horreum.entity.PersistentLogDAO;
import io.hyperfoil.tools.horreum.entity.alerting.DatasetLogDAO;
import io.hyperfoil.tools.horreum.entity.data.*;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.hyperfoil.tools.horreum.mapper.DatasetMapper;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.runtime.Startup;
import io.quarkus.security.identity.SecurityIdentity;

@ApplicationScoped
@Startup
public class DatasetServiceImpl implements DatasetService {
    private static final Logger log = Logger.getLogger(DatasetServiceImpl.class);

    //@formatter:off
    private static final String LABEL_QUERY = """
         WITH
         used_labels AS (
            SELECT label.id AS label_id, label.name, ds.schema_id, count(le) AS count
            FROM dataset_schemas ds
            JOIN label ON label.schema_id = ds.schema_id
            LEFT JOIN label_extractors le ON le.label_id = label.id
            WHERE ds.dataset_id = ?1 AND (?2 < 0 OR label.id = ?2) GROUP BY label.id, label.name, ds.schema_id
         ),
         lvalues AS (
            SELECT ul.label_id, le.name,
                  (CASE WHEN le.isarray THEN
                     jsonb_path_query_array(dataset.data -> ds.index, le.jsonpath::jsonpath)
                 ELSE
                     jsonb_path_query_first(dataset.data -> ds.index, le.jsonpath::jsonpath)
                  END) AS value
            FROM dataset
            JOIN dataset_schemas ds ON dataset.id = ds.dataset_id
            JOIN used_labels ul ON ul.schema_id = ds.schema_id
            LEFT JOIN label_extractors le ON ul.label_id = le.label_id
            WHERE dataset.id = ?1
         )
         SELECT lvalues.label_id, ul.name, function,
               (CASE
                  WHEN ul.count > 1 THEN jsonb_object_agg(COALESCE(lvalues.name, ''), lvalues.value)
                  WHEN ul.count = 1 THEN jsonb_agg(lvalues.value) -> 0
                  ELSE '{}'::jsonb END
               ) AS value
         FROM label
         JOIN lvalues ON lvalues.label_id = label.id
         JOIN used_labels ul ON label.id = ul.label_id
         GROUP BY lvalues.label_id, ul.name, function, ul.count
         """;
    protected static final String LABEL_PREVIEW = """
         WITH
         le AS (
            SELECT * FROM jsonb_populate_recordset(NULL::extractor, (?1)::jsonb)
         ),
         lvalues AS (
            SELECT le.name,
               (CASE WHEN le.isarray THEN
                  jsonb_path_query_array(dataset.data -> ds.index, le.jsonpath)
               ELSE
                  jsonb_path_query_first(dataset.data -> ds.index, le.jsonpath)
               END) AS value
            FROM le, dataset
            JOIN dataset_schemas ds ON dataset.id = ds.dataset_id
            WHERE dataset.id = ?2 AND ds.schema_id = ?3
         )
         SELECT (CASE
               WHEN jsonb_array_length((?1)::jsonb) > 1 THEN jsonb_object_agg(COALESCE(lvalues.name, ''), lvalues.value)
               WHEN jsonb_array_length((?1)::jsonb) = 1 THEN jsonb_agg(lvalues.value) -> 0
               ELSE '{}'::jsonb END
            ) AS value
         FROM lvalues
         """;

    private static final String SCHEMAS_SELECT = """
         SELECT dataset_id,
               jsonb_agg(
                  jsonb_build_object('id', schema.id, 'uri', ds.uri, 'name', schema.name, 'source', 0, 'type', 2, 'key', ds.index::text, 'hasJsonSchema', schema.schema IS NOT NULL)
               ) AS schemas
         FROM dataset_schemas ds
         JOIN dataset ON dataset.id = ds.dataset_id
         JOIN schema ON schema.id = ds.schema_id
         """;
    private static final String VALIDATION_SELECT = """
          validation AS (
            SELECT dataset_id, jsonb_agg(jsonb_build_object('schemaId', schema_id, 'error', error)) AS errors
            FROM dataset_validationerrors GROUP BY dataset_id
          )
         """;
    private static final String DATASET_SUMMARY_SELECT = """
         SELECT ds.id, ds.runid AS runId,
            ds.ordinal, ds.testid AS testId,
            test.name AS testname, ds.description,
            EXTRACT(EPOCH FROM ds.start) * 1000 AS start,
            EXTRACT(EPOCH FROM ds.stop) * 1000 AS stop,
            ds.owner, ds.access, dv.value AS view,
            COALESCE(schema_agg.schemas, '[]') AS schemas,
            COALESCE(validation.errors, '[]') AS validationErrors
         FROM dataset ds
         LEFT JOIN test ON test.id = ds.testid
         LEFT JOIN schema_agg ON schema_agg.dataset_id = ds.id
         LEFT JOIN validation ON validation.dataset_id = ds.id
         LEFT JOIN dataset_view dv ON dv.dataset_id = ds.id AND dv.view_id =
         """;
    private static final String LIST_SCHEMA_DATASETS = """
         WITH ids AS (
            SELECT dataset_id AS id FROM dataset_schemas WHERE uri = ?1
         ),
         schema_agg AS (
         """ +
            SCHEMAS_SELECT +
         """
            WHERE dataset_id IN (SELECT id FROM ids) GROUP BY dataset_id
         )
         SELECT ds.id, ds.runid AS runId, ds.ordinal,
            ds.testid AS testId, test.name AS testname, ds.description,
            EXTRACT(EPOCH FROM ds.start) * 1000 AS start,
            EXTRACT(EPOCH FROM ds.stop) * 1000 AS stop,
            ds.owner, ds.access, dv.value AS view,
            schema_agg.schemas AS schemas, '[]'::jsonb AS validationErrors
         FROM dataset ds
         LEFT JOIN test ON test.id = ds.testid
         LEFT JOIN schema_agg ON schema_agg.dataset_id = ds.id
         LEFT JOIN dataset_view dv ON dv.dataset_id = ds.id
         WHERE ds.id IN (SELECT id FROM ids)
         """;
    private static final String ALL_LABELS_SELECT = """
         SELECT dataset.id as dataset_id,
            COALESCE(jsonb_object_agg(label.name, lv.value) FILTER (WHERE label.name IS NOT NULL), '{}'::jsonb) AS values
         FROM dataset
         LEFT JOIN label_values lv ON dataset.id = lv.dataset_id
         LEFT JOIN label ON label.id = label_id
         """;

    //@formatter:on
    @Inject
    EntityManager em;

    @Inject
    ServiceMediator mediator;

    @Inject
    SecurityIdentity identity;

    @Inject
    TransactionManager tm;

    @PermitAll
    @WithRoles
    @Override
    public DatasetService.DatasetList listByTest(int testId, String filter, Integer limit, Integer page, String sort,
            SortDirection direction, Integer viewId) {
        StringBuilder sql = new StringBuilder("WITH schema_agg AS (")
                .append(SCHEMAS_SELECT).append(" WHERE testid = :testId GROUP BY dataset_id")
                .append("), ").append(VALIDATION_SELECT);
        JsonNode jsonFilter = null;
        if (filter != null && !filter.isBlank() && !filter.equals("{}")) {
            sql.append(", all_labels AS (").append(ALL_LABELS_SELECT).append(" WHERE testid = :testId GROUP BY dataset.id)");
            sql.append(DATASET_SUMMARY_SELECT);
            addViewIdCondition(sql, viewId);
            sql.append(
                    " JOIN all_labels ON all_labels.dataset_id = ds.id WHERE testid = :testId AND all_labels.values @> :jsonFilter");
            jsonFilter = Util.parseFingerprint(filter);
        } else {
            sql.append(DATASET_SUMMARY_SELECT);
            addViewIdCondition(sql, viewId);
            sql.append(" WHERE testid = :testId");
        }
        addOrderAndPaging(limit, page, sort, direction, sql);
        NativeQuery<DatasetSummary> query = initTypes(sql.toString());
        query.setParameter("testId", testId);
        if (jsonFilter != null) {
            query.setParameter("jsonFilter", jsonFilter, JsonBinaryType.INSTANCE);
        }
        if (viewId != null) {
            query.setParameter("viewId", viewId);
        }
        DatasetService.DatasetList list = new DatasetService.DatasetList();
        list.datasets = query.getResultList();
        list.total = DatasetDAO.count("testid = ?1", testId);
        return list;
    }

    private void addViewIdCondition(StringBuilder sql, Integer viewId) {
        if (viewId == null) {
            sql.append("(SELECT id FROM view WHERE test_id = :testId AND name = 'Default')");
        } else {
            sql.append(":viewId");
        }
    }

    private NativeQuery<DatasetSummary> initTypes(String sql) {
        return em.unwrap(Session.class).createNativeQuery(sql.toString(), Tuple.class)
                .addScalar("id", StandardBasicTypes.INTEGER)
                .addScalar("runId", StandardBasicTypes.INTEGER)
                .addScalar("ordinal", StandardBasicTypes.INTEGER)
                .addScalar("testId", StandardBasicTypes.INTEGER)
                .addScalar("testname", StandardBasicTypes.TEXT)
                .addScalar("description", StandardBasicTypes.TEXT)
                .addScalar("start", StandardBasicTypes.LONG)
                .addScalar("stop", StandardBasicTypes.LONG)
                .addScalar("owner", StandardBasicTypes.TEXT)
                .addScalar("access", StandardBasicTypes.INTEGER)
                .addScalar("view", JsonBinaryType.INSTANCE)
                .addScalar("schemas", JsonBinaryType.INSTANCE)
                .addScalar("validationErrors", JsonBinaryType.INSTANCE)
                .setTupleTransformer((tuples, aliases) -> {
                    DatasetSummary summary = new DatasetSummary();
                    summary.id = (int) tuples[0];
                    summary.runId = (int) tuples[1];
                    summary.ordinal = (int) tuples[2];
                    summary.testId = (int) tuples[3];
                    summary.testname = (String) tuples[4];
                    summary.description = (String) tuples[5];
                    summary.start = Instant.ofEpochMilli((Long) tuples[6]);
                    summary.stop = Instant.ofEpochMilli((Long) tuples[7]);
                    summary.owner = (String) tuples[8];
                    summary.access = Access.fromInt((int) tuples[9]);
                    summary.view = IndexedLabelValueMap.fromObjectNode((ObjectNode) tuples[10]);
                    summary.schemas = Util.OBJECT_MAPPER.convertValue(tuples[11], new TypeReference<>() {
                    });
                    if (tuples[12] != null && !((ArrayNode) tuples[12]).isEmpty()) {
                        try {
                            summary.validationErrors = Arrays
                                    .asList(Util.OBJECT_MAPPER.treeToValue((ArrayNode) tuples[12], ValidationError[].class));
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                        ;
                    }
                    return summary;
                });
    }

    private void addOrderAndPaging(Integer limit, Integer page, String sort, SortDirection direction, StringBuilder sql) {
        if (sort != null && sort.startsWith("view_data:")) {
            String[] parts = sort.split(":", 3);
            String vcid = parts[1];
            String label = parts[2];
            sql.append(" ORDER BY");
            // prefer numeric sort
            sql.append(" to_double(dv.value->'").append(vcid).append("'->>'").append(label).append("')");
            Util.addDirection(sql, direction);
            sql.append(", dv.value->'").append(vcid).append("'->>'").append(label).append("'");
            Util.addDirection(sql, direction);
        } else {
            Util.addOrderBy(sql, sort, direction);
        }
        Util.addLimitOffset(sql, limit, page);
    }

    @WithRoles
    @Override
    public DatasetService.DatasetList listBySchema(String uri, Integer limit, Integer page, String sort,
            @DefaultValue("Descending") SortDirection direction) {
        StringBuilder sql = new StringBuilder(LIST_SCHEMA_DATASETS);
        // TODO: filtering by fingerprint
        addOrderAndPaging(limit, page, sort, direction, sql);
        NativeQuery<DatasetSummary> query = initTypes(sql.toString());
        query.setParameter(1, uri);
        DatasetService.DatasetList list = new DatasetService.DatasetList();
        list.datasets = query.getResultList();
        list.total = ((Number) em.createNativeQuery("SELECT COUNT(dataset_id) FROM dataset_schemas WHERE uri = ?1")
                .setParameter(1, uri).getSingleResult()).longValue();
        return list;
    }

    @Override
    public List<LabelValue> labelValues(int datasetId) {
        Stream<Object[]> stream = em.unwrap(Session.class).createNativeQuery("""
                SELECT label_id, label.name AS label_name, schema.id AS schema_id, schema.name AS schema_name, schema.uri, value
                FROM label_values
                JOIN label ON label.id = label_id
                JOIN schema ON label.schema_id = schema.id
                WHERE dataset_id = ?1
                """, Object[].class)
                .setParameter(1, datasetId)
                .addScalar("label_id", StandardBasicTypes.INTEGER)
                .addScalar("label_name", StandardBasicTypes.TEXT)
                .addScalar("schema_id", StandardBasicTypes.INTEGER)
                .addScalar("schema_name", StandardBasicTypes.TEXT)
                .addScalar("uri", StandardBasicTypes.TEXT)
                .addScalar("value", JsonBinaryType.INSTANCE)
                .getResultStream();
        return stream.map(row -> {
            LabelValue value = new LabelValue();
            value.id = (int) row[0];
            value.name = (String) row[1];
            value.schema = new SchemaService.SchemaDescriptor((int) row[2], (String) row[3], (String) row[4]);
            value.value = (JsonNode) row[5];
            return value;
        }).collect(Collectors.toList());
    }

    @Override
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    public LabelPreview previewLabel(int datasetId, Label label) {
        // This is executed with elevated permissions, but with the same as a normal label calculation would use
        // Therefore we need to explicitly check dataset ownership
        DatasetDAO dataset = DatasetDAO.findById(datasetId);
        if (dataset == null || !Roles.hasRoleWithSuffix(identity, dataset.owner, "-tester")) {
            throw ServiceException.badRequest("Dataset not found or insufficient privileges.");
        }

        String extractors;
        try {
            extractors = Util.OBJECT_MAPPER.writeValueAsString(label.extractors);
        } catch (JsonProcessingException e) {
            log.error("Cannot serialize label extractors", e);
            throw ServiceException.badRequest("Cannot serialize label extractors");
        }
        JsonNode extracted;
        LabelPreview preview = new LabelPreview();
        try {
            extracted = (JsonNode) em.createNativeQuery(LABEL_PREVIEW).unwrap(NativeQuery.class)
                    .setParameter(1, extractors)
                    .setParameter(2, datasetId)
                    .setParameter(3, label.schemaId)
                    .addScalar("value", JsonBinaryType.INSTANCE).getSingleResult();
        } catch (PersistenceException e) {
            preview.output = Util.explainCauses(e);
            return preview;
        }

        if (label.function == null || label.function.isBlank()) {
            preview.value = extracted;
        } else {
            AtomicReference<String> errorRef = new AtomicReference<>();
            AtomicReference<String> outputRef = new AtomicReference<>();
            JsonNode result = Util.evaluateOnce(label.function, extracted, Util::convertToJson,
                    (code, exception) -> errorRef.set("Execution failed: " + exception.getMessage() + ":\n" + code),
                    outputRef::set);
            preview.value = errorRef.get() == null ? result : JsonNodeFactory.instance.textNode(errorRef.get());
            preview.output = outputRef.get();
        }
        return preview;
    }

    @WithRoles
    @Override
    public DatasetSummary getSummary(int datasetId, int viewId) {
        try {
            NativeQuery<DatasetSummary> query = initTypes(
                    "WITH schema_agg AS (" + SCHEMAS_SELECT +
                            " WHERE ds.dataset_id = ?1 GROUP BY ds.dataset_id), " +
                            VALIDATION_SELECT + DATASET_SUMMARY_SELECT + "?2 WHERE ds.id = ?1");
            query.setParameter(1, datasetId).setParameter(2, viewId);
            return query.getSingleResult();
        } catch (NoResultException e) {
            throw ServiceException.notFound("Cannot find dataset " + datasetId);
        }
    }

    @WithRoles
    @Override
    public Dataset getDataset(int datasetId) {
        DatasetDAO dataset = DatasetDAO.findById(datasetId);
        if (dataset != null) {
            Hibernate.initialize(dataset.data);
        } else {
            log.warnf("Could not retrieve dataset: " + datasetId);
            throw ServiceException.notFound("Could not find Dataset: " + datasetId
                    + ". If you have recently started a re-tranformation, please wait until datasets are available");

        }
        return DatasetMapper.from(dataset);
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    void calculateLabelValues(int testId, int datasetId, int queryLabelId, boolean isRecalculation) {
        log.debugf("Calculating label values for dataset %d, label %d", datasetId, queryLabelId);
        List<Object[]> extracted;
        try {
            // Note: we are fetching even labels that are marked as private/could be otherwise inaccessible
            // to the uploading user. However, the uploader should not have rights to fetch these anyway...
            extracted = em.unwrap(Session.class).createNativeQuery(LABEL_QUERY, Object[].class)
                    .setParameter(1, datasetId)
                    .setParameter(2, queryLabelId)
                    .addScalar("label_id", StandardBasicTypes.INTEGER)
                    .addScalar("name", StandardBasicTypes.TEXT)
                    .addScalar("function", StandardBasicTypes.TEXT)
                    .addScalar("value", JsonBinaryType.INSTANCE)
                    .getResultList();
        } catch (PersistenceException e) {
            logMessageInNewTx(datasetId, PersistentLogDAO.ERROR,
                    "Failed to extract data (JSONPath expression error?): " + Util.explainCauses(e));
            findFailingExtractor(datasetId);
            return;
        }

        // While any change should remove the label_value first via trigger it is possible
        // that something triggers two events after each other, removing the data (twice)
        // before the first event is processed. The second event would then find the label_value
        // already present and would fail with a constraint violation.
        if (queryLabelId < 0) {
            LabelValueDAO.delete("datasetId", datasetId);
        } else {
            LabelValueDAO.delete("datasetId = ?1 AND labelId = ?2", datasetId, queryLabelId);
        }

        FingerprintDAO.deleteById(datasetId);
        Util.evaluateWithCombinationFunction(extracted,
                (row) -> (String) row[2],
                (row) -> (row[3] instanceof ArrayNode ? flatten((ArrayNode) row[3]) : (JsonNode) row[3]),
                (row, result) -> createLabelValue(datasetId, testId, (int) row[0], Util.convertToJson(result)),
                (row) -> createLabelValue(datasetId, testId, (int) row[0], (JsonNode) row[3]),
                (row, e, jsCode) -> logMessage(datasetId, PersistentLogDAO.ERROR,
                        "Evaluation of label %s failed: '%s' Code:<pre>%s</pre>", row[0], e.getMessage(), jsCode),
                (out) -> logMessage(datasetId, PersistentLogDAO.DEBUG, "Output while calculating labels: <pre>%s</pre>", out));

        // create new dataset views from the recently created label values
        calcDatasetViews(datasetId);

        createFingerprint(datasetId, testId);
        mediator.updateLabels(new Dataset.LabelsUpdatedEvent(testId, datasetId, isRecalculation));
        if (mediator.testMode())
            Util.registerTxSynchronization(tm, txStatus -> mediator.publishEvent(AsyncEventChannels.DATASET_UPDATED_LABELS,
                    testId, new Dataset.LabelsUpdatedEvent(testId, datasetId, isRecalculation)));
    }

    @Transactional
    public void calcDatasetViews(int datasetId) {
        // TODO(user) move calc_dataset_view into Horreum business logic see https://github.com/hibernate/hibernate-orm/pull/7457
        em.createNativeQuery("DELETE FROM dataset_view WHERE dataset_id = ?1").setParameter(1, datasetId).executeUpdate();
        em.createNativeQuery("call calc_dataset_view(?1, NULL);").setParameter(1, datasetId).executeUpdate();
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void calcDatasetViewsByTestAndView(int testId, int viewId) {
        // delete all dataset views associated to the provided viewId and testId
        // for new views it won't delete anything
        em.createNativeQuery(
                "DELETE FROM dataset_view WHERE view_id = ?1 AND dataset_id IN (SELECT id FROM dataset WHERE testid = ?2)")
                .setParameter(1, viewId)
                .setParameter(2, testId)
                .executeUpdate();

        // re-create dataset views associated to the provided viewId
        try (ScrollableResults<Integer> datasetIds = em
                .createNativeQuery("SELECT id FROM dataset WHERE testid = ?1")
                .setParameter(1, testId)
                .unwrap(NativeQuery.class)
                .setReadOnly(false)
                .setFetchSize(100)
                .scroll(ScrollMode.FORWARD_ONLY)) {
            while (datasetIds.next()) {
                int datasetId = datasetIds.get();
                log.tracef("Recalculate dataset views for view %d and dataset %d", viewId, datasetId);
                em.createNativeQuery("call calc_dataset_view(?1, ?2);")
                        .setParameter(1, datasetId)
                        .setParameter(2, viewId)
                        .executeUpdate();
            }
        }
    }

    @Transactional
    public void deleteDataset(int datasetId) {
        em.createNativeQuery("DELETE FROM label_values WHERE dataset_id = ?1").setParameter(1, datasetId).executeUpdate();
        em.createNativeQuery("DELETE FROM dataset_schemas WHERE dataset_id = ?1").setParameter(1, datasetId).executeUpdate();
        em.createNativeQuery("DELETE FROM dataset_view WHERE dataset_id = ?1").setParameter(1, datasetId).executeUpdate();
        em.createNativeQuery("DELETE FROM fingerprint WHERE dataset_id = ?1").setParameter(1, datasetId).executeUpdate();
        em.createNativeQuery("DELETE FROM dataset WHERE id = ?1").setParameter(1, datasetId).executeUpdate();
    }

    private ArrayNode flatten(ArrayNode bucket) {
        JsonNode data = bucket.get(0);
        if (data == null)
            return bucket;

        if (data instanceof ArrayNode) {
            bucket.removeAll();
            data.forEach(bucket::add);
        }
        return bucket;
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void findFailingExtractor(int datasetId) {
        List<Object[]> extractors = em.unwrap(Session.class).createNativeQuery("""
                SELECT ds.uri, label.name AS name, le.name AS extractor_name, ds.index, le.jsonpath
                FROM dataset_schemas ds
                JOIN label ON label.schema_id = ds.schema_id
                JOIN label_extractors le ON le.label_id = label.id
                WHERE ds.dataset_id = ?1
                """, Object[].class)
                .setParameter(1, datasetId)
                .getResultList();
        for (Object[] row : extractors) {
            try {
                // actual result of query is ignored
                em.createNativeQuery(
                        "SELECT jsonb_path_query_first(data -> (?1), (?2)::jsonpath)#>>'{}' FROM dataset WHERE id = ?3")
                        .setParameter(1, row[3]).setParameter(2, row[4]).setParameter(3, datasetId).getSingleResult();
            } catch (PersistenceException e) {
                logMessageInNewTx(datasetId, PersistentLogDAO.ERROR,
                        "There seems to be an error in schema <code>%s</code> label <code>%s</code>, extractor <code>%s</code>, JSONPath expression <code>%s</code>: %s",
                        row[0], row[1], row[2], row[4], Util.explainCauses(e));
                return;
            }
        }
        logMessage(datasetId, PersistentLogDAO.DEBUG,
                "We thought there's an error in one of the JSONPaths but independent validation did not find any problems.");
    }

    private void createLabelValue(int datasetId, int testId, int labelId, JsonNode value) {
        LabelValueDAO labelValue = new LabelValueDAO();
        labelValue.datasetId = datasetId;
        labelValue.labelId = labelId;
        labelValue.value = value;
        labelValue.persist();
    }

    private void createFingerprint(int datasetId, int testId) {
        JsonNode json = null;
        try {
            json = em.createQuery("SELECT t.fingerprintLabels from test t WHERE t.id = ?1", JsonNode.class)
                    .setParameter(1, testId).getSingleResult();
        } catch (NoResultException noResultException) {
            log.infof("Could not find fingerprint for dataset: %d", datasetId);
        }
        if (json == null)
            return;

        ObjectNode fpNode = JsonNodeFactory.instance.objectNode();
        List<LabelValueDAO> labelValues = LabelValueDAO.find("datasetId", datasetId).list();
        List<String[]> labelPairs = new ArrayList<>(labelValues.size());
        for (var lv : labelValues)
            labelPairs.add(new String[] { LabelDAO.<LabelDAO> findById(lv.labelId).name, lv.value.asText() });

        for (int i = 0; i < json.size(); i++)
            for (var name : labelPairs) {
                if (json.get(i).asText().equals(name[0]))
                    fpNode.put(name[0], name[1]);
            }

        FingerprintDAO fp = new FingerprintDAO();
        fp.datasetId = datasetId;
        fp.dataset = DatasetDAO.findById(datasetId);
        fp.fingerprint = fpNode;
        if (fp.datasetId > 0 && fp.dataset != null)
            fp.persist();
    }

    @Transactional
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    void updateFingerprints(int testId) {
        for (var dataset : DatasetDAO.<DatasetDAO> find("testid", testId).list()) {
            FingerprintDAO.deleteById(dataset.id);
            createFingerprint(dataset.id, testId);
        }
    }

    public void onNewDataset(Dataset.EventNew event) {
        calculateLabelValues(event.testId, event.datasetId, event.labelId, event.isRecalculation);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    void logMessageInNewTx(int datasetId, int level, String message, Object... params) {
        logMessage(datasetId, level, message, params);
    }

    private void logMessage(int datasetId, int level, String message, Object... params) {
        String msg = String.format(message, params);
        DatasetDAO dataset = DatasetDAO.findById(datasetId);
        if (dataset != null) {
            log.tracef("Logging %s for test %d, dataset %d: %s", PersistentLogDAO.logLevel(level), dataset.testid, datasetId,
                    msg);
            new DatasetLogDAO(em.getReference(TestDAO.class, dataset.testid), em.getReference(DatasetDAO.class, datasetId),
                    level, "labels", msg).persist();
        }
    }
}
