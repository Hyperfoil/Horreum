package io.hyperfoil.tools.horreum.svc;

import static io.hyperfoil.tools.horreum.entity.json.Schema.QUERY_1ST_LEVEL_BY_RUNID_TRANSFORMERID_SCHEMA_ID;
import static io.hyperfoil.tools.horreum.entity.json.Schema.QUERY_2ND_LEVEL_BY_RUNID_TRANSFORMERID_SCHEMA_ID;
import static io.hyperfoil.tools.horreum.entity.json.Schema.QUERY_TRANSFORMER_TARGETS;
import static com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;

import io.hyperfoil.tools.horreum.api.RunService;
import io.hyperfoil.tools.horreum.api.SqlService;
import io.hyperfoil.tools.horreum.entity.alerting.TransformationLog;
import io.hyperfoil.tools.horreum.entity.alerting.DatasetLog;
import io.hyperfoil.tools.horreum.entity.json.Access;
import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.entity.json.Label;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Schema;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.entity.json.Transformer;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.hyperfoil.tools.horreum.server.WithToken;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import io.quarkus.runtime.Startup;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.IntegerType;
import org.hibernate.type.TextType;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.persistence.TransactionRequiredException;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.InvalidTransactionException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.networknt.schema.ValidationMessage;
import com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType;

@ApplicationScoped
@Startup
@WithRoles
public class RunServiceImpl implements RunService {
   private static final Logger log = Logger.getLogger(RunServiceImpl.class);

   //@formatter:off
   private static final String FIND_AUTOCOMPLETE =
         "SELECT * FROM (" +
            "SELECT DISTINCT jsonb_object_keys(q) AS key " +
            "FROM run, jsonb_path_query(run.data, ? ::::jsonpath) q " +
            "WHERE jsonb_typeof(q) = 'object') AS keys " +
         "WHERE keys.key LIKE CONCAT(?, '%');";

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
   protected static final String FIND_RUNS_WITH_URI = "SELECT id FROM run WHERE data->>'$schema' = ?1 OR (" +
         "CASE WHEN jsonb_typeof(data) = 'object' THEN ?1 IN (SELECT values.value->>'$schema' FROM jsonb_each(data) as values) " +
         "WHEN jsonb_typeof(data) = 'array' THEN ?1 IN (SELECT jsonb_array_elements(data)->>'$schema') ELSE false END)";
   //@formatter:on
   private static final String[] CONDITION_SELECT_TERMINAL = { "==", "!=", "<>", "<", "<=", ">", ">=", " " };
   private static final String UPDATE_TOKEN = "UPDATE run SET token = ? WHERE id = ?";
   private static final String CHANGE_ACCESS = "UPDATE run SET owner = ?, access = ? WHERE id = ?";

   @Inject
   EntityManager em;

   @Inject
   SecurityIdentity identity;

   @Inject
   TransactionManager tm;

   @Inject
   SqlServiceImpl sqlService;

   @Inject
   Vertx vertx;

   @Inject
   EventBus eventBus;

   @Inject
   TestServiceImpl testService;

   @Inject
   SchemaServiceImpl schemaService;

   @Context HttpServletResponse response;

