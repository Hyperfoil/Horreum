package io.hyperfoil.tools.horreum.svc;

import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.hibernate.Hibernate;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.IntegerType;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType;

import io.hyperfoil.tools.horreum.api.ReportService;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.entity.report.ReportComment;
import io.hyperfoil.tools.horreum.entity.report.ReportComponent;
import io.hyperfoil.tools.horreum.entity.report.TableReport;
import io.hyperfoil.tools.horreum.entity.report.TableReportConfig;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.ConsumeEvent;


@WithRoles
public class ReportServiceImpl implements ReportService {
   private static final Logger log = Logger.getLogger(ReportServiceImpl.class);

   private static final Comparator<TableReportSummaryItem> REPORT_COMPARATOR = Comparator.<TableReportSummaryItem, Instant>comparing(item -> item.created).reversed();

   //@formatter:on

   static {
      System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
   }

   @Inject
   SecurityIdentity identity;

   @Inject
   EntityManager em;

   @PermitAll
   @Override
   public AllTableReports getTableReports(Integer testId, String roles, Integer limit, Integer page, String sort, Sort.Direction direction) {
      StringBuilder queryBuilder = new StringBuilder();
      Map<String, Object> params = new HashMap<>();
      if (testId != null) {
         queryBuilder.append("config.test.id = :test");
         params.put("test", testId);
      }
      Set<String> rolesList = Roles.expandRoles(roles, identity);
      if (rolesList != null) {
         if (queryBuilder.length() > 0) {
            queryBuilder.append(" AND ");
         }
         queryBuilder.append(" config.test.owner IN :roles");
         params.put("roles", rolesList);
      }
      String query = queryBuilder.toString();
      PanacheQuery<TableReport> pQuery = TableReport.find(query, params);
      if (page != null && limit != null) {
         pQuery.page(page - 1, limit);
      }
      AllTableReports result = new AllTableReports();
      result.count = TableReportConfig.count(query, params);
      List<TableReport> reports = pQuery.list();
      Map<Map.Entry<Integer, String>, TableReportSummary> summaryLookup = new HashMap<>();
      for (TableReport report : reports) {
         int summaryTestId = report.config.test == null ? -1 : report.config.test.id;
         TableReportSummaryItem item = new TableReportSummaryItem();
         item.id = report.id;
         item.configId = report.config.id;
         item.created = report.created;
         TableReportSummary summary = summaryLookup.computeIfAbsent(Map.entry(summaryTestId, report.config.title), k -> {
            TableReportSummary newSummary = new TableReportSummary();
            newSummary.testId = summaryTestId;
            if (report.config.test != null) {
               newSummary.testName = report.config.test.name;
            }
            newSummary.title = report.config.title;
            newSummary.reports = new ArrayList<>();
            return newSummary;
         });
         summary.reports.add(item);
      }
      result.reports = new ArrayList<>(summaryLookup.values());
      result.reports.forEach(summary -> summary.reports.sort(REPORT_COMPARATOR));
      Comparator<TableReportSummary> comparator;
      switch (sort) {
         case "title":
         default:
            comparator = Comparator.comparing(s -> s.title);
            break;
         case "testname":
            comparator = Comparator.comparing(s -> s.testName != null ? s.testName : "");
            break;
         case "last report":
            comparator = Comparator.comparing(s -> s.reports.get(0).created);
            break;
         case "report count":
            comparator = Comparator.comparing(s -> s.reports.size());
      }
      if (direction == Sort.Direction.Descending) {
         comparator = comparator.reversed();
      }
      result.reports.sort(comparator);
      return result;
   }

   @PermitAll
   @Override
   public TableReportConfig getTableReportConfig(Integer id) {
      return TableReportConfig.findById(id);
   }

