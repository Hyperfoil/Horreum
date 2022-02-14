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
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

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

   //@formatter:off
   private static final String SELECT_BY_ACCESSOR = "SELECT run.id, (" +
            "CASE WHEN :accessors LIKE '%[]' THEN " +
               "jsonb_path_query_array(run.data, (rs.prefix || se.jsonpath)::::jsonpath) " +
            "ELSE " +
               "jsonb_path_query_first(run.data, (rs.prefix || se.jsonpath)::::jsonpath) " +
            "END" +
         ")::::text AS result FROM schemaextractor se " +
         "JOIN run_schemas rs ON se.schema_id = rs.schemaid " +
         "JOIN run ON run.id = rs.runid " +
         "WHERE NOT run.trashed AND (se.accessor = :accessors OR (se.accessor || '[]') = :accessors) AND ";
   private static final String SELECT_BY_ACCESSORS = "SELECT run.id, jsonb_object_agg(se.accessor, (" +
            "CASE WHEN ca LIKE '%[]' THEN " +
               "jsonb_path_query_array(run.data, (rs.prefix || se.jsonpath)::::jsonpath) " +
            "ELSE " +
               "jsonb_path_query_first(run.data, (rs.prefix || se.jsonpath)::::jsonpath) " +
            "END)" +
         ")::::text AS result FROM schemaextractor se " +
         "JOIN run_schemas rs ON se.schema_id = rs.schemaid " +
         "JOIN run ON run.id = rs.runid " +
         "JOIN regexp_split_to_table(:accessors, ';') AS ca ON (se.accessor = ca OR (se.accessor || '[]') = ca) " +
         "WHERE NOT run.trashed AND ";
         // "WHERE xxx GROUP BY run.id " must be appended here
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
         throw ServiceException.badRequest("No test");
      }
      if (!nullOrEmpty(config.filterFunction) && nullOrEmpty(config.filterAccessors)) {
         throw ServiceException.badRequest("Filter function is defined but there are no accessors");
      }
      if (nullOrEmpty(config.categoryAccessors)) {
         if (!nullOrEmpty(config.categoryFunction)) {
            throw ServiceException.badRequest("Category function is defined but there are no accessors");
         } else if (!nullOrEmpty(config.categoryFormatter)) {
            throw ServiceException.badRequest("Category formatter is defined but there are not accessors");
         }
      }
      if (nullOrEmpty(config.seriesAccessors)) {
         throw ServiceException.badRequest("Service accessors must be defined");
      }
      if (nullOrEmpty(config.labelAccessors)) {
         if (!nullOrEmpty(config.labelFunction)) {
            throw ServiceException.badRequest("Label function is defined but there are no accessors");
         } else if (!nullOrEmpty(config.labelFormatter)) {
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
      List<Object[]> runCategories = Collections.emptyList(), series, labels = Collections.emptyList();
      Query timestampQuery;
      if (!nullOrEmpty(config.filterAccessors)) {
         List<Integer> runIds = filterRunIds(config);
         log.debugf("Table report %s(%d) includes runs %s", config.title, config.id, runIds);
         series = selectByRuns(config.seriesAccessors, runIds);
         log.debugf("Series: %s", series.stream().collect(Collectors.toMap(row -> row[0], row -> row[1])));
         if (!nullOrEmpty(config.labelAccessors)) {
            labels = selectByRuns(config.labelAccessors, runIds);
            log.debugf("Labels: %s", labels.stream().collect(Collectors.toMap(row -> row[0], row -> row[1])));
         }
         if (!nullOrEmpty(config.categoryAccessors)) {
            runCategories = selectByRuns(config.categoryAccessors, runIds);
            log.debugf("Categories: %s", runCategories.stream().collect(Collectors.toMap(row -> row[0], row -> row[1])));
         }
         timestampQuery = em.createNativeQuery("SELECT id, start FROM run WHERE id IN :runs").setParameter("runs", runIds);
      } else {
         log.debugf("Table report %s(%d) includes all runs for test %s(%d)", config.title, config.id, config.test.name, config.test.id);
         series = selectByTest(config.seriesAccessors, config.test.id);
         log.debugf("Series: %s", series.stream().collect(Collectors.toMap(row -> row[0], row -> row[1])));
         if (!nullOrEmpty(config.labelAccessors)) {
            labels = selectByTest(config.labelAccessors, config.test.id);
            log.debugf("Labels: %s", labels.stream().collect(Collectors.toMap(row -> row[0], row -> row[1])));
         }
         if (!nullOrEmpty(config.categoryAccessors)) {
            runCategories = selectByTest(config.categoryAccessors, config.test.id);
            log.debugf("Categories: %s", runCategories.stream().collect(Collectors.toMap(row -> row[0], row -> row[1])));
         }
         timestampQuery = em.createNativeQuery("SELECT id, start FROM run WHERE testid = ?").setParameter(1, config.test.id);
      }
      if (runCategories.isEmpty()) {
         assert config.categoryAccessors == null;
         assert config.categoryFunction == null;
         assert config.categoryFormatter == null;
         runCategories = series.stream().map(row -> new Object[]{ row[0], "" }).collect(Collectors.toList());
      }
      if (labels.isEmpty()) {
         assert config.labelAccessors == null;
         assert config.labelFunction == null;
         assert config.labelFormatter == null;
         labels = series.stream().map(row -> new Object[] { row[0], "" }).collect(Collectors.toList());
      }
      Map<Integer, TableReport.RunData> runData = getRunData(config, runCategories, series, labels);
      log.debugf("Run data: %s", runData);

      @SuppressWarnings("unchecked")
      Map<Integer, Timestamp> timestamps = ((Stream<Object[]>) timestampQuery.getResultStream())
            .collect(Collectors.toMap(row -> (Integer) row[0], row -> (Timestamp) row[1]));
      // TODO: customizable time range
      List<Integer> runIds = getFinalRunIds(timestamps, runData);
      List<List<Object[]>> values = config.components.stream()
            .map(component -> selectByRuns(component.accessors, runIds))
            .collect(Collectors.toList());
      executeInContext(config, context -> {
         for (int i = 0; i < values.size(); i++) {
            List<Object[]> valuesForComponent = values.get(i);
            ReportComponent component = config.components.get(i);
            for (Object[] row : valuesForComponent) {
               Integer runId = (Integer) row[0];
               TableReport.RunData data = runData.get(runId);
               if (nullOrEmpty(component.function)) {
                  if (row[1] == null) {
                     data.values.addNull();
                  } else {
                     Double dValue = Util.toDoubleOrNull(row[1]);
                     if (dValue != null) {
                        data.values.add(dValue);
                     } else {
                        data.values.add(String.valueOf(row[1]));
                     }
                  }
               } else {
                  String jsCode = buildCode(component.function, (String) row[1]);
                  try {
                     Value value = context.eval("js", jsCode);
                     Double maybeDouble = Util.toDoubleOrNull(value, err -> {}, info -> {});
                     if (maybeDouble != null) {
                        data.values.add(maybeDouble);
                     } else {
                        data.values.add(Util.convertToJson(value));
                     }
                  } catch (PolyglotException e) {
                     log.errorf(e, "Failed to run report %s(%d) label function on run %d.", config.title, config.id, runId);
                     log.infof("Offending code: %s", jsCode);
                  }
               }
            }
         }
      });
      report.runData = runIds.stream().map(runData::get).collect(Collectors.toList());
      return report;
   }

   private boolean nullOrEmpty(String str) {
      return str == null || str.trim().isEmpty();
   }

   private Map<Integer, TableReport.RunData> getRunData(TableReportConfig config, List<Object[]> runCategories, List<Object[]> series, List<Object[]> labels) {
      assert !runCategories.isEmpty();
      assert !series.isEmpty();
      assert !labels.isEmpty();

      Map<Integer, TableReport.RunData> runData = new HashMap<>();
      executeInContext(config, context -> {
         for (Object[] row : runCategories) {
            Integer runId = (Integer) row[0];
            TableReport.RunData data = new TableReport.RunData();
            data.runId = runId;
            data.values = JsonNodeFactory.instance.arrayNode(config.components.size());
            if (nullOrEmpty(config.categoryFunction)) {
               data.category = Util.unwrapDoubleQuotes((String) row[1]);
            } else {
               String jsCode = buildCode(config.categoryFunction, (String) row[1]);
               try {
                  data.category = Util.convert(context.eval("js", jsCode)).toString();
               } catch (PolyglotException e) {
                  log.errorf(e, "Failed to run report %s(%d) category function on run %d.", config.title, config.id, runId);
                  log.infof("Offending code: %s", jsCode);
                  continue;
               }
            }
            runData.put(runId, data);
         }
         for (Object[] row: series) {
            Integer runId = (Integer) row[0];
            TableReport.RunData data = runData.get(runId);
            if (nullOrEmpty(config.seriesFunction)) {
               data.series = Util.unwrapDoubleQuotes((String) row[1]);
            } else {
               String jsCode = buildCode(config.seriesFunction, (String) row[1]);
               try {
                  if (data != null) {
                     data.series = Util.convert(context.eval("js", jsCode)).toString();
                  }
               } catch (PolyglotException e) {
                  log.errorf(e, "Failed to run report %s(%d) series function on run %d.", config.title, config.id, runId);
                  log.infof("Offending code: %s", jsCode);
               }
            }
         }
         for (Object[] row: labels) {
            Integer runId = (Integer) row[0];
            TableReport.RunData data = runData.get(runId);
            if (nullOrEmpty(config.labelFunction)) {
               data.label = Util.unwrapDoubleQuotes((String) row[1]);
            } else {
               String jsCode = buildCode(config.labelFunction, (String) row[1]);
               try {
                  if (data != null) {
                     data.label = Util.convert(context.eval("js", jsCode)).toString();
                  }
               } catch (PolyglotException e) {
                  log.errorf(e, "Failed to run report %s(%d) label function on run %d.", config.title, config.id, runId);
                  log.infof("Offending code: %s", jsCode);
               }
            }
         }
      });
      return runData;
   }

   private List<Integer> getFinalRunIds(Map<Integer, Timestamp> timestamps, Map<Integer, TableReport.RunData> runData) {
      Map<Coords, TableReport.RunData> runsByCoords = new HashMap<>();
      for (TableReport.RunData data : runData.values()) {
         Timestamp dataTimestamp = timestamps.get(data.runId);
         if (dataTimestamp == null) {
            log.errorf("No timestamp for run %d", data.runId);
            continue;
         }
         Coords coords = new Coords(data.category, data.series, data.label);
         TableReport.RunData prev = runsByCoords.get(coords);
         if (prev == null) {
            runsByCoords.put(coords, data);
            continue;
         }
         Timestamp prevTimestamp = timestamps.get(prev.runId);
         if (prevTimestamp == null) {
            log.errorf("No timestamp for prev run %d", prev.runId);
            runsByCoords.put(coords, data);
         } else if (prevTimestamp.before(dataTimestamp)) {
            runsByCoords.put(coords, data);
         }
      }
      return runsByCoords.values().stream().map(data -> data.runId).collect(Collectors.toList());
   }

   private List<Object[]> selectByTest(String accessors, int testId) {
      List<Object[]> runCategories;
      String query;
      if (accessors.indexOf(';') >= 0) {
         query = SELECT_BY_ACCESSORS + "run.testid = :testid GROUP BY run.id";
      } else {
         query = SELECT_BY_ACCESSOR + "run.testid = :testid";
      }
      //noinspection unchecked
      runCategories = em
            .createNativeQuery(query)
            .setParameter("accessors", accessors)
            .setParameter("testid", testId)
            .getResultList();
      return runCategories;
   }

   private List<Object[]> selectByRuns(String accessors, List<Integer> runIds) {
      List<Object[]> runCategories;
      String query;
      if (accessors.indexOf(';') >= 0) {
         query = SELECT_BY_ACCESSORS + "run.id IN :runs GROUP BY run.id";
      } else {
         query = SELECT_BY_ACCESSOR + "run.id IN :runs";
      }
      //noinspection unchecked
      runCategories = em
            .createNativeQuery(query)
            .setParameter("accessors", accessors)
            // this was hitting some bug (?) in Hibernate when using positional parameters
            .setParameter("runs", runIds)
            .getResultList();
      return runCategories;
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

   private List<Integer> filterRunIds(TableReportConfig config) {
      List<Object[]> list = selectByTest(config.filterAccessors, config.test.id);
      List<Integer> runIds = new ArrayList<>(list.size());
      if (nullOrEmpty(config.filterFunction)) {
         for (Object[] row : list) {
            Integer runId = (Integer) row[0];
            String result = (String) row[1];
            if (result.equalsIgnoreCase("true")) {
               runIds.add(runId);
            }
         }
      } else {
         executeInContext(config, context -> {
            for (Object[] row : list) {
               Integer runId = (Integer) row[0];
               String jsCode = buildCode(config.filterFunction, (String) row[1]);
               try {
                  org.graalvm.polyglot.Value value = context.eval("js", jsCode);
                  // TODO debuggable
                  if (value.isBoolean()) {
                     if (value.asBoolean()) {
                        runIds.add(runId);
                     }
                  } else {
                     log.errorf("Report %s(%d) filter result for run %d is not a boolean: %s", config.title, config.id, runId, value);
                     log.infof("Offending code: %s", jsCode);
                  }
               } catch (PolyglotException e) {
                  log.errorf(e, "Failed to run report %s(%d) filter function on run %d.", config.title, config.id, runId);
                  log.infof("Offending code: %s", jsCode);
               }
            }
         });
      }
      return runIds;
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
