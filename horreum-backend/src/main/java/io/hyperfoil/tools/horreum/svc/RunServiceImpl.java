package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.api.RunService;
import io.hyperfoil.tools.horreum.api.SqlService;
import io.hyperfoil.tools.horreum.entity.json.Access;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.SchemaExtractor;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.entity.json.ViewComponent;
import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.server.RoleManager;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.hyperfoil.tools.horreum.server.WithToken;
import io.quarkus.runtime.Startup;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.eventbus.EventBus;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.networknt.schema.ValidationMessage;

@ApplicationScoped
@Startup
@WithRoles
@WithToken
public class RunServiceImpl implements RunService {
   private static final Logger log = Logger.getLogger(RunServiceImpl.class);

   //@formatter:off
   private static final String FIND_AUTOCOMPLETE =
         "SELECT * FROM (" +
            "SELECT DISTINCT jsonb_object_keys(q) AS key " +
            "FROM run, jsonb_path_query(run.data, ? ::::jsonpath) q " +
            "WHERE jsonb_typeof(q) = 'object') AS keys " +
         "WHERE keys.key LIKE CONCAT(?, '%');";
   // TODO: array queries!
   private static final String GET_TAGS =
         "WITH test_tags AS (" +
            "SELECT id AS testid, unnest(string_to_array(tags, ';')) AS accessor, tagscalculation FROM test" +
         "), tags AS (" +
            "SELECT rs.runid, se.id as extractor_id, se.accessor, jsonb_path_query_first(run.data, (rs.prefix || se.jsonpath)::::jsonpath) AS value, test_tags.tagscalculation " +
            "FROM schemaextractor se " +
            "JOIN test_tags ON se.accessor = test_tags.accessor " +
            "JOIN run_schemas rs ON rs.testid = test_tags.testid AND rs.schemaid = se.schema_id " +
            "JOIN run ON run.id = rs.runid " +
            "WHERE rs.runid = ?" +
         ")" +
         "SELECT tagscalculation, " +
            "json_object_agg(tags.accessor, tags.value)::::text AS tags, " +
            "json_agg(tags.extractor_id)::::text AS extractor_ids FROM tags GROUP BY runid, tagscalculation;";
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
   RoleManager roleManager;

   @Inject
   EventBus eventBus;

   @Inject
   TestServiceImpl testService;

   @Inject
   SchemaServiceImpl schemaService;

   @Context HttpServletResponse response;

   @PostConstruct
   public void init() {
      sqlService.registerListener("calculate_tags", this::onCalculateTags);
   }

