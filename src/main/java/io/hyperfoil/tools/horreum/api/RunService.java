package io.hyperfoil.tools.horreum.api;

import io.agroal.api.AgroalDataSource;
import io.hyperfoil.tools.horreum.JsFetch;
import io.hyperfoil.tools.horreum.JsProxyObject;
import io.hyperfoil.tools.horreum.entity.converter.JsonContext;
import io.hyperfoil.tools.horreum.entity.converter.JsonResultTransformer;
import io.hyperfoil.tools.horreum.entity.json.Access;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Schema;
import io.hyperfoil.tools.horreum.entity.json.SchemaExtractor;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.entity.json.ViewComponent;
import io.hyperfoil.tools.yaup.HashedLists;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.json.ValueConverter;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.eventbus.EventBus;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.jboss.logging.Logger;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Path("/api/run")
@Consumes({MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public class RunService {
   private static final Logger log = Logger.getLogger(RunService.class);

   private static final int WITH_THRESHOLD = 2;

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

   @Inject
   AgroalDataSource dataSource;

   private Run findRun(@PathParam("id") Integer id, @QueryParam("token") String token) {
      try (CloseMe h1 = sqlService.withRoles(em, identity);
           CloseMe h2 = sqlService.withToken(em, token)) {
         return Run.find("id", id).firstResult();
      }
   }

   private Response runQuery(String query, Integer id, String token) {
      try (CloseMe h1 = sqlService.withRoles(em, identity);
           CloseMe h2 = sqlService.withToken(em, token);
           Connection connection = dataSource.getConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
         statement.setInt(1, id);
         ResultSet rs = SqlService.execute(statement);
         if (rs.next()) {
            return Response.ok(rs.getString(1)).build();
         } else {
            return Response.status(Response.Status.NOT_FOUND).build();
         }
      } catch (SQLException e) {
         log.error("Failed to read the run", e);
         return Response.serverError().build();
      }
   }

   @PermitAll
   @GET
   @Path("{id}")
   public Response getRun(@PathParam("id") Integer id,
                          @QueryParam("token") String token) {
      return runQuery("SELECT row_to_json(run) from run where id = ?", id, token);
   }

   @PermitAll
   @GET
   @Path("{id}/data")
   public Response getData(@PathParam("id") Integer id, @QueryParam("token") String token) {
      return runQuery("SELECT data#>>'{}' from run where id = ?", id, token);
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
      try (Connection connection = dataSource.getConnection();
           CloseMeJdbc h = sqlService.withRoles(connection, identity);
           PreparedStatement statement = connection.prepareStatement(UPDATE_TOKEN)) {
         if (token != null) {
            statement.setString(1, token);
         } else {
            statement.setNull(1, Types.VARCHAR);
         }
         statement.setInt(2, id);
         if (statement.executeUpdate() != 1) {
            return Response.serverError().entity("Token reset failed (missing permissions?)").build();
         } else {
            return (token != null ? Response.ok(token) : Response.noContent()).build();
         }
      } catch (SQLException e) {
         log.error("GET /id/resetToken failed", e);
         return Response.serverError().entity("Token reset failed").build();
      }
   }

   @RolesAllowed("tester")
   @POST
   @Path("{id}/updateAccess")
   // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
   public Response updateAccess(@PathParam("id") Integer id,
                                @QueryParam("owner") String owner,
                                @QueryParam("access") Access access) {
      try (Connection connection = dataSource.getConnection();
           CloseMeJdbc h = sqlService.withRoles(connection, identity);
           PreparedStatement statement = connection.prepareStatement(CHANGE_ACCESS)) {
         statement.setString(1, owner);
         statement.setInt(2, access.ordinal());
         statement.setInt(3, id);
         if (statement.executeUpdate() != 1) {
            return Response.serverError().entity("Access change failed (missing permissions?)").build();
         } else {
            return Response.accepted().build();
         }
      } catch (SQLException e) {
         log.error("GET /id/resetToken failed", e);
         return Response.serverError().entity("Access change failed").build();
      }
   }

   @PermitAll
   @GET
   @Path("{id}/structure")
   public Json getStructure(@PathParam("id") Integer id,
                            @QueryParam("token") String token) {
      Run found = findRun(id, token);
      if (found != null) {
         Json response = Json.typeStructure(found.data);
         return response;
      }
      return new Json(false);
   }

   @RolesAllowed(Roles.UPLOADER)
   @POST
   @Path("test/{testId}")
   @Consumes(MediaType.APPLICATION_JSON)
   public Response addToTest(@PathParam("testId") Integer testId, Run run) {
      run.testid = testId;
      return add(run);
   }

   @RolesAllowed(Roles.UPLOADER)
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
                                  Json data) {
      if (data == null) {
         return Response.status(Response.Status.BAD_REQUEST).entity("No data!").build();
      }
      Object foundTest = findIfNotSet(test, data);
      Object foundStart = findIfNotSet(start, data);
      Object foundStop = findIfNotSet(stop, data);
      Object foundDescription = findIfNotSet(description, data);

      try (CloseMe h = sqlService.withRoles(em, identity)) {
         if (schemaUri == null || schemaUri.isEmpty()) {
            schemaUri = data.getString("$schema");
         } else {
            data.set("$schema", schemaUri);
         }
         if (schemaUri == null) {
            for (Object value : data.values()) {
               if (value instanceof Json) {
                  Json json = (Json) value;
                  String uri = json.getString("$schema");
                  if (uri != null) {
                     Schema schema = Schema.find("uri", uri).firstResult();
                     if (schema != null) {
                        foundTest = findIfNotSet(foundTest, json, schema.testPath);
                        foundStart = findIfNotSet(foundStart, json, schema.startPath);
                        foundStop = findIfNotSet(foundStop, json, schema.stopPath);
                        foundDescription = findIfNotSet(foundDescription, json, schema.descriptionPath);
                     }
                  }
               }
            }
         } else {
            Schema schema = Schema.find("uri", schemaUri).firstResult();
            if (schema != null) {
               foundTest = findIfNotSet(foundTest, data, schema.testPath);
               foundStart = findIfNotSet(foundStart, data, schema.startPath);
               foundStop = findIfNotSet(foundStop, data, schema.stopPath);
               foundDescription = findIfNotSet(foundDescription, data, schema.descriptionPath);
            }
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

         Test testEntity = getOrCreateTest(testNameOrId, owner, access);
         if (testEntity == null) {
            return Response.serverError().entity("Failed to find or create test " + testNameOrId).build();
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

         return addAuthenticated(run);
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

   private Object findIfNotSet(Object current, Json json, String path) {
      if (current == null && path != null && !path.isEmpty()) {
         return Json.find(json, path);
      }
      return current;
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

   private Test getOrCreateTest(String testNameOrId, String owner, Access access) {
      Test testEntity = testService.getByNameOrId(testNameOrId);
      if (testEntity == null && !testNameOrId.matches("-?\\d+")) {
         testEntity = new Test();
         testEntity.name = testNameOrId;
         testEntity.description = "created by data upload";
         testEntity.owner = owner;
         testEntity.access = access;
         testService.addAuthenticated(testEntity);
      }
      return testEntity;
   }

   @RolesAllowed(Roles.UPLOADER)
   @POST
   @Consumes(MediaType.APPLICATION_JSON)
   @Transactional
   public Response add(Run run) {
      try (CloseMe h = sqlService.withRoles(em, identity)) {
         return addAuthenticated(run);
      }
   }

   private Response addAuthenticated(Run run) {
      Test test = Test.find("id", run.testid).firstResult();
      if (test == null) {
         return Response.serverError().entity("failed to find test " + run.testid).build();
      }

      if (run.owner == null || run.access == null) {
         return Response.status(400).entity("Missing access info (owner and access)").build();
      } else if (!identity.getRoles().contains(run.owner)) {
         return Response.status(400).entity("This user does not have permissions to upload run for owner=" + run.owner).build();
      }

      try {
         if (run.id == null) {
             em.persist(run);
             em.flush();
//            run.persistAndFlush(); //currently appears to be a bug in Panache where validation fails
         } else {
            em.merge(run);
         }
         //run.persistAndFlush();
      } catch (Exception e) {
         e.printStackTrace();
      }
      eventBus.publish(Run.EVENT_NEW, run);
      //run.persistAndFlush();

      StreamingOutput streamingOutput = outputStream -> {
         outputStream.write(run.id);
         outputStream.flush();
      };
      return Response.ok(streamingOutput).build();
      //return Response.ok(run.id).build();
   }

   @DenyAll
   @Deprecated
   @POST
   @Path("{id}/js")
   public Json jsAction(@PathParam("id") Integer id, @QueryParam("token") String token, String body) {
      Json rtrn = new Json();

      try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
         context.getBindings("js").putMember("_http", new JsFetch());
         Source fetchSource = Source.newBuilder("js", "fetch = async (url,options)=>{ return new Promise(async (resolve)=>{ const resp = _http.jsApply(url,options); resolve(resp); } );}", "fakeFetch").build();
         context.eval(fetchSource);

         Source asyncSource = Source.newBuilder("js", "const AsyncFunction = Object.getPrototypeOf(async function(){}).constructor; print('AsyncFunction',AsyncFunction);", "asyncfunction").build();

         context.eval(asyncSource);

         context.eval(org.graalvm.polyglot.Source.newBuilder("js", new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("jsonpath.js"))).lines()
            .parallel().collect(Collectors.joining("\n")), "jsonpath.js").build());
         context.eval(org.graalvm.polyglot.Source.newBuilder("js", new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("luxon.min.js"))).lines()
            .parallel().collect(Collectors.joining("\n")), "luxon.js").build());

         Value jsonPath = context.getBindings("js").getMember("jsonpath");
         Value dateTime = context.getBindings("js").getMember("luxon").getMember("DateTime");
         Value fetch = context.getBindings("js").getMember("fetch");

         Value factory = context.eval("js", "new Function('jsonpath','DateTime','fetch','return '+" + StringUtil.quote(body) + ")");
         Value fn = factory.execute(jsonPath, dateTime, fetch);

         Run run = findRun(id, token);


         JsonContext jsonContext = new JsonContext();

         Json runJson = Json.fromString(jsonContext.getContext(Run.class).toJson(run));
         Value returned = fn.execute(new JsProxyObject(runJson), jsonPath, dateTime);


         if (returned.toString().startsWith("Promise{[")) {//hack to check if the function returned a promise
            List<Value> resolved = new ArrayList<>();
            List<Value> rejected = new ArrayList<>();
            returned.invokeMember("then", new ProxyExecutable() {
               @Override
               public Object execute(Value... arguments) {
                  resolved.addAll(Arrays.asList(arguments));
                  return arguments;
               }
            }, new ProxyExecutable() {
               @Override
               public Object execute(Value... arguments) {
                  rejected.addAll(Arrays.asList(arguments));
                  return arguments;
               }
            });
            if (rejected.size() > 0) {
               returned = rejected.get(0);
            } else if (resolved.size() == 1) {
               returned = resolved.get(0);
            }
         }

         Object converted = ValueConverter.convert(returned);
         String convertedStr = ValueConverter.asString(converted);
         if (!convertedStr.isEmpty()) {
            rtrn.add(converted);
         }
      } catch (IOException e) {
         e.printStackTrace();
         return Json.fromThrowable(e);
      } catch (PolyglotException e) {
         e.printStackTrace();
         return Json.fromThrowable(e);
      }
      return rtrn;
   }

   private void addPaging(StringBuilder sql, Integer limit, Integer page, String sort, String direction) {
      addOrderBy(sql, sort, direction);
      addLimitOffset(sql, limit, page);
   }

   private void addOrderBy(StringBuilder sql, String sort, String direction) {
      sort = sort == null || sort.trim().isEmpty() ? "start" : sort;
      direction = direction == null || direction.trim().isEmpty() ? "Ascending" : direction;
      if (sort != null) {
         sql.append(" ORDER BY " + sort);
         addDirection(sql, direction);
      }
   }

   private void addDirection(StringBuilder sql, String direction) {
      if (direction != null) {
         if ("Ascending".equalsIgnoreCase(direction)) {
            sql.append(" ASC");
         } else {
            sql.append(" DESC");
         }
      }
      sql.append(" NULLS LAST");
   }

   private void addLimitOffset(StringBuilder sql, Integer limit, Integer page) {
      if (limit != null && limit > 0) {
         sql.append(" limit " + limit);
         if (page != null && page >= 0) {
            sql.append(" offset " + (limit * (page - 1)));
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
      try (Connection connection = dataSource.getConnection();
           CloseMeJdbc h = sqlService.withRoles(connection, identity);
           PreparedStatement findAutocomplete = connection.prepareStatement(FIND_AUTOCOMPLETE)) {
         findAutocomplete.setString(1, jsonpath);
         findAutocomplete.setString(2, incomplete);
         ResultSet rs = findAutocomplete.executeQuery();
         Json.ArrayBuilder array = Json.array();
         while (rs.next()) {
            String option = rs.getString(1);
            if (!option.matches("^[a-zA-Z0-9_-]*$")) {
               option = "\"" + option + "\"";
            }
            array.add(option);
         }
         return Response.ok(array.build()).build();
      } catch (SQLException e) {
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
         .append("FROM run JOIN test ON test.id = run.testId JOIN run_tags ON run_tags.runid = run.id WHERE ");
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

      sqlQuery.unwrap(org.hibernate.query.Query.class).setResultTransformer(JsonResultTransformer.INSTANCE);
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)){
         Json runs = new Json(true);
         sqlQuery.getResultList().forEach(runs::add);
         runs.forEach(run -> {
            Json jsrun = (Json) run;
            String tags = jsrun.getString("tags");
            if (tags != null && !tags.isEmpty()) {
               jsrun.set("tags", Json.fromString(tags));
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
   public long runCount() {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         return Run.count();
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
                            @QueryParam("trashed") boolean trashed) {
      StringBuilder sql = new StringBuilder("WITH schema_agg AS (")
            .append("    SELECT DISTINCT ON(schemaid) jsonb_object_agg(schemaid, uri) AS schemas, rs.runid FROM run_schemas rs GROUP BY schemaid, rs.runid")
            .append("), view_agg AS (")
            .append("    SELECT jsonb_object_agg(coalesce(vd.vcid, 0), vd.object) AS view, vd.runid FROM view_data vd GROUP BY vd.runid")
            .append(") SELECT run.id, run.start, run.stop, run.owner, schema_agg.schemas::::text AS schemas, view_agg.view#>>'{}' AS view, ")
            .append("run.trashed, run.description, run_tags.tags::::text FROM run ")
            .append("LEFT JOIN schema_agg ON schema_agg.runid = run.id ")
            .append("LEFT JOIN view_agg ON view_agg.runid = run.id ")
            .append("LEFT JOIN run_tags ON run_tags.runid = run.id ")
            .append("WHERE run.testid = ? ");
      if (!trashed) {
         sql.append(" AND NOT run.trashed ");
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
      try (CloseMe h = sqlService.withRoles(em, identity)) {
         test = Test.find("id", testId).firstResult();
         Query query = em.createNativeQuery(sql.toString());
         query.setParameter(1, testId);
         List<Object[]> resultList = query.getResultList();
         Json.ArrayBuilder runs = Json.array();
         for (Object[] row : resultList) {
            String viewString = (String) row[5];
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
            String schemas = (String) row[4];
            String tags = (String) row[8];
            runs.add(Json.map()
                  .add("id", row[0])
                  .add("start", ((Timestamp) row[1]).getTime())
                  .add("stop", ((Timestamp) row[2]).getTime())
                  .add("testid", testId)
                  .add("owner", row[3])
                  .add("schema", schemas == null ? null : Json.fromString(schemas))
                  .add("view", view.build())
                  .add("trashed", row[6])
                  .add("description", row[7])
                  .add("tags", tags == null ? null : Json.fromString(tags))
                  .build());
         }
         Json response = new Json.MapBuilder()
               .add("runs", runs.build())
               .add("total", trashed ? Run.count("testid = ?1", testId) : Run.count("testid = ?1 AND trashed = false", testId))
               .build();
         return Response.ok(response).build();
      }
   }

   @Deprecated
   @PermitAll
   @POST
   @Path("list/{testId}/")
   public Response testList(@PathParam("testId") Integer testId,
                            @QueryParam("limit") Integer limit,
                            @QueryParam("page") Integer page,
                            @QueryParam("sort") String sort,
                            @QueryParam("direction") String direction,
                            Json options) {
      try (CloseMe h = sqlService.withRoles(em, identity)) {
         Test test = Test.find("id", testId).firstResult();
         if (test == null) {
            return Response.serverError().entity("failed to find test " + testId).build();
         }
         if (options == null || options.isEmpty()) {
            return Response.serverError().entity("could not read post body as json").build();
         }
      }
      String sql = getTestListQuery(testId, limit, page, sort, direction, options);
      try (Connection connection = dataSource.getConnection();
           CloseMeJdbc h = sqlService.withRoles(connection, identity);
           Statement statement = connection.createStatement()){
         return Response.ok(SqlService.fromResultSet(statement.executeQuery(sql))).build();
      } catch (SQLException e) {
         log.error("POST /list/testId failed", e);
         return Response.serverError().entity(Json.fromThrowable(e)).build();
      }
   }

   private String getTestListQuery(Integer testId, Integer limit, Integer page, String sort, String direction, Json options) {
      StringBuilder sql = new StringBuilder();

      if (options.has("map") && options.get("map") instanceof Json) {

         Json map = options.getJson("map");
         System.out.println("RunService.testList map="+map.toString(2));
         List<String> jsonpaths = map.values().stream().map(Object::toString).filter(v -> v.startsWith("$.")).collect(Collectors.toList());
         HashedLists grouped = StringUtil.groupCommonPrefixes(jsonpaths, (a, b) -> {
            String prefix = a.substring(0, StringUtil.commonPrefixLength(a, b));
            System.out.println("startingPrefix="+prefix);
            if (prefix.length() == 0) {
               return 0;
            }
            //TODO shorten prefix if there are an odd number of ( or [
            //TODO this unnecessarily shortens .**.JAVA_OPTS, need to identify when prefix ends with a full key
            if (prefix.contains(".")) {
               if(!a.endsWith(prefix) && !b.endsWith(prefix)) {
                  prefix = prefix.substring(0, prefix.lastIndexOf("."));
               }
            }
            if (prefix.endsWith(".**")){
               System.out.println("trim off .**");
               prefix = prefix.substring(0,prefix.length()-".**".length());
            }
            System.out.println("prefix="+prefix);
            return prefix.length();
         });
         Set<String> keys = new HashSet<>(grouped.keys());
         keys.forEach(key -> {
            List<String> list = grouped.get(key);
            if (list.size() < WITH_THRESHOLD) {
               grouped.removeAll(key);
            } else if (key.equalsIgnoreCase("$.")) {
               grouped.removeAll(key);
            }
         });
         if (!grouped.isEmpty()) {
            //create a WITH MATERIALIZED
            Map<String, String> pathToPrefix = new HashMap();
            Map<String, String> pathToGroup = new HashMap<>();
            keys = grouped.keys();
            sql.append("WITH jpgroup AS MATERIALIZED ( select id,start,stop,data,testId");
            AtomicInteger counter = new AtomicInteger(0);
            for (String key : keys) {
               sql.append(",");
               sql.append("jsonb_path_query_first(data,").append(StringUtil.quote(key, "'")).append(") as g").append(counter.get());

               List<String> paths = grouped.get(key);
               for (String path : paths) {
                  pathToGroup.putIfAbsent(path, "g" + counter.get());
                  pathToPrefix.putIfAbsent(path, key);
               }
               counter.incrementAndGet();
            }
            sql.append(" from run where testId = ").append(testId);
            addPaging(sql, limit, page, sort, direction);
            sql.append(") select id");//end with
            map.forEach((key, value) -> {
               sql.append(", ");
               if (value.toString().startsWith("$.")) {
                  String path = value.toString();
                  if (pathToGroup.containsKey(path)) {
                     String group = pathToGroup.get(path);
                     String prefix = pathToPrefix.get(path);
                     String fixedPath = "$" + path.substring(prefix.length());

                     sql.append("jsonb_path_query_first(").append(group).append(",");
                     sql.append(StringUtil.quote(fixedPath, "'"));
                     sql.append(") as ");
                     sql.append(key);
                  } else {
                     sql.append("jsonb_path_query_first(data,");
                     sql.append(StringUtil.quote(value.toString(), "'"));
                     sql.append(") as ");
                     sql.append(key);
                  }
               } else {
                  if (key.equals(value)) {
                     sql.append(key);
                  } else {
                     sql.append(value);
                     sql.append(" as ");
                     sql.append(key);
                  }

               }
            });
            sql.append(" from jpgroup");
         } else {
            sql.append("select id");
            map.forEach((key, value) -> {
               sql.append(", ");//always needed because we always select id
               if (value.toString().startsWith("$.")) {
                  sql.append("jsonb_path_query_first(data,");
                  sql.append(StringUtil.quote(value.toString(), "'"));
                  sql.append(") as ");
                  sql.append(key);
               } else if (value.toString().contains(("jsonb_"))){
                  sql.append(value.toString());
                  sql.append(" as ");
                  sql.append(key);
               }else {

                  sql.append(key);
               }
            });
            sql.append(" from run where testId = ").append(testId);
            if (options.has("contains") && options.get("contains") instanceof Json) {
               sql.append(" and data @> ");
               sql.append(StringUtil.quote(options.getJson("contains").toString(0), "'"));
            }
            addPaging(sql, limit, page, sort, direction);
         }
      } else {
         sql.append("select id,start,stop,testId from run where testId = ").append(testId);
         if (options.has("contains") && options.get("contains") instanceof Json) {
            sql.append(" and data @> ");
            sql.append(StringUtil.quote(options.getJson("contains").toString(0), "'"));
         }
         addPaging(sql, limit, page, sort, direction);
      }
      return sql.toString();
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
      return updateRun(id, run -> run.description = destringify(description));
   }

   private static String destringify(String str) {
      if (str == null || str.isEmpty()) {
         return str;
      }
      if (str.charAt(0) == '"' && str.charAt(str.length() - 1) == '"') {
         return str.substring(1, str.length() - 1);
      } else {
         return str;
      }
   }

   private Response updateRun(Integer id, Consumer<Run> consumer) {
      try (CloseMe closeMe = sqlService.withRoles(em, identity)) {
         Run run = Run.findById(id);
         if (run == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
         }
         consumer.accept(run);
         run.persistAndFlush();
         return Response.ok().build();
      }
   }
}