   @PostConstruct
   public void init() {
      sqlService.registerListener("calculate_datasets", this::onCalculateDataSets);
      sqlService.registerListener("calculate_labels", this::onLabelChanged);
      sqlService.registerListener("new_or_updated_schema", this::onNewOrUpdatedSchema);
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

   // We cannot run this without a transaction (to avoid timeout) because we have not request going on
   // and EM has to bind its lifecycle either to current request or transaction.
   @Transactional(Transactional.TxType.REQUIRES_NEW)
   @TransactionConfiguration(timeout = 3600) // 1 hour, this may run a long time
   void onNewOrUpdatedSchema(String schemaIdString) {
      int schemaId;
      try {
         schemaId = Integer.parseInt(schemaIdString);
      } catch (NumberFormatException e) {
         log.errorf("Cannot process schema add/update: invalid id %s", schemaIdString);
         return;
      }
      Schema schema = Schema.findById(schemaId);
      if (schema == null) {
         log.errorf("Cannot process schema add/update: cannot load schema %d", schemaId);
         return;
      }
      // we don't have to care about races with new runs
      ScrollableResults results = em.createNativeQuery(FIND_RUNS_WITH_URI)
            .setParameter(1, schema.uri)
            .unwrap(NativeQuery.class).setReadOnly(true).setFetchSize(100)
            .scroll(ScrollMode.FORWARD_ONLY);
      while (results.next()) {
         int runId = (int) results.get(0);
         log.infof("Recalculate DataSets for run %d - schema %d (%s) changed", runId, schema.id, schema.uri);
         Util.executeBlocking(vertx, CachedSecurityIdentity.ANONYMOUS, () -> onNewOrUpdatedSchemaForRun(runId));
      }
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   void onNewOrUpdatedSchemaForRun(int runId) {
      em.createNativeQuery("SELECT update_run_schemas(?1)::::text").setParameter(1, runId).getSingleResult();
      transform(runId);
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

   private Object runQuery(String query, Object... params) {
      Query q = em.createNativeQuery(query);
      for (int i = 0; i < params.length; ++i) {
         q.setParameter(i + 1, params[i]);
      }
      try {
         return q.getSingleResult();
      } catch (NoResultException e) {
         log.errorf("No results in %s with params: %s", query, Arrays.asList(params));
         throw ServiceException.notFound("No result");
      } catch (Throwable t) {
         log.errorf(t, "Query error in %s with params: %s", query, Arrays.asList(params));
         throw t;
      }
   }

   @PermitAll
   @WithToken
   @Override
   public Object getRun(Integer id, String token) {
      return runQuery("SELECT (to_jsonb(run) ||" +
            "jsonb_set('{}', '{schema}', (SELECT COALESCE(jsonb_object_agg(schemaid, uri), '{}') FROM run_schemas WHERE runid = run.id)::::jsonb, true) || " +
            "jsonb_set('{}', '{testname}', to_jsonb((SELECT name FROM test WHERE test.id = run.testid)), true) || " +
            "jsonb_set('{}', '{datasets}', (SELECT jsonb_agg(id ORDER BY id) FROM dataset WHERE runid = run.id), true)" +
            ")::::text FROM run where id = ?", id);
   }

   @Override
   public RunSummary getRunSummary(Integer id, String token) {
      // TODO: define the result set mapping properly without transforming jsonb and int[] to text
      Query query = em.createNativeQuery("SELECT run.id, run.start, run.stop, run.testid, " +
            "run.owner, run.access, run.token, run.trashed, run.description, " +
            "(SELECT name FROM test WHERE test.id = run.testid) as testname, " +
            "(SELECT COALESCE(jsonb_object_agg(schemaid, uri), '{}') FROM run_schemas WHERE runid = run.id)::::text as schema, " +
            "(SELECT json_agg(id ORDER BY id) FROM dataset WHERE runid = run.id)::::text as datasets " +
            " FROM run where id = ?").setParameter(1, id);
      query.unwrap(org.hibernate.query.Query.class);

      Object[] row = (Object[]) query.getSingleResult();
      RunSummary summary = new RunSummary();
      initSummary(row, summary);
      summary.schema = Util.toJsonNode((String) row[10]);
      summary.datasets = (ArrayNode) Util.toJsonNode((String) row[11]);
      return summary;
   }

   @PermitAll
   @WithToken
   @Override
   public Object getData(Integer id, String token) {
      return runQuery("SELECT data#>>'{}' from run where id = ?", id);
   }

   @PermitAll
   @WithToken
   @Override
   public QueryResult queryData(Integer id, String jsonpath, String schemaUri, boolean array) {
      String func = array ? "jsonb_path_query_array" : "jsonb_path_query_first";
      QueryResult result = new QueryResult();
      result.jsonpath = jsonpath;
      try {
         if (schemaUri != null && !schemaUri.isEmpty()) {
            jsonpath = jsonpath.trim();
            if (jsonpath.startsWith("$")) {
               jsonpath = jsonpath.substring(1);
            }
            String sqlQuery = "SELECT " + func + "(run.data, (rs.prefix || ?)::::jsonpath)#>>'{}' FROM run JOIN run_schemas rs ON rs.runid = run.id WHERE id = ? AND rs.uri = ?";
            result.value = String.valueOf(runQuery(sqlQuery, jsonpath, id, schemaUri));
         } else {
            String sqlQuery = "SELECT " + func + "(data, ?::::jsonpath)#>>'{}' FROM run WHERE id = ?";
            result.value = String.valueOf(runQuery(sqlQuery, jsonpath, id));
         }
         result.valid = true;
      } catch (PersistenceException pe) {
         SqlServiceImpl.setFromException(pe, result);
      }
      return result;
   }

   @RolesAllowed(Roles.TESTER)
   @Transactional
   @Override
   public String resetToken(Integer id) {
      return updateToken(id, Tokens.generateToken());
   }

   @RolesAllowed(Roles.TESTER)
   @Transactional
   @Override
   public String dropToken(Integer id) {
      return updateToken(id, null);
   }

   private String updateToken(Integer id, String token) {
      Query query = em.createNativeQuery(UPDATE_TOKEN);
      query.setParameter(1, token);
      query.setParameter(2, id);
      if (query.executeUpdate() != 1) {
         throw ServiceException.serverError("Token reset failed (missing permissions?)");
      } else {
         return token;
      }
   }

   @RolesAllowed(Roles.TESTER)
   @Transactional
   @Override
   // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
   public void updateAccess(Integer id, String owner, Access access) {
      Query query = em.createNativeQuery(CHANGE_ACCESS);
      query.setParameter(1, owner);
      query.setParameter(2, access.ordinal());
      query.setParameter(3, id);
      if (query.executeUpdate() != 1) {
         throw ServiceException.serverError("Access change failed (missing permissions?)");
      }
   }

   @PermitAll // all because of possible token-based upload
   @Transactional
   @WithToken
   @Override
   public String add(String testNameOrId, String owner, Access access, String token,
                     Run run) {
      if (owner != null) {
         run.owner = owner;
      }
      if (access != null) {
         run.access = access;
      }
      Test test = testService.getByNameOrId(testNameOrId);
      if (test == null) {
         throw ServiceException.serverError("Failed to find test " + testNameOrId);
      }
      run.testid = test.id;
      Integer runId = addAuthenticated(run, test);
      response.addHeader(HttpHeaders.LOCATION, "/run/" + runId);
      return String.valueOf(runId);
   }

   @PermitAll // all because of possible token-based upload
   @Transactional
   @WithToken
   @Override
   public String addRunFromData(String start, String stop, String test,
                                String owner, Access access, String token,
                                String schemaUri, String description,
                                JsonNode data) {
      if (data == null) {
         log.debugf("Failed to upload for test %s with description %s because of missing data.", test, description);
         throw ServiceException.badRequest("No data!");
      }
      Object foundTest = findIfNotSet(test, data);
      Object foundStart = findIfNotSet(start, data);
      Object foundStop = findIfNotSet(stop, data);
      Object foundDescription = findIfNotSet(description, data);

      if (schemaUri == null || schemaUri.isEmpty()) {
         JsonNode schemaNode = data.get("$schema");
         schemaUri = schemaNode == null ? null : schemaNode.asText();
      } else {
         if (data.isObject()) {
            ((ObjectNode) data).set("$schema", TextNode.valueOf(schemaUri));
         }
      }

      String testNameOrId = foundTest == null ? null : foundTest.toString().trim();
      if (testNameOrId == null || testNameOrId.isEmpty()) {
         log.debugf("Failed to upload for test %s with description %s as the test cannot be identified.", test, description);
         throw ServiceException.badRequest("Cannot identify test name.");
      }

      Instant startInstant = toInstant(foundStart);
      Instant stopInstant = toInstant(foundStop);
      if (startInstant == null) {
         log.debugf("Failed to upload for test %s with description %s; cannot parse start time %s (%s)", test, description, foundStart, start);
         throw ServiceException.badRequest("Cannot parse start time from " + foundStart + " (" + start + ")");
      } else if (stopInstant == null) {
         log.debugf("Failed to upload for test %s with description %s; cannot parse start time %s (%s)", test, description, foundStop,stop);
         throw ServiceException.badRequest("Cannot parse stop time from " + foundStop + " (" + stop + ")");
      }

      Test testEntity = testService.getByNameOrId(testNameOrId);
      if (testEntity == null) {
         log.debugf("Failed to upload for test %s with description %s as there is no such test.", test, description);
         throw ServiceException.serverError("Failed to find test " + testNameOrId);
      }

      Collection<ValidationMessage> validationErrors = schemaService.validate(data, schemaUri);
      if (validationErrors != null && !validationErrors.isEmpty()) {
         log.debugf("Failed to upload for test %s with description %s because of validation errors.", test, description);
         throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(validationErrors).build());
      }
      log.debugf("Creating new run for test %s(%d) with description %s", testEntity.name, testEntity.id, foundDescription);

      Run run = new Run();
      run.testid = testEntity.id;
      run.start = startInstant;
      run.stop = stopInstant;
      run.description = foundDescription != null ? foundDescription.toString() : null;
      run.data = data;
      run.owner = owner;
      run.access = access;
      // Some triggered functions in the database need to be able to read the just-inserted run
      // otherwise RLS policies will fail. That's why we reuse the token for the test and later wipe it out.
      run.token = token;

      Integer runId = addAuthenticated(run, testEntity);
      if (token != null) {
         // TODO: remove the token
      }
      response.addHeader(HttpHeaders.LOCATION, "/run/" + runId);
      return String.valueOf(runId);
   }

   private Object findIfNotSet(String value, JsonNode data) {
      if (value != null && !value.isEmpty()) {
         if (value.startsWith("$.")) {
            return Util.findJsonPath(data, value);
         } else {
            return value;
         }
      } else {
         return null;
      }
   }

   private Instant toInstant(Object time) {
      if (time == null) {
         return null;
      } else if (time instanceof Number) {
         return Instant.ofEpochMilli(((Number) time).longValue());
      } else {
         try {
            return Instant.ofEpochMilli(Long.parseLong((String) time));
         } catch (NumberFormatException e) {
            // noop
         }
         try {
            return Instant.parse(time.toString());
         } catch (DateTimeParseException e) {
            return null;
         }
      }
   }

   private Integer addAuthenticated(Run run, Test test) {
      // Id will be always generated anew
      run.id = null;

      if (run.owner == null) {
         List<String> uploaders = identity.getRoles().stream().filter(role -> role.endsWith("-uploader")).collect(Collectors.toList());
         if (uploaders.size() != 1) {
            log.debugf("Failed to upload for test %s: no owner, available uploaders: %s", test.name, uploaders);
            throw ServiceException.badRequest("Missing owner and cannot select single default owners; this user has these uploader roles: " + uploaders);
         }
         String uploader = uploaders.get(0);
         run.owner = uploader.substring(0, uploader.length() - 9) + "-team";
      } else if (!Objects.equals(test.owner, run.owner) && !identity.getRoles().contains(run.owner)) {
         log.debugf("Failed to upload for test %s: requested owner %s, available roles: %s", test.name, run.owner, identity.getRoles());
         throw ServiceException.badRequest("This user does not have permissions to upload run for owner=" + run.owner);
      }
      if (run.access == null) {
         run.access = Access.PRIVATE;
      }
      log.debugf("Uploading with owner=%s and access=%s", run.owner, run.access);

      try {
         if (run.id == null) {
            em.persist(run);
         } else {
            em.merge(run);
         }
         em.flush();
      } catch (Exception e) {
         log.error("Failed to persist run.", e);
         throw ServiceException.serverError("Failed to persist run");
      }
      log.debugf("Upload flushed, run ID %d", run.id);
      Util.publishLater(tm, eventBus, Run.EVENT_NEW, run);

      return run.id;
   }

   @PermitAll
   @WithToken
   @Override
   public List<String> autocomplete(String query) {
      if (query == null || query.isEmpty()) {
         return null;
      }
      String jsonpath = query.trim();
      String incomplete = "";
      if (jsonpath.endsWith(".")) {
         jsonpath = jsonpath.substring(0, jsonpath.length() - 1);
      } else {
         int lastDot = jsonpath.lastIndexOf('.');
         if (lastDot > 0) {
            incomplete = jsonpath.substring(lastDot + 1);
            jsonpath = jsonpath.substring(0, lastDot);
         } else {
            incomplete = jsonpath;
            jsonpath = "$.**";
         }
      }
      int conditionIndex = jsonpath.indexOf('@');
      if (conditionIndex >= 0) {
         int conditionSelectEnd = jsonpath.length();
         for (String terminal : CONDITION_SELECT_TERMINAL) {
            int ti = jsonpath.indexOf(terminal, conditionIndex + 1);
            if (ti >= 0) {
               conditionSelectEnd = Math.min(conditionSelectEnd, ti);
            }
         }
         String conditionSelect = jsonpath.substring(conditionIndex + 1, conditionSelectEnd);
         int queryIndex = jsonpath.indexOf('?');
         if (queryIndex < 0) {
            // This is a shortcut query '@.foo...'
            jsonpath = "$.**" + conditionSelect;
         } else if (queryIndex > conditionIndex) {
            // Too complex query with multiple conditions
            return Collections.emptyList();
         } else {
            while (queryIndex > 0 && Character.isWhitespace(jsonpath.charAt(queryIndex - 1))) {
               --queryIndex;
            }
            jsonpath = jsonpath.substring(0, queryIndex) + conditionSelect;
         }
      }
      if (!jsonpath.startsWith("$")) {
         jsonpath = "$.**." + jsonpath;
      }
      try {
         Query findAutocomplete = em.createNativeQuery(FIND_AUTOCOMPLETE);
         findAutocomplete.setParameter(1, jsonpath);
         findAutocomplete.setParameter(2, incomplete);
         @SuppressWarnings("unchecked")
         List<String> results = findAutocomplete.getResultList();
         return results.stream().map(option ->
               option.matches("^[a-zA-Z0-9_-]*$") ? option : "\"" + option + "\"")
               .collect(Collectors.toList());
      } catch (PersistenceException e) {
         throw ServiceException.badRequest("Failed processing query '" + query + "':\n" + e.getLocalizedMessage());
      }
   }

   @PermitAll
   @WithToken
   @Override
   public RunsSummary listAllRuns(String query, boolean matchAll, String roles, boolean trashed,
                                  Integer limit, Integer page, String sort, String direction) {
      StringBuilder sql = new StringBuilder("SELECT run.id, run.start, run.stop, run.testId, ")
         .append("run.owner, run.access, run.token, run.trashed, run.description, test.name AS testname ")
         .append("FROM run JOIN test ON test.id = run.testId WHERE ");
      String[] queryParts;
      boolean whereStarted = false;
      if (query == null || query.isEmpty()) {
         queryParts = new String[0];
      } else {
         query = query.trim();
         if (query.startsWith("$") || query.startsWith("@")) {
            queryParts = new String[] { query };
         } else {
            queryParts = query.split("([ \t\n,]+)|\\bOR\\b");
         }
         sql.append("(");
         for (int i = 0; i < queryParts.length; ++i) {
            if (i != 0) {
               sql.append(matchAll ? " AND " : " OR ");
            }
            sql.append("jsonb_path_exists(data, ?").append(i + 1).append(" ::::jsonpath)");
            if (queryParts[i].startsWith("$")) {
               // no change
            } else if (queryParts[i].startsWith("@")) {
               queryParts[i] = "$.** ? (" + queryParts[i] + ")";
            } else {
               queryParts[i] = "$.**." + queryParts[i];
            }
         }
         sql.append(")");
         whereStarted = true;
      }

      whereStarted = Roles.addRolesSql(identity, "run", sql, roles, queryParts.length + 1, whereStarted ? " AND" : null) || whereStarted;
      if (!trashed) {
         if (whereStarted) {
            sql.append(" AND ");
         }
         sql.append(" trashed = false ");
      }
      Util.addPaging(sql, limit, page, sort, direction);

      Query sqlQuery = em.createNativeQuery(sql.toString());
      for (int i = 0; i < queryParts.length; ++i) {
         sqlQuery.setParameter(i + 1, queryParts[i]);
      }

      Roles.addRolesParam(identity, sqlQuery, queryParts.length + 1, roles);

      try {
         @SuppressWarnings("unchecked")
         List<Object[]> runs = sqlQuery.getResultList();

         RunsSummary summary = new RunsSummary();
         // TODO: total does not consider the query but evaluating all the expressions would be expensive
         summary.total = trashed ? Run.count() : Run.count("trashed = false");
         summary.runs = runs.stream().map(row -> {
            RunSummary run = new RunSummary();
            initSummary(row, run);
            return run;
         }).collect(Collectors.toList());
         return summary;
      } catch (PersistenceException pe) {
         // In case of an error PostgreSQL won't let us execute another query in the same transaction
         try {
            Transaction old = tm.suspend();
            try {
               for (String jsonpath : queryParts) {
                  SqlService.JsonpathValidation result = sqlService.testJsonPathInternal(jsonpath);
                  if (!result.valid) {
                     throw new WebApplicationException(Response.status(400).entity(result).build());
                  }
               }
            } finally {
               tm.resume(old);
            }
         } catch (InvalidTransactionException | SystemException e) {
            // ignore
         }
         throw new WebApplicationException(pe, 500);
      }
   }

   private void initSummary(Object[] row, RunSummary run) {
      run.id = (int) row[0];
      run.start = ((Timestamp) row[1]).getTime();
      run.stop = ((Timestamp) row[2]).getTime();
      run.testid = (int) row[3];
      run.owner = (String) row[4];
      run.access = (int) row[5];
      run.token = (String) row[6];
      run.trashed = (boolean) row[7];
      run.description = (String) row[8];
      run.testname = (String) row[9];
   }

   @PermitAll
   @WithToken
   @Override
   public RunCounts runCount(Integer testId) {
      if (testId == null) {
         throw ServiceException.badRequest("Missing testId query param.");
      }
      RunCounts counts = new RunCounts();
      counts.total = Run.count("testid = ?1", testId);
      counts.active = Run.count("testid = ?1 AND trashed = false", testId);
      counts.trashed = counts.total - counts.active;
      return counts;
   }

   @PermitAll
   @WithToken
   @Override
   public TestRunsSummary listTestRuns(Integer testId, boolean trashed,
                                       Integer limit, Integer page, String sort, String direction) {
      StringBuilder sql = new StringBuilder("WITH schema_agg AS (")
            .append("    SELECT COALESCE(jsonb_object_agg(schemaid, uri), '{}') AS schemas, rs.runid FROM run_schemas rs GROUP BY rs.runid")
            .append("), dataset_agg AS (")
            .append("    SELECT runid, jsonb_agg(id ORDER BY id)::::text as datasets FROM dataset WHERE testid = ?1 GROUP BY runid")
            .append(") SELECT run.id, run.start, run.stop, run.access, run.owner, schema_agg.schemas::::text AS schemas, ")
            .append("run.trashed, run.description, COALESCE(dataset_agg.datasets, '[]') FROM run ")
            .append("LEFT JOIN schema_agg ON schema_agg.runid = run.id ")
            .append("LEFT JOIN dataset_agg ON dataset_agg.runid = run.id ")
            .append("WHERE run.testid = ?1 ");
      if (!trashed) {
         sql.append(" AND NOT run.trashed ");
      }
      Util.addOrderBy(sql, sort, direction);
      Util.addLimitOffset(sql, limit, page);
      Test test = Test.find("id", testId).firstResult();
      if (test == null) {
         throw ServiceException.notFound("Cannot find test ID " + testId);
      }
      Query query = em.createNativeQuery(sql.toString());
      query.setParameter(1, testId);
      @SuppressWarnings("unchecked")
      List<Object[]> resultList = query.getResultList();
      List<TestRunSummary> runs = new ArrayList<>();
      for (Object[] row : resultList) {
         TestRunSummary run = new TestRunSummary();
         run.id = (int) row[0];
         run.start = ((Timestamp) row[1]).getTime();
         run.stop = ((Timestamp) row[2]).getTime();
         run.testid = testId;
         run.access = (int) row[3];
         run.owner = (String) row[4];
         run.schema = Util.toJsonNode((String) row[5]);
         run.trashed = (boolean) row[6];
         run.description = (String) row[7];
         run.datasets = (ArrayNode) Util.toJsonNode((String) row[8]);
         runs.add(run);
      }
      TestRunsSummary summary = new TestRunsSummary();
      summary.total = trashed ? Run.count("testid = ?1", testId) : Run.count("testid = ?1 AND trashed = false", testId);
      summary.runs = runs;
      return summary;
   }

   @PermitAll
   @WithToken
   @Override
   public RunsSummary listBySchema(String uri, Integer limit, Integer page, String sort, String direction) {
      if (uri == null || uri.isEmpty()) {
         throw ServiceException.badRequest("No `uri` query parameter given.");
      }
      StringBuilder sql = new StringBuilder("SELECT run.id, run.start, run.stop, run.testId, ")
            .append("run.owner, run.access, run.token, test.name AS testname, run.description ")
            .append("FROM run_schemas rs JOIN run ON rs.runid = run.id JOIN test ON rs.testid = test.id ")
            .append("WHERE uri = ? AND NOT run.trashed");
      Util.addPaging(sql, limit, page, sort, direction);
      Query query = em.createNativeQuery(sql.toString());
      query.setParameter(1, uri);
      @SuppressWarnings("unchecked")
      List<Object[]> runs = query.getResultList();

      RunsSummary summary = new RunsSummary();
      summary.runs = runs.stream().map(row -> {
         RunSummary run = new RunSummary();
         run.id = (int) row[0];
         run.start = ((Timestamp) row[1]).getTime();
         run.stop = ((Timestamp) row[2]).getTime();
         run.testid = (int) row[3];
         run.owner = (String) row[4];
         run.access = (int) row[5];
         run.token = (String) row[6];
         run.testname = (String) row[7];
         run.description = (String) row[8];
         return run;
      }).collect(Collectors.toList());
      summary.total = ((BigInteger) em.createNativeQuery("SELECT count(*) FROM run_schemas WHERE uri = ?")
            .setParameter(1, uri).getSingleResult()).longValue();
      return summary;
   }

   @RolesAllowed(Roles.TESTER)
   @Transactional
   @Override
   public void trash(Integer id, Boolean isTrashed) {
      boolean trashed = isTrashed == null || isTrashed;
      updateRun(id, run -> run.trashed = trashed);
      if (trashed) {
         DataSet.delete("run.id", id);
         Util.publishLater(tm, eventBus, Run.EVENT_TRASHED, id);
      } else {
         transform(id);
      }
   }

   @RolesAllowed(Roles.TESTER)
   @Transactional
   @Override
   public void updateDescription(Integer id, String description) {
      // FIXME: fetchival stringifies the body into JSON string :-/
      updateRun(id, run -> run.description = Util.destringify(description));
   }

   public void updateRun(Integer id, Consumer<Run> consumer) {
      Run run = Run.findById(id);
      if (run == null) {
         throw ServiceException.notFound("Run not found");
      }
      consumer.accept(run);
      run.persistAndFlush();
   }

   @RolesAllowed(Roles.TESTER)
   @Transactional
   @Override
   public Object updateSchema(Integer id, String path, String schemaUri) {
      // FIXME: fetchival stringifies the body into JSON string :-/
      Run run = Run.findById(id);
      if (run == null) {
         throw ServiceException.notFound("Run not found.");
      }
      String uri = Util.destringify(schemaUri);
      // Triggering dirty property on Run
      JsonNode updated = run.data.deepCopy();
      ObjectNode item;
      if (updated.isObject()) {
         item = (ObjectNode) (path == null || path.isEmpty() ? updated : updated.get(path));
      } else if (updated.isArray()) {
         int index = path == null || path.isEmpty() ? 0 : Integer.parseInt(path);
         item = (ObjectNode) updated.get(index);
      } else {
         throw ServiceException.serverError("Cannot update run data with path " + path);
      }
      if (uri != null && !uri.isEmpty()) {
         item.set("$schema", new TextNode(uri));
      } else {
         item.remove("$schema");
      }
      run.data = updated;
      run.persist();
      Query query = em.createNativeQuery("SELECT COALESCE(jsonb_object_agg(schemaid, uri), '{}')::::text FROM run_schemas WHERE runid = ?");
      query.setParameter(1, run.id);
      Object schemas = query.getSingleResult();
      em.flush();
      return schemas;
   }

   @WithRoles
   @Transactional
   @Override
   public List<Integer> recalculateDatasets(int runId) {
      transform(runId);
      //noinspection unchecked
      return em.createNativeQuery("SELECT id FROM dataset WHERE runid = ? ORDER BY ordinal")
            .setParameter(1, runId).getResultList();
   }

   private void onCalculateDataSets(String param) {
      String[] parts = param.split(";", 2);
      int runId;
      try {
         runId = Integer.parseInt(parts[0]);
      } catch (NumberFormatException e) {
         log.errorf("Received notification to calculate dataset for run but cannot parse as run ID.", parts[0]);
         return;
      }
      transform(runId);
   }

   @ConsumeEvent(value = DataSet.EVENT_NEW, blocking = true)
   public void onNewDataset(DataSet dataSet) {
      calculateLabels(dataSet.id, -1);
   }

   @PermitAll
   @WithToken
   @WithRoles
   @Override
   public DataSet getDataSet(Integer datasetId) {
      return DataSet.findById(datasetId);
   }

   @PermitAll
   @WithRoles
   @Override
   public DatasetList listTestDatasets(int testId, Integer limit, Integer page, String sort, String direction) {
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
      DatasetList list = new DatasetList();
      for (Object[] row : rows) {
         DatasetSummary summary = new DatasetSummary();
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
            result.value = String.valueOf(runQuery(sqlQuery, jsonpath, datasetId));
         } else {
            // This schema-aware query already assumes that DataSet.data is an array of objects with defined schema
            String schemaQuery = "jsonb_path_query(data, '$[*] ? (@.\"$schema\" == $schema)', ('{\"schema\":\"' || ? || '\"}')::::jsonb)";
            String sqlQuery;
            if (!array) {
               sqlQuery = "SELECT jsonb_path_query_first(" + schemaQuery + ", ?::::jsonpath)#>>'{}' FROM dataset WHERE id = ? LIMIT 1";
            } else {
               sqlQuery = "SELECT jsonb_agg(v)#>>'{}' FROM (SELECT jsonb_path_query(" + schemaQuery + ", ?::::jsonpath) AS v FROM dataset WHERE id = ?) AS values";
            }
            result.value = String.valueOf(runQuery(sqlQuery, schemaUri, jsonpath, datasetId));
         }
         result.valid = true;
      } catch (PersistenceException pe) {
         SqlServiceImpl.setFromException(pe, result);
      }
      return result;
   }

   @Override
   public DatasetList listDatasetsBySchema(String uri, Integer limit, Integer page, String sort, String direction) {
      // TODO
      DatasetList list = new DatasetList();
      return list;
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   void transform(int runId) {
      if (runId < 1) {
         log.errorf("Transformation parameters error: run %s", runId);
      }
      // We need to make sure all old datasets are gone before creating new; otherwise we could
      // break the runid,ordinal uniqueness constraint
      DataSet.delete("runid", runId);

      Run run = Run.findById(runId);
      int ordinal = 0;
      Map<Integer, JsonNode> transformerResults = new TreeMap<>();
      // naked nodes (those produced by implicit identity transformers) are all added to each dataset
      List<JsonNode> nakedNodes = new ArrayList<>();

      List<Object[]> relevantSchemas = unchecked(em.createNamedQuery(QUERY_TRANSFORMER_TARGETS)
            .setParameter(1, run.id)
            .unwrap(NativeQuery.class)
            .addScalar("type", IntegerType.INSTANCE)
            .addScalar("key", TextType.INSTANCE)
            .addScalar("transformer_id", IntegerType.INSTANCE)
            .getResultList() );

      int schemasAndTransformers = relevantSchemas.size();
      for (Object[] relevantSchema : relevantSchemas) {
         int type = (int) relevantSchema[0];
         String key = (String) relevantSchema[1];
         Integer transformerId = (Integer) relevantSchema[2];

         Transformer t = null;
         if (transformerId != null) {
            t = Transformer.findById(transformerId);
            if (t == null) {
               log.errorf("Missing transformer with ID %d", transformerId);
            }
         }
         if (t != null) {
            ObjectNode root = JsonNodeFactory.instance.objectNode();
            JsonNode result;
            if (t.extractors != null && !t.extractors.isEmpty()) {
               if (type == Schema.TYPE_1ST_LEVEL) {
                  List<Object[]> extractedData = unchecked(em.createNamedQuery(QUERY_1ST_LEVEL_BY_RUNID_TRANSFORMERID_SCHEMA_ID)
                        .setParameter(1, run.id).setParameter(2, transformerId)
                        .unwrap(NativeQuery.class)
                        .addScalar("name", TextType.INSTANCE)
                        .addScalar("value", JsonNodeBinaryType.INSTANCE)
                        .getResultList());
                  addExtracted(root, extractedData);
               } else {
                  List<Object[]> extractedData = unchecked(em.createNamedQuery(QUERY_2ND_LEVEL_BY_RUNID_TRANSFORMERID_SCHEMA_ID)
                        .setParameter(1, run.id).setParameter(2, transformerId)
                        .setParameter(3, type == Schema.TYPE_2ND_LEVEL ? key : Integer.parseInt(key))
                        .unwrap(NativeQuery.class)
                        .addScalar("name", TextType.INSTANCE)
                        .addScalar("value", JsonNodeBinaryType.INSTANCE)
                        .getResultList());
                  addExtracted(root, extractedData);
               }
            }
            if (t.function != null && !t.function.isBlank()) {
               StringBuilder code = new StringBuilder();
               String obj = root.toString();
               code.append("const __obj = ").append(obj).append(" ;\n");
               code.append("const __func = ").append(t.function).append(";\n");
               code.append("__func(__obj);\n");
               ByteArrayOutputStream out = new ByteArrayOutputStream();
               try (org.graalvm.polyglot.Context context = org.graalvm.polyglot.Context.newBuilder("js").out(out).err(out).build()) {
                  context.enter();
                  try {
                     Value value = context.eval("js", code.toString());
                     result = Util.convertToJson(value);
                  } catch (PolyglotException e) {
                     log.errorf("Failed to execute transformation for run %d and test %d and cause %s", run.id, run.testid, e);
                     logTransformationMessage(em.getReference(Test.class, run.testid), run, TransformationLog.ERROR, "transformation " + t.name + " function");
                     continue;
                  } finally {
                     context.leave();
                     if (out.size() > 0) {
                        logTransformationMessage(em.getReference(Test.class, run.testid), run, TransformationLog.INFO, "transformation " + t.name + " function");
                     }
                  }
               }
            } else {
               result = root;
            }
            if (t.targetSchemaUri != null) {
               if (result.isObject()) {
                  putIfAbsent(t.targetSchemaUri, (ObjectNode) result);
               } else if (result.isArray()) {
                  ArrayNode array = (ArrayNode) result;
                  for (JsonNode node : array) {
                     if (node.isObject()) {
                        putIfAbsent(t.targetSchemaUri, (ObjectNode) node);
                     }
                  }
               } else {
                  result = instance.objectNode()
                        .put("$schema", t.targetSchemaUri).set("value", result);
               }
            }
            JsonNode existing = transformerResults.get(transformerId);
            if (existing == null) {
               transformerResults.put(transformerId, result);
            } else if (existing.isArray()) {
               if (result.isArray()) {
                  ((ArrayNode) existing).addAll((ArrayNode) result);
               } else {
                  ((ArrayNode) existing).add(result);
               }
            } else {
               if (result.isArray()) {
                  ((ArrayNode) result).insert(0, existing);
                  transformerResults.put(transformerId, result);
               } else {
                  transformerResults.put(transformerId, instance.arrayNode().add(existing).add(result));
               }
            }
         } else {
            JsonNode node;
            switch (type) {
               case Schema.TYPE_1ST_LEVEL:
                  node = run.data;
                  break;
               case Schema.TYPE_2ND_LEVEL:
                  node = run.data.path(key);
                  break;
               case Schema.TYPE_ARRAY_ELEMENT:
                  node = run.data.path(Integer.parseInt(key));
                  break;
               default:
                  throw new IllegalStateException("Unknown type " + type);
            }
            nakedNodes.add(node);
         }
      }
      if (schemasAndTransformers > 0) {
         int max = transformerResults.values().stream().filter(JsonNode::isArray).mapToInt(JsonNode::size).max().orElse(1);

         for (int position = 0; position < max; position += 1) {
            ArrayNode all = instance.arrayNode(max + nakedNodes.size());
            for (var entry: transformerResults.entrySet()) {
               JsonNode node = entry.getValue();
               if (node.isObject()) {
                  all.add(node);
               } else if (node.isArray()) {
                  if (position < node.size()) {
                     all.add(node.get(position));
                  } else {
                     log.warnf("Transformer %d produced an array of %d elements but other transformer " +
                           "produced %d elements; dataset %d/%d might be missing some data.",
                           entry.getKey(), node.size(), max, run.id, ordinal);
                  }
               } else {
                  log.warnf("Unexpected result provided by one of the transformers: %s", node);
               }
            }
            nakedNodes.forEach(all::add);
            createDataset(new DataSet(run.start, run.stop, run.description,
                  run.testid, all, run, ordinal++, run.owner, run.access));
         }
      } else {
         createDataset(new DataSet(run.start, run.stop,
               "Empty DataSet for run data without any schema.",
               run.testid, instance.arrayNode(), run, 0, run.owner, run.access));
      }
   }

   private void createDataset(DataSet ds) {
      try {
         ds.persist();
         Util.publishLater(tm, eventBus, DataSet.EVENT_NEW, ds);
      } catch (TransactionRequiredException tre) {
         log.error("Failed attempt to persist and send DataSet event during inactive Transaction. Likely due to prior error.", tre);
      }
   }

   @Transactional(Transactional.TxType.REQUIRES_NEW)
   protected void logTransformationMessage(Test test, Run run, int level, String format, Object... args) {
      new TransformationLog(test, run, level, "data", String.format(format, args)).persist();
   }

   @SuppressWarnings("unchecked")
   private List<Object[]> unchecked(@SuppressWarnings("rawtypes") List list) {
      return (List<Object[]>)list;
   }

   private JsonNode findNestedMatch(JsonNode node, String uri) {
      Iterator<Map.Entry<String, JsonNode>> children = node.fields();
      while (children.hasNext()) {
         Map.Entry<String, JsonNode> child = children.next();
         if (child.getValue().isObject() && uri.equals(child.getValue().path("$schema").textValue())) {
            return child.getValue();
         }
      }
      log.errorf("Cannot find nested node with URI %s despite it should be present.", uri);
      return null;
   }

   private void addExtracted(ObjectNode root, List<Object[]> resultSet) {
      for (Object[] labelValue : resultSet) {
         String name = (String)labelValue[0];
         JsonNode value = (JsonNode) labelValue[1];
         root.set(name, value);
      }
   }

   private void putIfAbsent(String uri, ObjectNode node) {
      if (uri != null && !uri.isBlank() && node != null && node.path("$schema").isMissingNode()) {
         node.put("$schema", uri);
      }
   }

}
