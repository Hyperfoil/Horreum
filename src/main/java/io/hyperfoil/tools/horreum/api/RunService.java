package io.hyperfoil.tools.horreum.api;

import io.hyperfoil.tools.horreum.entity.converter.JsonResultTransformer;
import io.hyperfoil.tools.horreum.entity.json.Access;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.SchemaExtractor;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.entity.json.ViewComponent;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.eventbus.EventBus;
import org.jboss.logging.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

@Path("/api/run")
@Consumes({MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public class RunService {
   private static final Logger log = Logger.getLogger(RunService.class);

   //@formatter:off
   private static final String FIND_AUTOCOMPLETE = "SELECT * FROM (" +
            "SELECT DISTINCT jsonb_object_keys(q) AS key " +
            "FROM run, jsonb_path_query(run.data, ? ::jsonpath) q " +
            "WHERE jsonb_typeof(q) = 'object') AS keys " +
         "WHERE keys.key LIKE CONCAT(?, '%');";
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
   SqlService sqlService;

   @Inject
   EventBus eventBus;

   @Inject
   TestService testService;

   @Inject
   SchemaService schemaService;

   private Response runQuery(String query, String token, Object... params) {
      try (@SuppressWarnings("unused") CloseMe h1 = sqlService.withRoles(em, identity);
           @SuppressWarnings("unused") CloseMe h2 = sqlService.withToken(em, token)) {
         Query q = em.createNativeQuery(query);
         for (int i = 0; i < params.length; ++i) {
            q.setParameter(i + 1, params[i]);
         }
         try {
            return Response.ok(q.getSingleResult()).build();
         } catch (NoResultException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
         }
      }
   }

   @PermitAll
   @GET
   @Path("{id}")
   public Response getRun(@PathParam("id") Integer id,
                          @QueryParam("token") String token) {
      return runQuery("SELECT (to_jsonb(run) ||" +
            "jsonb_set('{}', '{schema}', (SELECT COALESCE(jsonb_object_agg(schemaid, uri), '{}') FROM run_schemas WHERE runid = ?)::::jsonb, true) || " +
            "jsonb_set('{}', '{testname}', to_jsonb((SELECT name FROM test WHERE test.id = run.testid)), true)" +
            ")::::text FROM run where id = ?", token, id, id);
   }

   @PermitAll
   @GET
   @Path("{id}/data")
   public Response getData(@PathParam("id") Integer id, @QueryParam("token") String token) {
      return runQuery("SELECT data#>>'{}' from run where id = ?", token, id);
   }

   @RolesAllowed("tester")
   @POST
   @Path("{id}/resetToken")
   public Response resetToken(@PathParam("id") Integer id) {
      return updateToken(id, Tokens.generateToken());
   }

   @RolesAllowed("tester")
   @POST
   @Path("{id}/dropToken")
   public Response dropToken(@PathParam("id") Integer id) {
      return updateToken(id, null);
   }

   private Response updateToken(Integer id, String token) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         Query query = em.createNativeQuery(UPDATE_TOKEN);
         query.setParameter(1, token);
         query.setParameter(2, id);
         if (query.executeUpdate() != 1) {
            return Response.serverError().entity("Token reset failed (missing permissions?)").build();
         } else {
            return (token != null ? Response.ok(token) : Response.noContent()).build();
         }
      }
   }

   @RolesAllowed("tester")
   @POST
   @Path("{id}/updateAccess")
   // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
   public Response updateAccess(@PathParam("id") Integer id,
                                @QueryParam("owner") String owner,
                                @QueryParam("access") Access access) {
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         Query query = em.createNativeQuery(CHANGE_ACCESS);
         query.setParameter(1, owner);
         query.setParameter(2, access.ordinal());
         query.setParameter(3, id);
         if (query.executeUpdate() != 1) {
            return Response.serverError().entity("Access change failed (missing permissions?)").build();
         } else {
            return Response.accepted().build();
         }
      }
   }

   @PermitAll
   @GET
   @Path("{id}/structure")
   public Json getStructure(@PathParam("id") Integer id,
                            @QueryParam("token") String token) {
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
   @POST
   @Path("test/{test}")
   @Consumes(MediaType.APPLICATION_JSON)
   @Transactional
   public Response add(@PathParam("test") String testNameOrId,
                       @QueryParam("owner") String owner,
                       @QueryParam("access") Access access,
                       @QueryParam("token") String token,
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
            return Response.serverError().entity("Failed to find test " + testNameOrId).build();
         }
         run.testid = test.id;
         return addAuthenticated(run, test);
      }
   }

   @PermitAll // all because of possible token-based upload
   @POST
   @Path("data")
   @Transactional
   public Response addRunFromData(@QueryParam("start") String start,
                                  @QueryParam("stop") String stop,
                                  @QueryParam("test") String test,
                                  @QueryParam("owner") String owner,
                                  @QueryParam("access") Access access,
                                  @QueryParam("schema") String schemaUri,
                                  @QueryParam("description") String description,
                                  @QueryParam("token") String token,
                                  Json data) {
      if (data == null) {
         return Response.status(Response.Status.BAD_REQUEST).entity("No data!").build();
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
            return Response.status(Response.Status.BAD_REQUEST).entity("Cannot identify test name.").build();
         }

         Instant startInstant = toInstant(foundStart);
         Instant stopInstant = toInstant(foundStop);
         if (startInstant == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Cannot get start time.").build();
         } else if (stopInstant == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Cannot get stop time.").build();
         }

         Test testEntity = testService.getByNameOrId(testNameOrId);
         if (testEntity == null) {
            return Response.serverError().entity("Failed to find test " + testNameOrId).build();
         }

         Json validationErrors = schemaService.validate(data, schemaUri);
         if (validationErrors != null && !validationErrors.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(validationErrors).build();
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

         Response response = addAuthenticated(run, testEntity);
         if (token != null && response.getStatus() < 300) {
            // TODO: remove the token
         }
         return response;
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

   private Response addAuthenticated(Run run, Test test) {
      // Id will be always generated anew
      run.id = null;

      if (run.owner == null || run.access == null) {
         return Response.status(400).entity("Missing access info (owner and access)").build();
      } else if (!Objects.equals(test.owner, run.owner) && !identity.getRoles().contains(run.owner)) {
         return Response.status(400).entity("This user does not have permissions to upload run for owner=" + run.owner).build();
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
         return Response.serverError().entity("Failed to persist run").build();
      }
      eventBus.publish(Run.EVENT_NEW, run);

      return Response.status(201).entity(String.valueOf(run.id)).header(HttpHeaders.LOCATION, "/run/" + run.id).build();
   }

   private void addPaging(StringBuilder sql, Integer limit, Integer page, String sort, String direction) {
      addOrderBy(sql, sort, direction);
      addLimitOffset(sql, limit, page);
   }

   private void addOrderBy(StringBuilder sql, String sort, String direction) {
      sort = sort == null || sort.trim().isEmpty() ? "start" : sort;
      direction = direction == null || direction.trim().isEmpty() ? "Ascending" : direction;
      sql.append(" ORDER BY ").append(sort);
      addDirection(sql, direction);
   }

   private void addDirection(StringBuilder sql, String direction) {
      if (direction != null) {
         sql.append("Ascending".equalsIgnoreCase(direction) ? " ASC" : " DESC");
      }
      sql.append(" NULLS LAST");
   }

   private void addLimitOffset(StringBuilder sql, Integer limit, Integer page) {
      if (limit != null && limit > 0) {
         sql.append(" limit ").append(limit);
         if (page != null && page >= 0) {
            sql.append(" offset ").append(limit * (page - 1));
         }
      }
   }

   @PermitAll
   @GET
   @Path("autocomplete")
   public Response autocomplete(@QueryParam("query") String query) {
      if (query == null || query.isEmpty()) {
         return Response.noContent().build();
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
            return Response.ok("[]").build();
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
         List<Object[]> results = findAutocomplete.getResultList();
         Json.ArrayBuilder array = Json.array();
         for (Object[] row : results) {
            String option = (String) row[0];
            if (!option.matches("^[a-zA-Z0-9_-]*$")) {
               option = "\"" + option + "\"";
            }
            array.add(option);
         }
         return Response.ok(array.build()).build();
      } catch (PersistenceException e) {
         return Response.status(400).entity("Failed processing query '" + query + "':\n" + e.getLocalizedMessage()).build();
      }
   }

   @PermitAll
   @GET
   @Path("list")
   public Response list(@QueryParam("query") String query,
                        @QueryParam("matchAll") boolean matchAll,
                        @QueryParam("roles") String roles,
                        @QueryParam("trashed") boolean trashed,
                        @QueryParam("limit") Integer limit,
                        @QueryParam("page") Integer page,
                        @QueryParam("sort") String sort,
                        @QueryParam("direction") String direction) {
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
         }
         sql.append(")");
         whereStarted = true;
      }

      if (!identity.isAnonymous() && hasRolesParam(roles)) {
         if (whereStarted) {
            sql.append(" AND ");
         }
         sql.append(" run.owner = ANY(string_to_array(?").append(queryParts.length + 1).append(", ';')) ");
         whereStarted = true;
      }
      if (!trashed) {
         if (whereStarted) {
            sql.append(" AND ");
         }
         sql.append(" trashed = false ");
      }
      addPaging(sql, limit, page, sort, direction);

      Query sqlQuery = em.createNativeQuery(sql.toString());
      for (int i = 0; i < queryParts.length; ++i) {
         if (queryParts[i].startsWith("$")) {
            sqlQuery.setParameter(i + 1, queryParts[i]);
         } else if (queryParts[i].startsWith("@")) {
            sqlQuery.setParameter(i + 1, "   $.** ? (" + queryParts[i] + ")");
         } else {
            sqlQuery.setParameter(i + 1, "$.**." + queryParts[i]);
         }
      }

      if (!identity.isAnonymous() && hasRolesParam(roles)) {
         String actualRoles;
         if (roles.equals("__my")) {
            actualRoles = String.join(";", identity.getRoles());
         } else {
            actualRoles = roles;
         }
         sqlQuery.setParameter(queryParts.length + 1, actualRoles);
      }

      SqlService.setResultTransformer(sqlQuery, JsonResultTransformer.INSTANCE);
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)){
         Json runs = new Json(true);
         @SuppressWarnings("unchecked")
         List<Json> list = sqlQuery.getResultList();
         list.forEach(runs::add);
         runs.forEach(run -> {
            Json jsrun = (Json) run;
            Object tagsOrNull = jsrun.get("tags");
            String tags = tagsOrNull == null ? null : tagsOrNull.toString();
            if (tags != null && !tags.isEmpty()) {
               jsrun.set("tags", Json.fromString(tags));
            } else {
               jsrun.set("tags", EMPTY_ARRAY);
            }
         });
         Json result = new Json.MapBuilder()
               // TODO: total does not consider the query but evaluating all the expressions would be expensive
               .add("total", trashed ? Run.count() : Run.count("trashed = false"))
               .add("runs", runs)
               .build();
         return Response.ok(result).build();
      }
   }

   @PermitAll
   @GET
   @Path("count")
   public Response runCount(@QueryParam("testId") Integer testId) {
      if (testId == null) {
         return Response.status(400).entity("Missing testId query param.").build();
      }
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         long total = Run.count("testid = ?1", testId);
         long active = Run.count("testid = ?1 AND trashed = false", testId);
         return Response.ok(new Json.MapBuilder()
               .add("total", total )
               .add("active", active)
               .add("trashed", total - active)
               .build()).build();
      }
   }

   private boolean hasRolesParam(String roles) {
      return roles != null && !roles.isEmpty() && !roles.equals("__all");
   }

   @PermitAll
   @GET
   @Path("list/{testId}/")
   public Response testList(@PathParam("testId") Integer testId,
                            @QueryParam("limit") Integer limit,
                            @QueryParam("page") Integer page,
                            @QueryParam("sort") String sort,
                            @QueryParam("direction") String direction,
                            @QueryParam("trashed") boolean trashed,
                            @QueryParam("tags") String tags
   ) {
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
         addDirection(sql, direction);
         sql.append(", jsonb_path_query_first(view_agg.view, '$.*.").append(accessor).append("')#>>'{}'");
         addDirection(sql, direction);
      } else {
         addOrderBy(sql, sort, direction);
      }
      addLimitOffset(sql, limit, page);
      Test test;
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         test = Test.find("id", testId).firstResult();
         if (test == null) {
            return Response.status(404).entity("Cannot find test ID " + testId).build();
         }
         Query query = em.createNativeQuery(sql.toString());
         query.setParameter(1, testId);
         Tags.addTagValues(tagsMap, query, 2);
         @SuppressWarnings("unchecked")
         List<Object[]> resultList = query.getResultList();
         Json.ArrayBuilder runs = Json.array();
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
            runs.add(Json.map()
                  .add("id", row[0])
                  .add("start", ((Timestamp) row[1]).getTime())
                  .add("stop", ((Timestamp) row[2]).getTime())
                  .add("testid", testId)
                  .add("access", row[3])
                  .add("owner", row[4])
                  .add("schema", schemas == null ? null : Json.fromString(schemas))
                  .add("view", view.build())
                  .add("trashed", row[7])
                  .add("description", row[8])
                  .add("tags", runTags == null ? null : Json.fromString(runTags))
                  .build());
         }
         Json response = new Json.MapBuilder()
               .add("runs", runs.build())
               .add("total", trashed ? Run.count("testid = ?1", testId) : Run.count("testid = ?1 AND trashed = false", testId))
               .build();
         return Response.ok(response).build();
      }
   }

   @RolesAllowed("tester")
   @POST
   @Path("{id}/trash")
   @Transactional
   public Response trash(@PathParam("id") Integer id, @QueryParam("isTrashed") Boolean isTrashed) {
      Response response = updateRun(id, run -> run.trashed = isTrashed == null || isTrashed);
      if (response.getStatus() == Response.Status.OK.getStatusCode()) {
         eventBus.publish(Run.EVENT_TRASHED, id);
      }
      return response;
   }

   @RolesAllowed("tester")
   @POST
   @Path("{id}/description")
   @Consumes(MediaType.TEXT_PLAIN)
   @Transactional
   public Response updateDescription(@PathParam("id") Integer id, String description) {
      // FIXME: fetchival stringifies the body into JSON string :-/
      return updateRun(id, run -> run.description = Util.destringify(description));
   }

   private Response updateRun(Integer id, Consumer<Run> consumer) {
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         Run run = Run.findById(id);
         if (run == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
         }
         consumer.accept(run);
         run.persistAndFlush();
         return Response.ok().build();
      }
   }

   @RolesAllowed("tester")
   @POST
   @Path("{id}/schema")
   @Consumes(MediaType.TEXT_PLAIN)
   @Transactional
   public Response updateSchema(@PathParam("id") Integer id, @QueryParam("path") String path, String schemaUri) {
      // FIXME: fetchival stringifies the body into JSON string :-/
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         Run run = Run.findById(id);
         if (run == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
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
         return Response.ok(schemas).build();
      }
   }
}
