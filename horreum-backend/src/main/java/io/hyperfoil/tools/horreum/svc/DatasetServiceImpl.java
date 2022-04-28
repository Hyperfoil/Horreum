package io.hyperfoil.tools.horreum.svc;

import java.sql.Timestamp;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.security.PermitAll;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;

import org.hibernate.query.NativeQuery;
import org.hibernate.type.IntegerType;
import org.hibernate.type.TextType;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType;

import io.hyperfoil.tools.horreum.api.DatasetService;
import io.hyperfoil.tools.horreum.api.QueryResult;
import io.hyperfoil.tools.horreum.entity.alerting.DatasetLog;
import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.entity.json.Label;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.hyperfoil.tools.horreum.server.WithToken;
import io.quarkus.runtime.Startup;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.eventbus.EventBus;

@ApplicationScoped
@Startup
public class DatasetServiceImpl implements DatasetService {
   private static final Logger log = Logger.getLogger(DatasetServiceImpl.class);

   //@formatter:off
   private static final String LABEL_QUERY =
         "WITH used_labels AS (" +
            "SELECT le.label_id, label.name, ds.schema_id, count(le) > 1 AS multi FROM dataset_schemas ds " +
            "JOIN label ON label.schema_id = ds.schema_id " +
            "JOIN label_extractors le ON le.label_id = label.id " +
            "WHERE ds.dataset_id = ?1 AND (?2 < 0 OR label.id = ?2) GROUP BY le.label_id, label.name, ds.schema_id" +
         "), lvalues AS (" +
            "SELECT le.label_id, le.name, (CASE WHEN le.isarray THEN " +
                  "jsonb_path_query_array(dataset.data -> ds.index, le.jsonpath::::jsonpath) " +
               "ELSE " +
                  "jsonb_path_query_first(dataset.data -> ds.index, le.jsonpath::::jsonpath) " +
               "END) AS value " +
            "FROM dataset JOIN dataset_schemas ds ON dataset.id = ds.dataset_id " +
            "JOIN used_labels ul ON ul.schema_id = ds.schema_id " +
            "JOIN label_extractors le ON ul.label_id = le.label_id " +
            "WHERE dataset.id = ?1" +
         ") SELECT lvalues.label_id, ul.name, function, (CASE WHEN ul.multi THEN jsonb_object_agg(lvalues.name, lvalues.value) " +
            "ELSE jsonb_agg(lvalues.value) -> 0 END) AS value FROM label " +
            "JOIN lvalues ON lvalues.label_id = label.id " +
            "JOIN used_labels ul ON label.id = ul.label_id " +
            "GROUP BY lvalues.label_id, ul.name, function, ul.multi";
   private static final String LIST_TEST_DATASETS =
         "WITH schema_agg AS (" +
            "SELECT dataset_id, jsonb_agg(uri) as schemas FROM dataset_schemas ds JOIN dataset ON dataset.id = ds.dataset_id " +
            "WHERE testid = ?1 GROUP BY dataset_id" +
         ") SELECT ds.id, ds.runid, ds.ordinal, ds.testid, test.name, ds.description, ds.start, ds.stop, ds.owner, ds.access, " +
            "dv.value::::text as view, schema_agg.schemas::::text as schemas " +
            "FROM dataset ds LEFT JOIN test ON test.id = ds.testid " +
            "LEFT JOIN schema_agg ON schema_agg.dataset_id = ds.id " +
            "LEFT JOIN dataset_view dv ON dv.dataset_id = ds.id AND dv.view_id = defaultview_id " +
            "WHERE testid = ?1";
   //@formatter:on

   @Inject
   EntityManager em;

   @Inject
   SqlServiceImpl sqlService;

   @Inject
   TransactionManager tm;

   @Inject
   EventBus eventBus;

   @PostConstruct
   void init() {
      sqlService.registerListener("calculate_labels", this::onLabelChanged);
   }

   @PermitAll
   @WithRoles
   @Override
   public DatasetService.DatasetList listTestDatasets(int testId, Integer limit, Integer page, String sort, String direction) {
      StringBuilder sql = new StringBuilder(LIST_TEST_DATASETS);
      // TODO: filtering by fingerprint
      if (sort != null && sort.startsWith("view_data:")) {
         String[] parts = sort.split(":", 3);
         String vcid = parts[1];
         String label = parts[2];
         sql.append(" ORDER BY");
         // TODO: use view ID in the sort format rather than wildcards below
         // prefer numeric sort
         sql.append(" to_double(dv.value->'").append(vcid).append("'->>'").append(label).append("')");
         Util.addDirection(sql, direction);
         sql.append(", dv.value->'").append(vcid).append("'->>'").append(label).append("'");
         Util.addDirection(sql, direction);
      } else {
         Util.addOrderBy(sql, sort, direction);
      }
      Util.addLimitOffset(sql, limit, page);
      @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery(sql.toString())
            .setParameter(1, testId).getResultList();
      DatasetService.DatasetList list = new DatasetService.DatasetList();
      for (Object[] row : rows) {
         DatasetService.DatasetSummary summary = new DatasetService.DatasetSummary();
         summary.id = (Integer) row[0];
         summary.runId = (Integer) row[1];
         summary.ordinal = (Integer) row[2];
         summary.testId = (Integer) row[3];
         summary.testname = (String) row[4];
         summary.description = (String) row[5];
         summary.start = ((Timestamp) row[6]).getTime();
         summary.stop = ((Timestamp) row[7]).getTime();
         summary.owner = (String) row[8];
         summary.access = (Integer) row[9];
         summary.view = (ObjectNode) Util.toJsonNode((String) row[10]);
         summary.schemas = (ArrayNode) Util.toJsonNode((String) row[11]);
         // TODO: all the 'views'
         list.datasets.add(summary);
      }

      list.total = DataSet.count("testid = ?1", testId);
      return list;
   }

