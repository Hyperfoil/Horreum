package io.hyperfoil.tools.horreum.svc;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.internal.services.ReportService;
import io.hyperfoil.tools.horreum.api.report.ReportComment;
import io.hyperfoil.tools.horreum.api.report.TableReport;
import io.hyperfoil.tools.horreum.api.report.TableReportConfig;
import io.hyperfoil.tools.horreum.entity.PersistentLogDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.entity.report.*;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.hyperfoil.tools.horreum.mapper.ReportCommentMapper;
import io.hyperfoil.tools.horreum.mapper.TableReportMapper;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import io.quarkus.security.identity.SecurityIdentity;

@ApplicationScoped
@Startup
public class ReportServiceImpl implements ReportService {

    static {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    }

    @Inject
    SecurityIdentity identity;

    @Inject
    EntityManager em;

    @Inject
    TimeService timeService;

    @PermitAll
    @WithRoles
    @Override
    public AllTableReports getTableReports(String folder, Integer testId, String roles, Integer limit, Integer page,
            String sort, SortDirection direction) {
        StringBuilder queryBuilder = new StringBuilder()
                .append("WITH selected AS (")
                .append("SELECT tr.id AS report_id, trc.id AS config_id, trc.title, test.name AS testname, test.id as testid, tr.created FROM tablereportconfig trc ")
                .append("LEFT JOIN tablereport tr ON trc.id = tr.config_id ")
                .append("LEFT JOIN test ON test.id = trc.testid WHERE 1 = 1 ");
        Map<String, Object> params = new HashMap<>();
        if (testId != null) {
            queryBuilder.append("AND testid = :test ");
            params.put("test", testId);
        } else if (folder != null && !"*".equals(folder)) {
            queryBuilder.append("AND COALESCE(test.folder, '') = :folder ");
            params.put("folder", folder);
        }
        Set<String> rolesList = Roles.expandRoles(roles, identity);
        if (rolesList != null) {
            queryBuilder.append("AND test.owner IN :roles ");
            params.put("roles", rolesList);
        }
        queryBuilder
                .append("ORDER BY created DESC), grouped AS (")
                .append("SELECT MAX(config_id) AS config_id, title, testname, testid, ")
                .append("COALESCE(jsonb_agg(jsonb_build_object('report_id', report_id, 'config_id', config_id, 'created', EXTRACT (EPOCH FROM created))) FILTER (WHERE report_id IS NOT NULL), '[]') AS reports ")
                .append("FROM selected GROUP BY title, testname, testid ")
                .append(") SELECT config_id, title, testname, testid, reports, ")
                .append("(SELECT COUNT(*) FROM grouped) AS total FROM grouped");
        Util.addPaging(queryBuilder, limit, page, sort, direction);

        NativeQuery<Object[]> query = em.unwrap(Session.class).createNativeQuery(queryBuilder.toString(), Object[].class)
                .addScalar("config_id", StandardBasicTypes.INTEGER)
                .addScalar("title", StandardBasicTypes.TEXT)
                .addScalar("testname", StandardBasicTypes.TEXT)
                .addScalar("testid", StandardBasicTypes.INTEGER)
                .addScalar("reports", JsonBinaryType.INSTANCE)
                .addScalar("total", StandardBasicTypes.INTEGER);
        for (var entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
        List<Object[]> rows = query.getResultList();

        AllTableReports result = new AllTableReports();
        result.count = !rows.isEmpty() ? (int) rows.get(0)[5] : 0;
        result.reports = new ArrayList<>();

        for (Object[] row : rows) {
            TableReportSummary summary = new TableReportSummary();
            summary.configId = (int) row[0];
            summary.title = (String) row[1];
            summary.testName = (String) row[2];
            summary.testId = row[3] == null ? -1 : (int) row[3];
            summary.reports = new ArrayList<>();
            ArrayNode reports = (ArrayNode) row[4];
            reports.forEach(report -> {
                TableReportSummaryItem item = new TableReportSummaryItem();
                item.id = report.get("report_id").asInt();
                item.configId = report.get("config_id").asInt();
                item.created = Instant.ofEpochSecond(report.get("created").asLong());
                summary.reports.add(item);
            });
            result.reports.add(summary);
        }
        return result;
    }

    @PermitAll
    @WithRoles
    @Override
    public TableReportConfig getTableReportConfig(int id) {
        TableReportConfigDAO config = TableReportConfigDAO.findById(id);
        if (config == null) {
            throw ServiceException.notFound("Table report config does not exist or insufficient permissions.");
        }
        return TableReportMapper.fromTableReportConfig(config);
    }

    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Override
    @Transactional
    public TableReport updateTableReportConfig(TableReportConfig dto, Integer reportId) {
        if (dto.id != null && dto.id < 0) {
            dto.id = null;
        }
        boolean createNewConfig = reportId == null || reportId < 0;
        if (createNewConfig) {
            // We are going to create a new report, therefore we'll use a new config
            dto.id = null;
        }
        for (var component : dto.components) {
            if (component.id != null && component.id < 0 || createNewConfig) {
                component.id = null;
            }
        }
        validateTableConfig(dto);
        TableReportConfigDAO config = TableReportMapper.toTableReportConfig(dto);
        config.ensureLinked();
        TableReportDAO report = createTableReport(config, reportId);
        if (config.id == null) {
            config.persist();
        } else {
            TableReportConfigDAO original = TableReportConfigDAO.findById(config.id);
            original.components.clear();
            original.components.addAll(config.components);
            config.components = original.components;
            em.merge(config);
        }
        if (report.id == null) {
            report.persist();
        } else {
            em.merge(report);
        }
        em.flush();
        return TableReportMapper.from(report);
    }

    private void validateTableConfig(TableReportConfig config) {
        if (config == null) {
            throw ServiceException.badRequest("No config");
        }
        if (config.test == null || config.test.id == null) {
            throw ServiceException.badRequest("Table report configuration does not have a test with ID assigned.");
        }
        if (!nullOrEmpty(config.filterFunction) && nullOrEmpty(config.filterLabels)) {
            throw ServiceException.badRequest("Filter function is defined but there are no accessors");
        }
        if (nullOrEmpty(config.categoryLabels)) {
            if (!nullOrEmpty(config.categoryFunction)) {
                throw ServiceException.badRequest("Category function is defined but there are no accessors");
            } else if (!nullOrEmpty(config.categoryFormatter)) {
                throw ServiceException.badRequest("Category formatter is defined but there are not accessors");
            }
        }
        if (nullOrEmpty(config.seriesLabels)) {
            throw ServiceException.badRequest("Service accessors must be defined");
        }
        if (nullOrEmpty(config.scaleLabels)) {
            if (!nullOrEmpty(config.scaleFunction)) {
                throw ServiceException.badRequest("Label function is defined but there are no accessors");
            } else if (!nullOrEmpty(config.scaleFormatter)) {
                throw ServiceException.badRequest("Label formatter is defined but there are no accessors");
            }
        }
    }

    @PermitAll
    @WithRoles
    @Override
    public TableReport getTableReport(int id) {
        TableReportDAO report = TableReportDAO.findById(id);
        if (report == null) {
            throw ServiceException.notFound("Report " + id + " does not exist.");
        }
        Hibernate.initialize(report.config);
        return TableReportMapper.from(report);
    }

    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    @Override
    public void deleteTableReport(int id) {
        TableReportDAO report = TableReportDAO.findById(id);
        if (report == null) {
            throw ServiceException.notFound("Report " + id + " does not exist.");
        }
        report.delete();
        report.config.delete();
    }

    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Override
    @Transactional
    public ReportComment updateComment(int reportId, ReportComment dto) {
        ReportCommentDAO comment = ReportCommentMapper.to(dto);
        if (comment.id == null || comment.id < 0) {
            comment.id = null;
            comment.report = TableReportDAO.findById(reportId);
            if (comment.comment != null && !comment.comment.isEmpty()) {
                comment.persistAndFlush();
            }
        } else if (nullOrEmpty(comment.comment)) {
            ReportCommentDAO.deleteById(comment.id);
            return null;
        } else {
            comment.report = TableReportDAO.findById(reportId);
            em.merge(comment);
        }
        return ReportCommentMapper.from(comment);
    }

    @Override
    public TableReportConfig exportTableReportConfig(int id) {
        // In case of TableReports we don't need to accumulate
        // data from other sources not remap data so this is the same
        return getTableReportConfig(id);
    }

    @RolesAllowed({ Roles.ADMIN, Roles.TESTER })
    @WithRoles
    @Transactional
    @Override
    public void importTableReportConfig(TableReportConfig config) {
        validateTableConfig(config);
        config.ensureLinked();
        TableReportConfigDAO trc = TableReportMapper.toTableReportConfig(config);
        em.merge(trc);
    }

    @PermitAll
    @WithRoles
    @Override
    public TableReport previewTableReport(TableReportConfig dto, Integer reportId) {
        validateTableConfig(dto);
        TableReportConfigDAO config = TableReportMapper.toTableReportConfig(dto);
        TableReportDAO report = createTableReport(config, reportId);
        em.detach(report);
        return TableReportMapper.from(report);
    }

    private TableReportDAO createTableReport(TableReportConfigDAO config, Integer reportId) {
        Integer testId = config.test.id;
        TestDAO test = TestDAO.findById(testId);
        if (test == null) {
            throw ServiceException.badRequest("Cannot find test with ID " + testId);
        } else {
            // We don't assign the full test because then we'd serialize the complete object...
            config.test.name = test.name;
        }
        TableReportDAO report;
        if (reportId == null) {
            report = new TableReportDAO();
            report.comments = Collections.emptyList();
            report.created = timeService.now();
        } else {
            report = TableReportDAO.findById(reportId);
            if (report == null) {
                throw ServiceException.badRequest("Cannot find report ID " + reportId);
            }
            report.logs.clear();
        }
        report.config = config;
        List<Object[]> categories = Collections.emptyList(), series, scales = Collections.emptyList();
        NativeQuery<Object[]> timestampQuery;
        if (!nullOrEmpty(config.filterLabels)) {
            List<Integer> datasetIds = filterDatasetIds(config, report);
            Log.debugf("Table report %s(%d) includes datasets %s", config.title, config.id, datasetIds);
            series = selectByDatasets(config.seriesLabels, datasetIds);
            Log.debugf("Series: %s", rowsToMap(series));
            if (!nullOrEmpty(config.scaleLabels)) {
                scales = selectByDatasets(config.scaleLabels, datasetIds);
                Log.debugf("Scales: %s", rowsToMap(scales));
            }
            if (!nullOrEmpty(config.categoryLabels)) {
                categories = selectByDatasets(config.categoryLabels, datasetIds);
                Log.debugf("Categories: %s", rowsToMap(categories));
            }
            timestampQuery = em.unwrap(Session.class)
                    .createNativeQuery("SELECT id, start FROM dataset WHERE id IN :datasets", Object[].class)
                    .setParameter("datasets", datasetIds);
        } else {
            log(report, PersistentLogDAO.DEBUG, "Table report %s(%d) includes all datasets for test %s(%d)", config.title,
                    config.id, config.test.name, config.test.id);
            series = selectByTest(config.test.id, config.seriesLabels);
            Log.debugf("Series: %s", rowsToMap(series));
            if (!nullOrEmpty(config.scaleLabels)) {
                scales = selectByTest(config.test.id, config.scaleLabels);
                Log.debugf("Scales: %s", rowsToMap(scales));
            }
            if (!nullOrEmpty(config.categoryLabels)) {
                categories = selectByTest(config.test.id, config.categoryLabels);
                Log.debugf("Categories: %s", rowsToMap(categories));
            }
            timestampQuery = em.unwrap(Session.class)
                    .createNativeQuery("SELECT id, start FROM dataset WHERE testid = ?", Object[].class)
                    .setParameter(1, config.test.id);
        }
        if (categories.isEmpty() && !series.isEmpty()) {
            assert config.categoryLabels == null;
            assert config.categoryFunction == null;
            assert config.categoryFormatter == null;
            categories = series.stream()
                    .map(row -> new Object[] { row[0], row[1], row[2], JsonNodeFactory.instance.textNode("") })
                    .collect(Collectors.toList());
        }
        if (scales.isEmpty() && !series.isEmpty()) {
            assert config.scaleLabels == null;
            assert config.scaleFunction == null;
            assert config.scaleFormatter == null;
            scales = series.stream().map(row -> new Object[] { row[0], row[1], row[2], JsonNodeFactory.instance.textNode("") })
                    .collect(Collectors.toList());
        }
        Map<Integer, TableReportDAO.Data> datasetData = series.isEmpty() ? Collections.emptyMap()
                : getData(config, report, categories, series, scales);
        Log.debugf("Data per dataset: %s", datasetData);

        Map<Integer, Instant> timestamps = timestampQuery.getResultStream()
                .collect(Collectors.toMap(row -> (Integer) row[0], row -> (Instant) row[1]));
        // TODO: customizable time range
        List<Integer> datasetIds = getFinalDatasetIds(timestamps, datasetData);
        List<List<Object[]>> values = config.components.stream()
                .map(component -> selectByDatasets(component.labels, datasetIds))
                .collect(Collectors.toList());
        executeInContext(config, context -> {
            for (int i = 0; i < values.size(); i++) {
                List<Object[]> valuesForComponent = values.get(i);
                ReportComponentDAO component = config.components.get(i);
                for (Object[] row : valuesForComponent) {
                    Integer datasetId = (Integer) row[0];
                    JsonNode value = (JsonNode) row[3];
                    TableReportDAO.Data data = datasetData.get(datasetId);
                    if (nullOrEmpty(component.function)) {
                        if (value == null || value.isNull()) {
                            data.values.addNull();
                        } else {
                            Double dValue = value.asDouble();
                            if (dValue != null) {
                                data.values.add(dValue);
                            } else {
                                data.values.add(value);
                            }
                        }
                    } else {
                        String jsCode = buildCode(component.function, String.valueOf(value));
                        try {
                            Value calculatedValue = context.eval("js", jsCode);
                            Double maybeDouble = Util.toDoubleOrNull(calculatedValue,
                                    err -> log(report, PersistentLogDAO.ERROR, err),
                                    info -> log(report, PersistentLogDAO.INFO, info));
                            if (maybeDouble != null) {
                                data.values.add(maybeDouble);
                            } else {
                                data.values.add(Util.convertToJson(calculatedValue));
                            }
                        } catch (PolyglotException e) {
                            log(report, PersistentLogDAO.ERROR,
                                    "Failed to run report %s(%d) label function on run %d. Offending code: <br><pre>%s</pre>",
                                    config.title, config.id, datasetId, jsCode);
                            Log.debug("Caused by exception", e);
                        }
                    }
                }
            }
        });
        report.data = datasetIds.stream().map(datasetData::get).collect(Collectors.toList());
        return report;
    }

    private Map<Object, Object> rowsToMap(List<Object[]> series) {
        return series.stream().collect(
                Collectors.toMap(row -> row[0] == null ? "<null>" : row[0], row -> row[3] == null ? "<null>" : row[3]));
    }

    private boolean nullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    private boolean nullOrEmpty(JsonNode node) {
        return node == null || node.isNull() || node.isEmpty();
    }

    private Map<Integer, TableReportDAO.Data> getData(TableReportConfigDAO config, TableReportDAO report,
            List<Object[]> categories, List<Object[]> series, List<Object[]> scales) {
        assert !categories.isEmpty();
        assert !series.isEmpty();
        assert !scales.isEmpty();

        Map<Integer, TableReportDAO.Data> datasetData = new HashMap<>();
        executeInContext(config, context -> {
            for (Object[] row : categories) {
                TableReportDAO.Data data = new TableReportDAO.Data();
                data.datasetId = (Integer) row[0];
                data.runId = (int) row[1];
                data.ordinal = (int) row[2];
                JsonNode value = (JsonNode) row[3];
                data.values = JsonNodeFactory.instance.arrayNode(config.components.size());
                if (nullOrEmpty(config.categoryFunction)) {
                    data.category = toText(value);
                } else {
                    String jsCode = buildCode(config.categoryFunction, String.valueOf(value));
                    try {
                        data.category = Util.convert(context.eval("js", jsCode)).toString();
                    } catch (PolyglotException e) {
                        log(report, PersistentLogDAO.ERROR,
                                "Failed to run report %s(%d) category function on dataset %d/%d (%d). Offending code: <br><pre>%s</pre>",
                                config.title, config.id, data.runId, data.ordinal, data.datasetId, jsCode);
                        Log.debug("Caused by exception", e);
                        continue;
                    }
                }
                datasetData.put(data.datasetId, data);
            }
            for (Object[] row : series) {
                Integer datasetId = (Integer) row[0];
                int runId = (int) row[1];
                int ordinal = (int) row[2];
                JsonNode value = (JsonNode) row[3];
                TableReportDAO.Data data = datasetData.get(datasetId);
                if (data == null) {
                    log(report, PersistentLogDAO.ERROR, "Missing values for dataset %d!", datasetId);
                    continue;
                }
                if (nullOrEmpty(config.seriesFunction)) {
                    data.series = toText(value);
                } else {
                    String jsCode = buildCode(config.seriesFunction, String.valueOf(value));
                    try {
                        data.series = Util.convert(context.eval("js", jsCode)).toString();
                    } catch (PolyglotException e) {
                        log(report, PersistentLogDAO.ERROR,
                                "Failed to run report %s(%d) series function on run %d/%d (%d). Offending code: <br><pre>%s</pre>",
                                config.title, config.id, runId, ordinal, datasetId, jsCode);
                        Log.debug("Caused by exception", e);
                    }
                }
            }
            for (Object[] row : scales) {
                Integer datasetId = (Integer) row[0];
                int runId = (int) row[1];
                int ordinal = (int) row[2];
                JsonNode value = (JsonNode) row[3];
                TableReportDAO.Data data = datasetData.get(datasetId);
                if (data == null) {
                    log(report, PersistentLogDAO.ERROR, "Missing values for dataset %d!", datasetId);
                    continue;
                }
                if (nullOrEmpty(config.scaleFunction)) {
                    data.scale = toText(value);
                } else {
                    String jsCode = buildCode(config.scaleFunction, String.valueOf(value));
                    try {
                        data.scale = Util.convert(context.eval("js", jsCode)).toString();
                    } catch (PolyglotException e) {
                        log(report, PersistentLogDAO.ERROR,
                                "Failed to run report %s(%d) label function on dataset %d/%d (%d). Offending code: <br><pre>%s</pre>",
                                config.title, config.id, runId, ordinal, datasetId, jsCode);
                        Log.debug("Caused by exception", e);
                    }
                }
            }
        });
        return datasetData;
    }

    private String toText(JsonNode value) {
        return value == null ? "" : value.isTextual() ? value.asText() : value.toString();
    }

    private List<Integer> getFinalDatasetIds(Map<Integer, Instant> timestamps, Map<Integer, TableReportDAO.Data> datasetData) {
        Map<Coords, TableReportDAO.Data> dataByCoords = new HashMap<>();
        for (TableReportDAO.Data data : datasetData.values()) {
            Instant dataTimestamp = timestamps.get(data.datasetId);
            if (dataTimestamp == null) {
                Log.errorf("No timestamp for dataset %d", data.datasetId);
                continue;
            }
            Coords coords = new Coords(data.category, data.series, data.scale);
            TableReportDAO.Data prev = dataByCoords.get(coords);
            if (prev == null) {
                dataByCoords.put(coords, data);
                continue;
            }
            Instant prevTimestamp = timestamps.get(prev.datasetId);
            if (prevTimestamp == null) {
                Log.errorf("No timestamp for prev dataset %d", prev.datasetId);
                dataByCoords.put(coords, data);
            } else if (prevTimestamp.isBefore(dataTimestamp)) {
                dataByCoords.put(coords, data);
            }
        }
        return dataByCoords.values().stream().map(data -> data.datasetId).collect(Collectors.toList());
    }

    private List<Object[]> selectByTest(int testId, ArrayNode labels) {
        // We need an expression that will return NULLs when the labels are not present
        StringBuilder sql = new StringBuilder("WITH ");
        sql.append("ds AS (SELECT id, runid, ordinal FROM dataset WHERE testid = :testid), ");
        sql.append("values as (SELECT lv.dataset_id, label.name, lv.value FROM label_values lv ");
        sql.append(
                "JOIN label ON label.id = label_id WHERE dataset_id IN (SELECT id FROM ds) AND json_contains(:labels, label.name)) ");
        sql.append("SELECT id, runid, ordinal, ");
        if (labels.size() != 1) {
            // jsonb_object_agg fails when values.name is null
            sql.append(
                    "COALESCE(jsonb_object_agg(values.name, values.value) FILTER (WHERE values.name IS NOT NULL), '{}'::jsonb)");
        } else {
            sql.append("values.value");
        }
        sql.append(" AS value FROM ds LEFT JOIN values ON ds.id = values.dataset_id ");
        if (labels.size() != 1) {
            sql.append(" GROUP BY id, runid, ordinal");
        }
        NativeQuery<Object[]> query = em.unwrap(Session.class).createNativeQuery(sql.toString(), Object[].class)
                .setParameter("testid", testId)
                .setParameter("labels", labels, JsonBinaryType.INSTANCE)
                .addScalar("id", StandardBasicTypes.INTEGER)
                .addScalar("runid", StandardBasicTypes.INTEGER)
                .addScalar("ordinal", StandardBasicTypes.INTEGER)
                .addScalar("value", JsonBinaryType.INSTANCE);
        return query.getResultList();
    }

    private List<Object[]> selectByDatasets(ArrayNode labels, List<Integer> datasets) {
        StringBuilder sql = new StringBuilder("SELECT dataset.id AS id, dataset.runid AS runid, dataset.ordinal AS ordinal, ");
        if (labels.size() != 1) {
            sql.append("COALESCE(jsonb_object_agg(label.name, lv.value) FILTER (WHERE label.name IS NOT NULL), '{}'::jsonb)");
        } else {
            sql.append("lv.value");
        }
        sql.append(" AS value FROM dataset ")
                .append("LEFT JOIN label_values lv ON dataset.id = lv.dataset_id ")
                .append("LEFT JOIN label ON label.id = lv.label_id ")
                .append("WHERE dataset.id IN :datasets AND (json_contains(:labels, label.name) OR label.name IS NULL)");
        if (labels.size() != 1) {
            sql.append(" GROUP BY dataset.id, dataset.runid, dataset.ordinal");
        }
        NativeQuery<Object[]> query = em.unwrap(Session.class).createNativeQuery(sql.toString(), Object[].class)
                .setParameter("datasets", datasets)
                .setParameter("labels", labels, JsonBinaryType.INSTANCE)
                .addScalar("id", StandardBasicTypes.INTEGER)
                .addScalar("runid", StandardBasicTypes.INTEGER)
                .addScalar("ordinal", StandardBasicTypes.INTEGER)
                .addScalar("value", JsonBinaryType.INSTANCE);
        return query.getResultList();
    }

    public static final class Coords {
        final String category;
        final String series;
        final String labels;

        public Coords(String category, String series, String labels) {
            this.category = category;
            this.series = series;
            this.labels = labels;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Coords coords = (Coords) o;
            return Objects.equals(category, coords.category) && Objects.equals(series, coords.series)
                    && Objects.equals(labels, coords.labels);
        }

        @Override
        public int hashCode() {
            return Objects.hash(category, series, labels);
        }
    }

    private List<Integer> filterDatasetIds(TableReportConfigDAO config, TableReportDAO report) {
        List<Object[]> list = selectByTest(config.test.id, config.filterLabels);
        if (list.isEmpty()) {
            log(report, PersistentLogDAO.WARN, "There are no matching datasets for test %s (%d)", config.test.name,
                    config.test.id);
        }
        List<Integer> datasetIds = new ArrayList<>(list.size());
        if (nullOrEmpty(config.filterFunction)) {
            StringBuilder debugList = new StringBuilder();
            for (Object[] row : list) {
                Integer datasetId = (Integer) row[0];
                int runId = (int) row[1];
                int ordinal = (int) row[2];
                if (debugList.length() != 0) {
                    debugList.append(", ");
                }
                debugList.append(runId).append('/').append(ordinal);
                if (row[3] != null && ((JsonNode) row[3]).asBoolean(false)) {
                    datasetIds.add(datasetId);
                } else {
                    debugList.append("(filtered, null dataset id, check for run without a schema)");
                }
            }
            log(report, PersistentLogDAO.DEBUG, "Datasets considered for report: %s", debugList);
        } else {
            executeInContext(config, context -> {
                StringBuilder debugList = new StringBuilder();
                for (Object[] row : list) {
                    Integer datasetId = (Integer) row[0];
                    int runId = (int) row[1];
                    int ordinal = (int) row[2];
                    String jsCode = buildCode(config.filterFunction, String.valueOf(row[3]));
                    if (debugList.length() != 0) {
                        debugList.append(", ");
                    }
                    debugList.append(runId).append('/').append(ordinal);
                    try {
                        org.graalvm.polyglot.Value value = context.eval("js", jsCode);
                        if (value.isBoolean()) {
                            if (value.asBoolean()) {
                                datasetIds.add(datasetId);
                            } else {
                                debugList.append("(filtered)");
                                Log.debugf("Dataset %d/%d (%d) filtered out, value: %s", runId, ordinal, datasetId, row[3]);

                            }
                        } else {
                            debugList.append("(filtered: not boolean)");
                            log(report, PersistentLogDAO.ERROR,
                                    "Report %s(%d) filter result for dataset %d/%d (%d) is not a boolean: %s. Offending code: <br><pre>%s</pre>",
                                    config.title, config.id, runId, ordinal, datasetId, value, jsCode);
                        }
                    } catch (PolyglotException e) {
                        debugList.append("(filtered: JS error)");
                        log(report, PersistentLogDAO.ERROR,
                                "Failed to run report %s(%d) filter function on dataset %d/%d (%d). Offending code: <br><pre>%s</pre>",
                                config.title, config.id, runId, ordinal, datasetId, jsCode);
                        Log.debug("Caused by exception", e);
                    }
                }
                log(report, PersistentLogDAO.DEBUG, "Datasets considered for report: %s", debugList);
            });
        }
        return datasetIds;
    }

    private void log(TableReportDAO report, int level, String msg, Object... args) {
        String message = args.length == 0 ? msg : msg.formatted(args);
        report.logs.add(new ReportLogDAO(report, level, message));
    }

    private String buildCode(String function, String param) {
        return "var __obj = " + param + ";\n" +
                "var __func = " + function + ";\n" +
                "__func(__obj)";
    }

    private void executeInContext(TableReportConfigDAO config, Consumer<Context> consumer) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("js").out(out).err(out).build()) {
            context.enter();
            try {
                consumer.accept(context);
            } finally {
                context.leave();
            }
        } finally {
            if (out.size() > 0) {
                Log.infof("Output while calculating data for report %s(%d): <pre>%s</pre>", config.title, config.id,
                        out.toString());
            }
        }
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    public void onTestDelete(int testId) {
        int changedRows = em.createNativeQuery("UPDATE tablereportconfig SET testid = NULL WHERE testid = ?")
                .setParameter(1, testId).executeUpdate();
        Log.infof("Disowned %d report configs as test (%d) was deleted", changedRows, testId);
    }
}