   @RolesAllowed(Roles.TESTER)
   @Override
   @Transactional
   public TableReport updateTableReportConfig(TableReportConfig config, Integer reportId) {
      if (config.id != null && config.id < 0) {
         config.id = null;
      }
      boolean createNewConfig = reportId == null || reportId < 0;
      if (createNewConfig) {
         // We are going to create a new report, therefore we'll use a new config
         config.id = null;
      }
      for (var component : config.components) {
         if (component.id != null && component.id < 0 || createNewConfig) {
            component.id = null;
         }
      }
      validateTableConfig(config);
      config.ensureLinked();
      TableReport report = createTableReport(config, reportId);
      if (config.id == null) {
         config.persist();
      } else {
         TableReportConfig original = TableReportConfig.findById(config.id);
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
      return report;
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
   @Override
   public TableReport getTableReport(Integer id) {
      TableReport report = TableReport.findById(id);
      Hibernate.initialize(report.config);
      return report;
   }

   @RolesAllowed(Roles.TESTER)
   @Transactional
   @Override
   public void deleteTableReport(Integer id) {
      TableReport report = TableReport.findById(id);
      if (report == null) {
         throw ServiceException.notFound("Report " + id + " does not exist.");
      }
      report.delete();
      report.config.delete();
   }

   @RolesAllowed(Roles.TESTER)
   @Override
   @Transactional
   public ReportComment updateComment(Integer reportId, ReportComment comment) {
      if (comment.id == null || comment.id < 0) {
         comment.id = null;
         comment.report = TableReport.findById(reportId);
         if (comment.comment != null && !comment.comment.isEmpty()) {
            comment.persistAndFlush();
         }
      } else if (nullOrEmpty(comment.comment)){
         ReportComment.deleteById(comment.id);
         return null;
      } else {
         comment.report = TableReport.findById(reportId);
         em.merge(comment);
      }
      return comment;
   }

   @PermitAll
   @Override
   public TableReport previewTableReport(TableReportConfig config, Integer reportId) {
      validateTableConfig(config);
      return createTableReport(config, reportId);
   }

   private TableReport createTableReport(TableReportConfig config, Integer reportId) {
      Integer testId = config.test.id;
      Test test = Test.findById(testId);
      if (test == null) {
         throw ServiceException.badRequest("Cannot find test with ID " + testId);
      } else {
         // We don't assign the full test because then we'd serialize the complete object...
         config.test.name = test.name;
      }
      TableReport report;
      if (reportId == null) {
         report = new TableReport();
         report.comments = Collections.emptyList();
         report.created = Instant.now();
      } else {
         report = TableReport.findById(reportId);
         if (report == null) {
            throw ServiceException.badRequest("Cannot find report ID " + reportId);
         }
      }
      report.config = config;
      List<Object[]> categories = Collections.emptyList(), series, scales = Collections.emptyList();
      Query timestampQuery;
      if (!nullOrEmpty(config.filterLabels)) {
         List<Integer> datasetIds = filterDatasetIds(config);
         log.debugf("Table report %s(%d) includes datasets %s", config.title, config.id, datasetIds);
         series = selectByDatasets(config.seriesLabels, datasetIds);
         log.debugf("Series: %s", series.stream().collect(Collectors.toMap(row -> row[0], row -> row[3])));
         if (!nullOrEmpty(config.scaleLabels)) {
            scales = selectByDatasets(config.scaleLabels, datasetIds);
            log.debugf("Scales: %s", scales.stream().collect(Collectors.toMap(row -> row[0], row -> row[3])));
         }
         if (!nullOrEmpty(config.categoryLabels)) {
            categories = selectByDatasets(config.categoryLabels, datasetIds);
            log.debugf("Categories: %s", categories.stream().collect(Collectors.toMap(row -> row[0], row -> row[3])));
         }
         timestampQuery = em.createNativeQuery("SELECT id, start FROM dataset WHERE id IN :datasets").setParameter("datasets", datasetIds);
      } else {
         log.debugf("Table report %s(%d) includes all datasets for test %s(%d)", config.title, config.id, config.test.name, config.test.id);
         series = selectByTest(config.test.id, config.seriesLabels);
         log.debugf("Series: %s", series.stream().collect(Collectors.toMap(row -> row[0], row -> row[3])));
         if (!nullOrEmpty(config.scaleLabels)) {
            scales = selectByTest(config.test.id, config.scaleLabels);
            log.debugf("Scales: %s", scales.stream().collect(Collectors.toMap(row -> row[0], row -> row[3])));
         }
         if (!nullOrEmpty(config.categoryLabels)) {
            categories = selectByTest(config.test.id, config.categoryLabels);
            log.debugf("Categories: %s", categories.stream().collect(Collectors.toMap(row -> row[0], row -> row[3])));
         }
         timestampQuery = em.createNativeQuery("SELECT id, start FROM run WHERE testid = ?").setParameter(1, config.test.id);
      }
      if (categories.isEmpty() && !series.isEmpty()) {
         assert config.categoryLabels == null;
         assert config.categoryFunction == null;
         assert config.categoryFormatter == null;
         categories = series.stream().map(row -> new Object[]{ row[0], "" }).collect(Collectors.toList());
      }
      if (scales.isEmpty() && !series.isEmpty()) {
         assert config.scaleLabels == null;
         assert config.scaleFunction == null;
         assert config.scaleFormatter == null;
         scales = series.stream().map(row -> new Object[] { row[0], "" }).collect(Collectors.toList());
      }
      Map<Integer, TableReport.Data> datasetData = series.isEmpty() ? Collections.emptyMap() : getData(config, categories, series, scales);
      log.debugf("Data per dataset: %s", datasetData);

      @SuppressWarnings("unchecked")
      Map<Integer, Timestamp> timestamps = ((Stream<Object[]>) timestampQuery.getResultStream())
            .collect(Collectors.toMap(row -> (Integer) row[0], row -> (Timestamp) row[1]));
      // TODO: customizable time range
      List<Integer> datasetIds = getFinalDatasetIds(timestamps, datasetData);
      List<List<Object[]>> values = config.components.stream()
            .map(component -> selectByDatasets(component.labels, datasetIds))
            .collect(Collectors.toList());
      executeInContext(config, context -> {
         for (int i = 0; i < values.size(); i++) {
            List<Object[]> valuesForComponent = values.get(i);
            ReportComponent component = config.components.get(i);
            for (Object[] row : valuesForComponent) {
               Integer datasetId = (Integer) row[0];
               JsonNode value = (JsonNode) row[3];
               TableReport.Data data = datasetData.get(datasetId);
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
                     Double maybeDouble = Util.toDoubleOrNull(calculatedValue, err -> {}, info -> {});
                     if (maybeDouble != null) {
                        data.values.add(maybeDouble);
                     } else {
                        data.values.add(Util.convertToJson(calculatedValue));
                     }
                  } catch (PolyglotException e) {
                     log.errorf(e, "Failed to run report %s(%d) label function on run %d.", config.title, config.id, datasetId);
                     log.infof("Offending code: %s", jsCode);
                  }
               }
            }
         }
      });
      report.data = datasetIds.stream().map(datasetData::get).collect(Collectors.toList());
      return report;
   }