   @WithRoles
   @Override
   public QueryResult queryDataSet(Integer datasetId, String jsonpath, boolean array, String schemaUri) {
      if (schemaUri != null && schemaUri.isBlank()) {
         schemaUri = null;
      }
      QueryResult result = new QueryResult();
      result.jsonpath = jsonpath;
      try {
         if (schemaUri == null) {
            String func = array ? "jsonb_path_query_array" : "jsonb_path_query_first";
            String sqlQuery = "SELECT " + func + "(data, ?::::jsonpath)#>>'{}' FROM dataset WHERE id = ?";
            result.value = String.valueOf(Util.runQuery(em, sqlQuery, jsonpath, datasetId));
         } else {
            // This schema-aware query already assumes that DataSet.data is an array of objects with defined schema
            String schemaQuery = "jsonb_path_query(data, '$[*] ? (@.\"$schema\" == $schema)', ('{\"schema\":\"' || ? || '\"}')::::jsonb)";
            String sqlQuery;
            if (!array) {
               sqlQuery = "SELECT jsonb_path_query_first(" + schemaQuery + ", ?::::jsonpath)#>>'{}' FROM dataset WHERE id = ? LIMIT 1";
            } else {
               sqlQuery = "SELECT jsonb_agg(v)#>>'{}' FROM (SELECT jsonb_path_query(" + schemaQuery + ", ?::::jsonpath) AS v FROM dataset WHERE id = ?) AS values";
            }
            result.value = String.valueOf(Util.runQuery(em, sqlQuery, schemaUri, jsonpath, datasetId));
         }
         result.valid = true;
      } catch (PersistenceException pe) {
         SqlServiceImpl.setFromException(pe, result);
      }
      return result;
   }

   @WithRoles
   @Override
   public DatasetService.DatasetList listDatasetsBySchema(String uri, Integer limit, Integer page, String sort, String direction) {
      // TODO
      DatasetService.DatasetList list = new DatasetService.DatasetList();
      return list;
   }

   @WithToken
   @WithRoles
   @Override
   public DataSet getDataSet(Integer datasetId) {
      return DataSet.findById(datasetId);
   }

   private void onLabelChanged(String param) {
      String[] parts = param.split(";");
      if (parts.length != 2) {
         log.errorf("Invalid parameter to onLabelChanged: %s", param);
         return;
      }
      int datasetId = Integer.parseInt(parts[0]);
      int labelId = Integer.parseInt(parts[1]);
      calculateLabels(datasetId, labelId);
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   void calculateLabels(int datasetId, int queryLabelId) {
      log.infof("Calculating labels for dataset %d, label %d", datasetId, queryLabelId);
      // Note: we are fetching even labels that are marked as private/could be otherwise inaccessible
      // to the uploading user. However, the uploader should not have rights to fetch these anyway...
      @SuppressWarnings("unchecked") List<Object[]> extracted =
            (List<Object[]>) em.createNativeQuery(LABEL_QUERY)
                  .setParameter(1, datasetId)
                  .setParameter(2, queryLabelId)
                  .unwrap(NativeQuery.class)
                  .addScalar("label_id", IntegerType.INSTANCE)
                  .addScalar("name", TextType.INSTANCE)
                  .addScalar("function", TextType.INSTANCE)
                  .addScalar("value", JsonNodeBinaryType.INSTANCE)
                  .getResultList();
      Util.evaluateMany(extracted, row -> (String) row[2], row -> (JsonNode) row[3],
            (row, result) -> createLabel(datasetId, (int) row[0], Util.convertToJson(result)),
            row -> createLabel(datasetId, (int) row[0], (JsonNode) row[3]),
            (row, e, jsCode) -> logMessage(datasetId, DatasetLog.ERROR,
                  "Evaluation of label %s failed: '%s' Code:<pre>%s</pre>", row[0], e.getMessage(), jsCode),
            out -> logMessage(datasetId, DatasetLog.DEBUG, "Output while calculating labels: <pre>%s</pre>", out));
      Util.publishLater(tm, eventBus, DataSet.EVENT_LABELS_UPDATED, new DataSet.LabelsUpdatedEvent(datasetId));
   }

   private void createLabel(int datasetId, int labelId, JsonNode value) {
      Label.Value labelValue = new Label.Value();
      labelValue.datasetId = datasetId;
      labelValue.labelId = labelId;
      labelValue.value = value;
      labelValue.persist();
   }

   @ConsumeEvent(value = DataSet.EVENT_NEW, blocking = true)
   public void onNewDataset(DataSet dataSet) {
      calculateLabels(dataSet.id, -1);
   }

   private void logMessage(int datasetId, int level, String message, Object... params) {
      String msg = String.format(message, params);
      if (level == DatasetLog.ERROR) {
         log.errorf("Calculating labels for DS %d: %s", datasetId, msg);
      }
      // TODO log in DB
   }

   @Transactional(Transactional.TxType.REQUIRES_NEW)
   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   void logCalculation(int severity, int runId, String message) {
      // TODO: split log for datasets and runs?
//      Run run = Run.findById(runId);
//      if (run == null) {
//         log.errorf("Cannot find run %d! Cannot log message : %s", runId, message);
//      } else {
//         new CalculationLog(em.getReference(Test.class, run.testid), em.getReference(Run.class, run.id), severity, "tags", message).persistAndFlush();
//      }
   }
}