   private void onCalculateTags(String param) {
      String[] parts = param.split(";", 3);
      if (parts.length < 3) {
         log.errorf("Received notification to recalculate tags %s but cannot extract run ID.", param);
         return;
      }
      int runId;
      try {
         runId = Integer.parseInt(parts[0]);
      } catch (NumberFormatException e) {
         log.errorf("Received notification to recalculate tags for run %s but cannot parse as run ID.", parts[0]);
         return;
      }
      try (@SuppressWarnings("unused") CloseMe h1 = roleManager.withRoles(em, parts[2]);
           @SuppressWarnings("unused") CloseMe h2 = roleManager.withToken(em, parts[1])) {
         log.debugf("Recalculating tags for run %s", runId);
         Object[] result = (Object[]) em.createNativeQuery(GET_TAGS).setParameter(1, runId).getSingleResult();
         String calculation = (String) result[0];
         String tags = String.valueOf(result[1]);
         String extractorIds = String.valueOf(result[2]);

         if (calculation != null) {
            StringBuilder jsCode = new StringBuilder();
            jsCode.append("const __obj = ").append(tags).append(";\n");
            jsCode.append("const __func = ").append(calculation).append(";\n");
            jsCode.append("__func(__obj);");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (org.graalvm.polyglot.Context context = org.graalvm.polyglot.Context.newBuilder("js").out(out).err(out).build()) {
               context.enter();
               try {
                  Value value = context.eval("js", jsCode);
                  // TODO debuggable
                  if (value.isNull()) {
                     tags = null;
                  } else {
                     tags = Util.convert(value).toString();
                     if ("undefined".equals(tags)) {
                        tags = null;
                     }
                  }
               } catch (PolyglotException e) {
                  log.errorf(e, "Failed to evaluate tags function on run %d.", runId);
                  log.infof("Offending code: %s", jsCode);
                  return;
               } finally {
                  if (out.size() > 0) {
                     log.infof("Output while calculating tags for run %d: <pre>%s</pre>", runId, out.toString());
                  }
                  context.leave();
               }
            }
         }
         Query insert = em.createNativeQuery("INSERT INTO run_tags (runid, tags, extractor_ids) VALUES (?, (?::::text)::::jsonb, ARRAY(SELECT jsonb_array_elements((?::::text)::::jsonb)::::int));");
         insert.setParameter(1, runId).setParameter(2, tags).setParameter(3, extractorIds);
         if (insert.executeUpdate() != 1) {
            log.errorf("Failed to insert run tags for run %d (invalid update count - maybe missing privileges?)", runId);
         }
         eventBus.publish(Run.EVENT_TAGS_CREATED, new Run.TagsEvent(runId, tags));
      } catch (NoResultException e) {
         log.infof("Run %d does not create any tags.", runId);
         eventBus.publish(Run.EVENT_TAGS_CREATED, new Run.TagsEvent(runId, null));
      } catch (Throwable e) {
         log.errorf(e, "Failed to calculate tags for run %d", runId);
      }
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
   @Override
   public Object getRun(Integer id, String token) {
      return runQuery("SELECT (to_jsonb(run) ||" +
            "jsonb_set('{}', '{schema}', (SELECT COALESCE(jsonb_object_agg(schemaid, uri), '{}') FROM run_schemas WHERE runid = ?)::::jsonb, true) || " +
            "jsonb_set('{}', '{testname}', to_jsonb((SELECT name FROM test WHERE test.id = run.testid)), true)" +
            ")::::text FROM run where id = ?", id, id);
   }

   @PermitAll
   @Override
   public Object getData(Integer id, String token) {
      return runQuery("SELECT data#>>'{}' from run where id = ?", id);
   }

   @PermitAll
   @Override
   public QueryResult queryData(Integer id, String jsonpath, String schemaUri, Boolean array) {
      String func = array != null && array ? "jsonb_path_query_array" : "jsonb_path_query_first";
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
   @Override
   public RunsSummary list(String query, boolean matchAll, String roles, boolean trashed,
                           Integer limit, Integer page, String sort, String direction) {
      StringBuilder sql = new StringBuilder("SELECT run.id, run.start, run.stop, run.testId, ")
         .append("run.owner, run.access, run.token, ")
         .append("test.name AS testname, run.trashed, run.description, run_tags.tags::::text AS tags ")
         .append("FROM run JOIN test ON test.id = run.testId LEFT JOIN run_tags ON run_tags.runid = run.id WHERE ");
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
            run.id = (int) row[0];
            run.start = ((Timestamp) row[1]).getTime();
            run.stop = ((Timestamp) row[2]).getTime();
            run.testid = (int) row[3];
            run.owner = (String) row[4];
            run.access = (int) row[5];
            run.token = (String) row[6];
            run.testname = (String) row[7];
            run.trashed = (boolean) row[8];
            run.description = (String) row[9];
            String tags = (String) row[10];
            run.tags = tags == null || tags.isEmpty() ? Util.EMPTY_ARRAY : Util.toJsonNode(tags);
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

   @PermitAll
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
   @Override
   public TestRunsSummary testList(Integer testId, boolean trashed, String tags,
                                   Integer limit, Integer page, String sort, String direction) {
      StringBuilder sql = new StringBuilder("WITH schema_agg AS (")
            .append("    SELECT COALESCE(jsonb_object_agg(schemaid, uri), '{}') AS schemas, rs.runid FROM run_schemas rs GROUP BY rs.runid")
            .append("), view_agg AS (")
            .append("    SELECT jsonb_object_agg(coalesce(vd.vcid, 0), vd.object) AS view, vd.runid FROM view_data vd GROUP BY vd.runid")
            .append(") SELECT run.id, run.start, run.stop, run.access, run.owner, schema_agg.schemas::::text AS schemas, view_agg.view#>>'{}' AS view, ")
            .append("run.trashed, run.description, run_tags.tags::::text FROM run ")
            .append("LEFT JOIN schema_agg ON schema_agg.runid = run.id ")
            .append("LEFT JOIN view_agg ON view_agg.runid = run.id ")
            .append("LEFT JOIN run_tags ON run_tags.runid = run.id ")
            .append("WHERE run.testid = ?1 ");
      if (!trashed) {
         sql.append(" AND NOT run.trashed ");
      }
      Map<String, String> tagsMap = Tags.parseTags(tags);
      if (tagsMap != null) {
         Tags.addTagQuery(tagsMap, sql, 2);
      }
      if (sort != null && sort.startsWith("view_data:")) {
         String accessor = sort.substring(sort.indexOf(':', 10) + 1);
         sql.append(" ORDER BY");
         // TODO: use view ID in the sort format rather than wildcards below
         // prefer numeric sort
         sql.append(" to_double(jsonb_path_query_first(view_agg.view, '$.*.").append(accessor).append("')#>>'{}')");
         Util.addDirection(sql, direction);
         sql.append(", jsonb_path_query_first(view_agg.view, '$.*.").append(accessor).append("')#>>'{}'");
         Util.addDirection(sql, direction);
      } else {
         Util.addOrderBy(sql, sort, direction);
      }
      Util.addLimitOffset(sql, limit, page);
      Test test = Test.find("id", testId).firstResult();
      if (test == null) {
         throw ServiceException.notFound("Cannot find test ID " + testId);
      }
      Query query = em.createNativeQuery(sql.toString());
      query.setParameter(1, testId);
      Tags.addTagValues(tagsMap, query, 2);
      @SuppressWarnings("unchecked")
      List<Object[]> resultList = query.getResultList();
      List<TestRunSummary> runs = new ArrayList<>();
      for (Object[] row : resultList) {
         String viewString = (String) row[6];
         JsonNode unorderedView = viewString == null ? Util.EMPTY_OBJECT : Util.toJsonNode(viewString);
         ArrayNode view = JsonNodeFactory.instance.arrayNode();
         if (test.defaultView != null) {
            for (ViewComponent c : test.defaultView.components) {
               JsonNode componentData = unorderedView.get(String.valueOf(c.id));
               String[] accessors = c.accessors();
               if (componentData == null) {
                  if (accessors.length == 1) {
                     view.addNull();
                  } else {
                     ObjectNode builder = view.addObject();
                     for (String accessor: accessors) {
                        if (SchemaExtractor.isArray(accessor)) {
                           builder.set(SchemaExtractor.arrayName(accessor), JsonNodeFactory.instance.nullNode());
                        } else {
                           builder.set(accessor, JsonNodeFactory.instance.nullNode());
                        }
                     }
                  }
               } else {
                  if (accessors.length == 1) {
                     String accessor = accessors[0];
                     if (SchemaExtractor.isArray(accessors[0])) {
                        accessor = SchemaExtractor.arrayName(accessor);
                     }
                     view.add(componentData.get(accessor));
                  } else {
                     view.add(componentData);
                  }
               }
            }
         }
         String schemas = (String) row[5];
         String runTags = (String) row[9];
         TestRunSummary run = new TestRunSummary();
         run.id = (int) row[0];
         run.start = ((Timestamp) row[1]).getTime();
         run.stop = ((Timestamp) row[2]).getTime();
         run.testid = testId;
         run.access = (int) row[3];
         run.owner = (String) row[4];
         run.schema = Util.toJsonNode(schemas);
         run.view = view;
         run.trashed = (boolean) row[7];
         run.description = (String) row[8];
         run.tags = Util.toJsonNode(runTags);
         runs.add(run);
      }
      TestRunsSummary summary = new TestRunsSummary();
      summary.total = trashed ? Run.count("testid = ?1", testId) : Run.count("testid = ?1 AND trashed = false", testId);
      summary.runs = runs;
      return summary;
   }

   @PermitAll
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
      updateRun(id, run -> run.trashed = isTrashed == null || isTrashed);
      Util.publishLater(tm, eventBus, Run.EVENT_TRASHED, id);
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
}