   private boolean nullOrEmpty(String str) {
      return str == null || str.trim().isEmpty();
   }

   private boolean nullOrEmpty(JsonNode node) {
      return node == null || node.isNull() || node.isEmpty();
   }

   private Map<Integer, TableReport.Data> getData(TableReportConfig config, List<Object[]> categories, List<Object[]> series, List<Object[]> scales) {
      assert !categories.isEmpty();
      assert !series.isEmpty();
      assert !scales.isEmpty();

      Map<Integer, TableReport.Data> datasetData = new HashMap<>();
      executeInContext(config, context -> {
         for (Object[] row : categories) {
            Integer datasetId = (Integer) row[0];
            int runId = (int) row[1];
            int ordinal = (int) row[2];
            JsonNode value = (JsonNode) row[3];
            TableReport.Data data = new TableReport.Data();
            data.datasetId = datasetId;
            data.values = JsonNodeFactory.instance.arrayNode(config.components.size());
            if (nullOrEmpty(config.categoryFunction)) {
               data.category = toText(value);
            } else {
               String jsCode = buildCode(config.categoryFunction, String.valueOf(value));
               try {
                  data.category = Util.convert(context.eval("js", jsCode)).toString();
               } catch (PolyglotException e) {
                  log.errorf(e, "Failed to run report %s(%d) category function on dataset %d/%d (%d).", config.title, config.id, runId, ordinal + 1, datasetId);
                  log.infof("Offending code: %s", jsCode);
                  continue;
               }
            }
            datasetData.put(datasetId, data);
         }
         for (Object[] row: series) {
            Integer datasetId = (Integer) row[0];
            int runId = (int) row[1];
            int ordinal = (int) row[2];
            JsonNode value = (JsonNode) row[3];
            TableReport.Data data = datasetData.get(datasetId);
            if (nullOrEmpty(config.seriesFunction)) {
               data.series = toText(value);
            } else {
               String jsCode = buildCode(config.seriesFunction, String.valueOf(value));
               try {
                  if (data != null) {
                     data.series = Util.convert(context.eval("js", jsCode)).toString();
                  }
               } catch (PolyglotException e) {
                  log.errorf(e, "Failed to run report %s(%d) series function on run %d/%d (%d).", config.title, config.id, runId, ordinal + 1, datasetId);
                  log.infof("Offending code: %s", jsCode);
               }
            }
         }
         for (Object[] row: scales) {
            Integer datasetId = (Integer) row[0];
            int runId = (int) row[1];
            int ordinal = (int) row[2];
            JsonNode value = (JsonNode) row[3];
            TableReport.Data data = datasetData.get(datasetId);
            if (nullOrEmpty(config.scaleFunction)) {
               data.scale = toText(value);
            } else {
               String jsCode = buildCode(config.scaleFunction, String.valueOf(value));
               try {
                  if (data != null) {
                     data.scale = Util.convert(context.eval("js", jsCode)).toString();
                  }
               } catch (PolyglotException e) {
                  log.errorf(e, "Failed to run report %s(%d) label function on dataset %d/%d (%d).", config.title, config.id, runId, ordinal + 1, datasetId);
                  log.infof("Offending code: %s", jsCode);
               }
            }
         }
      });
      return datasetData;
   }

