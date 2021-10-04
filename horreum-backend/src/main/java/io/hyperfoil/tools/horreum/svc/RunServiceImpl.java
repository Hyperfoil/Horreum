package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.api.RunService;
import io.hyperfoil.tools.horreum.api.SqlService;
import io.hyperfoil.tools.horreum.entity.json.Access;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.SchemaExtractor;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.entity.json.ViewComponent;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.json.ValueConverter;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.networknt.schema.ValidationMessage;

@ApplicationScoped
@Startup
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
            "SELECT id AS testid, unnest(regexp_split_to_array(tags, ';')) AS accessor, tagscalculation FROM test" +
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
   private static final Json EMPTY_ARRAY = new Json(true);

   @Inject
   EntityManager em;

   @Inject
   SecurityIdentity identity;

   @Inject
   TransactionManager tm;

   @Inject
   SqlServiceImpl sqlService;

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
      try (@SuppressWarnings("ununsed") CloseMe h1 = sqlService.withRoles(em, parts[2]);
           @SuppressWarnings("ununsed") CloseMe h2 = sqlService.withToken(em, parts[1])) {
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
                     tags = ValueConverter.convert(value).toString();
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
         Query insert = em.createNativeQuery("INSERT INTO run_tags (runid, tags, extractor_ids) VALUES (?, ?::::jsonb, ARRAY(SELECT jsonb_array_elements(?::::jsonb)::::int));");
         insert.setParameter(1, runId).setParameter(2, tags).setParameter(3, extractorIds);
         if (insert.executeUpdate() != 1) {
            log.errorf("Failed to insert run tags for run %d (invalid update count - maybe missing privileges?)", runId);
         }
      }
   }

   private Object runQuery(String query, String token, Object... params) {
      try (@SuppressWarnings("unused") CloseMe h1 = sqlService.withRoles(em, identity);
           @SuppressWarnings("unused") CloseMe h2 = sqlService.withToken(em, token)) {
         Query q = em.createNativeQuery(query);
         for (int i = 0; i < params.length; ++i) {
            q.setParameter(i + 1, params[i]);
         }
         try {
            return q.getSingleResult();
         } catch (NoResultException e) {
            throw ServiceException.notFound("No result");
         }
      }
   }

   @PermitAll
   @Override
   public Object getRun(Integer id, String token) {
      return runQuery("SELECT (to_jsonb(run) ||" +
            "jsonb_set('{}', '{schema}', (SELECT COALESCE(jsonb_object_agg(schemaid, uri), '{}') FROM run_schemas WHERE runid = ?)::::jsonb, true) || " +
            "jsonb_set('{}', '{testname}', to_jsonb((SELECT name FROM test WHERE test.id = run.testid)), true)" +
            ")::::text FROM run where id = ?", token, id, id);
   }

   @PermitAll
   @Override
   public Object getData(Integer id, String token) {
      return runQuery("SELECT data#>>'{}' from run where id = ?", token, id);
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
            result.value = String.valueOf(runQuery(sqlQuery, null, jsonpath, id, schemaUri));
         } else {
            String sqlQuery = "SELECT " + func + "(data, ?::::jsonpath)#>>'{}' FROM run WHERE id = ?";
            result.value = String.valueOf(runQuery(sqlQuery, null, jsonpath, id));
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
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         Query query = em.createNativeQuery(UPDATE_TOKEN);
         query.setParameter(1, token);
         query.setParameter(2, id);
         if (query.executeUpdate() != 1) {
            throw ServiceException.serverError("Token reset failed (missing permissions?)");
         } else {
            return token;
         }
      }
   }

   @RolesAllowed(Roles.TESTER)
   @Transactional
   @Override
   // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
   public void updateAccess(Integer id, String owner, Access access) {
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         Query query = em.createNativeQuery(CHANGE_ACCESS);
         query.setParameter(1, owner);
         query.setParameter(2, access.ordinal());
         query.setParameter(3, id);
         if (query.executeUpdate() != 1) {
            throw ServiceException.serverError("Access change failed (missing permissions?)");
         }
      }
   }

   @PermitAll
   @Override
   public Json getStructure(Integer id, String token) {
      try (@SuppressWarnings("unused") CloseMe h1 = sqlService.withRoles(em, identity);
           @SuppressWarnings("unused") CloseMe h2 = sqlService.withToken(em, token)) {
         Run found = Run.find("id", id).firstResult();
         if (found != null) {
            return Json.typeStructure(found.data);
         }
         return new Json(false);
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
      try (@SuppressWarnings("unused") CloseMe h1 = sqlService.withRoles(em, identity);
           @SuppressWarnings("unused") CloseMe h2 = sqlService.withToken(em, token)) {
         Test test = testService.getByNameOrId(testNameOrId);
         if (test == null) {
            throw ServiceException.serverError("Failed to find test " + testNameOrId);
         }
         run.testid = test.id;
         Integer runId = addAuthenticated(run, test);
         response.addHeader(HttpHeaders.LOCATION, "/run/" + runId);
         return String.valueOf(runId);
      }
   }

   @PermitAll // all because of possible token-based upload
   @Transactional
   @Override
   public String addRunFromData(String start, String stop, String test,
                                String owner, Access access, String token,
                                String schemaUri, String description,
                                Json data) {
      if (data == null) {
         throw ServiceException.badRequest("No data!");
      }
      Object foundTest = findIfNotSet(test, data);
      Object foundStart = findIfNotSet(start, data);
      Object foundStop = findIfNotSet(stop, data);
      Object foundDescription = findIfNotSet(description, data);

      try (@SuppressWarnings("unused") CloseMe h1 = sqlService.withRoles(em, identity);
           @SuppressWarnings("unused") CloseMe h2 = sqlService.withToken(em, token)) {
         if (schemaUri == null || schemaUri.isEmpty()) {
            schemaUri = data.getString("$schema");
         } else {
            data.set("$schema", schemaUri);
         }

         String testNameOrId = foundTest == null ? null : foundTest.toString().trim();
         if (testNameOrId == null || testNameOrId.isEmpty()) {
            throw ServiceException.badRequest("Cannot identify test name.");
         }

         Instant startInstant = toInstant(foundStart);
         Instant stopInstant = toInstant(foundStop);
         if (startInstant == null) {
            throw ServiceException.badRequest("Cannot get start time.");
         } else if (stopInstant == null) {
            throw ServiceException.badRequest("Cannot get stop time.");
         }

         Test testEntity = testService.getByNameOrId(testNameOrId);
         if (testEntity == null) {
            throw ServiceException.serverError("Failed to find test " + testNameOrId);
         }

         Collection<ValidationMessage> validationErrors = schemaService.validate(data, schemaUri);
         if (validationErrors != null && !validationErrors.isEmpty()) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(validationErrors).build());
         }

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
   }

   private Object findIfNotSet(String value, Json data) {
      if (value != null && !value.isEmpty()) {
         if (value.startsWith("$.")) {
            return Json.find(data, value, null);
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
            throw ServiceException.badRequest("Missing owner and cannot select single default owners; this user has these uploader roles: " + uploaders);
         }
         String uploader = uploaders.get(0);
         run.owner = uploader.substring(0, uploader.length() - 9) + "-team";
      } else if (!Objects.equals(test.owner, run.owner) && !identity.getRoles().contains(run.owner)) {
         throw ServiceException.badRequest("This user does not have permissions to upload run for owner=" + run.owner);
      }
      if (run.access == null) {
         run.access = Access.PRIVATE;
      }

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
      eventBus.publish(Run.EVENT_NEW, run);

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
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
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

      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)){
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
            run.tags = tags == null || tags.isEmpty() ? EMPTY_ARRAY : Json.fromString(tags);
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
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         RunCounts counts = new RunCounts();
         counts.total = Run.count("testid = ?1", testId);
         counts.active = Run.count("testid = ?1 AND trashed = false", testId);
         counts.trashed = counts.total - counts.active;
         return counts;
      }
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
      if (sort.startsWith("view_data:")) {
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
      Test test;
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         test = Test.find("id", testId).firstResult();
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
            Json unorderedView = viewString == null ? Json.map().build() : Json.fromString(viewString);
            Json.ArrayBuilder view = Json.array();
            if (test.defaultView != null) {
               for (ViewComponent c : test.defaultView.components) {
                  Json componentData = unorderedView.getJson(String.valueOf(c.id));
                  if (componentData == null) {
                     view.add(null);
                  } else {
                     String[] accessors = c.accessors();
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
            run.schema = schemas == null ? null : Json.fromString(schemas);
            run.view = view.build();
            run.trashed = (boolean) row[7];
            run.description = (String) row[8];
            run.tags = runTags == null ? null : Json.fromString(runTags);
            runs.add(run);
         }
         TestRunsSummary summary = new TestRunsSummary();
         summary.total = trashed ? Run.count("testid = ?1", testId) : Run.count("testid = ?1 AND trashed = false", testId);
         summary.runs = runs;
         return summary;
      }
   }

   @PermitAll
   @Override
   public RunsSummary listBySchema(String uri, Integer limit, Integer page, String sort, String direction) {
      if (uri == null || uri.isEmpty()) {
         throw ServiceException.badRequest("No `uri` query parameter given.");
      }
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
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
   }

   @RolesAllowed(Roles.TESTER)
   @Transactional
   @Override
   public void trash(Integer id, Boolean isTrashed) {
      updateRun(id, run -> run.trashed = isTrashed == null || isTrashed);
      eventBus.publish(Run.EVENT_TRASHED, id);
   }

   @RolesAllowed(Roles.TESTER)
   @Transactional
   @Override
   public void updateDescription(Integer id, String description) {
      // FIXME: fetchival stringifies the body into JSON string :-/
      updateRun(id, run -> run.description = Util.destringify(description));
   }

   public void updateRun(Integer id, Consumer<Run> consumer) {
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         Run run = Run.findById(id);
         if (run == null) {
            throw ServiceException.notFound("Run not found");
         }
         consumer.accept(run);
         run.persistAndFlush();
      }
   }

   @RolesAllowed(Roles.TESTER)
   @Transactional
   @Override
   public Object updateSchema(Integer id, String path, String schemaUri) {
      // FIXME: fetchival stringifies the body into JSON string :-/
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         Run run = Run.findById(id);
         if (run == null) {
            throw ServiceException.notFound("Run not found.");
         }
         // Triggering dirty property on Run
         Json data = run.data.clone();
         String uri = Util.destringify(schemaUri);
         Json item = path == null || path.isEmpty() ? data : data.getJson(path);
         if (uri != null && !uri.isEmpty()) {
            item.set("$schema", uri);
         } else {
            item.remove("$schema");
         }
         run.data = data;
         run.persist();
         Query query = em.createNativeQuery("SELECT COALESCE(jsonb_object_agg(schemaid, uri), '{}')::::text FROM run_schemas WHERE runid = ?");
         query.setParameter(1, run.id);
         Object schemas = query.getSingleResult();
         em.flush();
         return schemas;
      }
   }
}