   private String toText(JsonNode value) {
      return value == null ? null : value.isTextual() ? value.asText() : value.toString();
   }

   private List<Integer> getFinalDatasetIds(Map<Integer, Timestamp> timestamps, Map<Integer, TableReport.Data> datasetData) {
      Map<Coords, TableReport.Data> dataByCoords = new HashMap<>();
      for (TableReport.Data data : datasetData.values()) {
         Timestamp dataTimestamp = timestamps.get(data.datasetId);
         if (dataTimestamp == null) {
            log.errorf("No timestamp for dataset %d", data.datasetId);
            continue;
         }
         Coords coords = new Coords(data.category, data.series, data.scale);
         TableReport.Data prev = dataByCoords.get(coords);
         if (prev == null) {
            dataByCoords.put(coords, data);
            continue;
         }
         Timestamp prevTimestamp = timestamps.get(prev.datasetId);
         if (prevTimestamp == null) {
            log.errorf("No timestamp for prev dataset %d", prev.datasetId);
            dataByCoords.put(coords, data);
         } else if (prevTimestamp.before(dataTimestamp)) {
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
      sql.append("JOIN label ON label.id = label_id WHERE dataset_id IN (SELECT id FROM ds) AND json_contains(:labels, label.name)) ");
      sql.append("SELECT id, runid, ordinal, ");
      if (labels.size() != 1) {
         sql.append("jsonb_object_agg(values.name, values.value)");
      } else {
         sql.append("values.value");
      }
      sql.append(" AS value FROM ds LEFT JOIN values ON ds.id = values.dataset_id");
      if (labels.size() != 1) {
         sql.append(" GROUP BY id, runid, ordinal");
      }
      Query query = em.createNativeQuery(sql.toString())
            .setParameter("testid", testId)
            .unwrap(NativeQuery.class)
            .setParameter("labels", labels, JsonNodeBinaryType.INSTANCE)
            .addScalar("id", IntegerType.INSTANCE)
            .addScalar("runid", IntegerType.INSTANCE)
            .addScalar("ordinal", IntegerType.INSTANCE)
            .addScalar("value", JsonNodeBinaryType.INSTANCE);
      //noinspection unchecked
      return query.getResultList();
   }

   private List<Object[]> selectByDatasets(ArrayNode labels, List<Integer> datasets) {
      StringBuilder sql = new StringBuilder("SELECT dataset.id AS id, dataset.runid AS runid, dataset.ordinal AS ordinal, ");
      if (labels.size() != 1) {
         sql.append("jsonb_object_agg(label.name, lv.value)");
      } else {
         sql.append("lv.value");
      }
      sql.append(" AS value FROM label ")
         .append("JOIN label_values lv ON label.id = lv.label_id ")
         .append("JOIN dataset ON dataset.id = lv.dataset_id ")
         .append("WHERE dataset.id IN :datasets AND json_contains(:labels, label.name) ");
      if (labels.size() != 1) {
         sql.append("GROUP BY dataset.id, dataset.runid, dataset.ordinal");
      }
      Query query = em.createNativeQuery(sql.toString())
            .setParameter("datasets", datasets)
            .unwrap(NativeQuery.class)
            .setParameter("labels", labels, JsonNodeBinaryType.INSTANCE)
            .addScalar("id", IntegerType.INSTANCE)
            .addScalar("runid", IntegerType.INSTANCE)
            .addScalar("ordinal", IntegerType.INSTANCE)
            .addScalar("value", JsonNodeBinaryType.INSTANCE);
      //noinspection unchecked
      return (List<Object[]>) query.getResultList();
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
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         Coords coords = (Coords) o;
         return Objects.equals(category, coords.category) && Objects.equals(series, coords.series) && Objects.equals(labels, coords.labels);
      }

      @Override
      public int hashCode() {
         return Objects.hash(category, series, labels);
      }
   }

   private List<Integer> filterDatasetIds(TableReportConfig config) {
      List<Object[]> list = selectByTest(config.test.id, config.filterLabels);
      // TODO: if list is empty log warning to persistent log
      List<Integer> datasetIds = new ArrayList<>(list.size());
      if (nullOrEmpty(config.filterFunction)) {
         for (Object[] row : list) {
            Integer datasetId = (Integer) row[0];
            if (((JsonNode) row[3]).asBoolean(false)) {
               datasetIds.add(datasetId);
            }
         }
      } else {
         executeInContext(config, context -> {
            for (Object[] row : list) {
               Integer datasetId = (Integer) row[0];
               int runId = (int) row[1];
               int ordinal = (int) row[2];
               String jsCode = buildCode(config.filterFunction, String.valueOf(row[3]));
               try {
                  org.graalvm.polyglot.Value value = context.eval("js", jsCode);
                  // TODO debuggable
                  if (value.isBoolean()) {
                     if (value.asBoolean()) {
                        datasetIds.add(datasetId);
                     } else if (log.isDebugEnabled()) {
                        log.debugf("Dataset %d/%d (%d) filtered out, value: %s", runId, ordinal, datasetId, row[3]);
                     }
                  } else {
                     log.errorf("Report %s(%d) filter result for dataset %d/%d (%d) is not a boolean: %s", config.title, config.id, runId, ordinal + 1, datasetId, value);
                     log.infof("Offending code: %s", jsCode);
                  }
               } catch (PolyglotException e) {
                  log.errorf(e, "Failed to run report %s(%d) filter function on dataset %d/%d (%d).", config.title, config.id, runId, ordinal + 1, datasetId);
                  log.infof("Offending code: %s", jsCode);
               }
            }
         });
      }
      return datasetIds;
   }

   private String buildCode(String function, String param) {
      StringBuilder jsCode = new StringBuilder();
      jsCode.append("var __obj = ").append(param).append(";\n");
      jsCode.append("var __func = ").append(function).append(";\n");
      jsCode.append("__func(__obj)");
      return jsCode.toString();
   }

   private void executeInContext(TableReportConfig config, Consumer<Context> consumer) {
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
            log.infof("Output while calculating data for report %s(%d): <pre>%s</pre>", config.title, config.id, out.toString());
         }
      }
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @ConsumeEvent(value = Test.EVENT_DELETED, blocking = true)
   @Transactional
   public void onTestDelete(Test test) {
      int changedRows = em.createNativeQuery("UPDATE tablereportconfig SET testid = NULL WHERE testid = ?")
            .setParameter(1, test.id).executeUpdate();
      log.infof("Disowned %d report configs as test %s(%d) was deleted.", changedRows, test.name, test.id);
   }
}
