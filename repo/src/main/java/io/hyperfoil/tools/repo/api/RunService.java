package io.hyperfoil.tools.repo.api;

import io.agroal.api.AgroalDataSource;
import io.hyperfoil.tools.repo.JsFetch;
import io.hyperfoil.tools.repo.JsProxyObject;
import io.hyperfoil.tools.repo.entity.converter.JsonContext;
import io.hyperfoil.tools.repo.entity.json.Access;
import io.hyperfoil.tools.repo.entity.json.Run;
import io.hyperfoil.tools.repo.entity.json.Schema;
import io.hyperfoil.tools.repo.entity.json.Test;
import io.hyperfoil.tools.repo.entity.json.ViewComponent;
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
import java.util.stream.Collectors;

@Path("/api/run")
@Consumes({MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public class RunService {
   private static final Logger log = Logger.getLogger(RunService.class);

   private static final int WITH_THRESHOLD = 2;

   private static final String FILTER_JSONPATH_EXISTS = "SELECT id FROM run WHERE jsonb_path_exists(data, ? ::jsonpath)";
   private static final String FILTER_JSONPATH_EXISTS_WITH_ROLE = "SELECT id FROM run WHERE jsonb_path_exists(data, ? ::jsonpath) AND owner = ANY(?)";

   //@formatter:off
   private static final String FIND_AUTOCOMPLETE = "SELECT * FROM (" +
            "SELECT DISTINCT jsonb_object_keys(q) AS key " +
            "FROM run, jsonb_path_query(run.data, ? ::jsonpath) q " +
            "WHERE jsonb_typeof(q) = 'object') AS keys " +
         "WHERE keys.key LIKE CONCAT(?, '%');";
   private static final String FIND_ACCESSORS =
         "SELECT accessor, (SELECT '$' || se.jsonpath) as path, schema.uri, schema.id as schemaid, run.id as runid " +
         "FROM run JOIN schema ON schema.uri IN (" +
            "SELECT jsonb_path_query(run.data, '$.\\$schema'::jsonpath)#>>'{}' " +
         ") LEFT JOIN schemaextractor se on se.schema_id = schema.id " +
         "WHERE run.testid = ?" +
         "UNION SELECT accessor, (SELECT '$.*' || se.jsonpath) as path, schema.uri, schema.id as schemaid, run.id as runid " +
         "FROM run JOIN schema ON schema.uri IN (" +
            "SELECT jsonb_path_query(run.data, '$.*.\\$schema'::jsonpath)#>>'{}' " +
         ") LEFT JOIN schemaextractor se on se.schema_id = schema.id " +
         "WHERE run.testid = ?";
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

   @PermitAll
   @GET
   @Path("{id}")
   public Run getRun(@PathParam("id") Integer id,
                     @QueryParam("token") String token) {
      try (CloseMe h1 = sqlService.withRoles(em, identity);
           CloseMe h2 = sqlService.withToken(em, token)) {
         return Run.find("id", id).firstResult();
      }
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
      Run found = getRun(id, token);
      if(found!=null){
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
      run.testId = testId;
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
                                  Json data) {
      if (data == null) {
         return Response.status(Response.Status.BAD_REQUEST).entity("No data!").build();
      }
      try (CloseMe h = sqlService.withRoles(em, identity)) {
         if (schemaUri == null || schemaUri.isEmpty()) {
            schemaUri = data.getString("$schema");
         } else {
            data.set("$schema", schemaUri);
         }
         Schema schema = Schema.find("uri", schemaUri).firstResult();

         Object foundTest = findIfNotSet(test, data, schema == null ? null : schema.testPath);
         Object foundStart = findIfNotSet(start, data, schema == null ? null : schema.startPath);
         Object foundStop = findIfNotSet(stop, data, schema == null ? null : schema.stopPath);

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
         run.testId = testEntity.id;
         run.start = startInstant;
         run.stop = stopInstant;
         run.data = data;
         run.owner = owner;
         run.access = access;

         return addAuthenticated(run);
      }
   }

   private Object findIfNotSet(String value, Json data, String path) {
      if (value != null) {
         if (value.startsWith("$.")) {
            return Json.find(data, value, null);
         } else {
            return value;
         }
      } else if (path != null) {
         return Json.find(data, path, null);
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
      Test test = Test.find("id", run.testId).firstResult();
      if (test == null) {
         return Response.serverError().entity("failed to find test " + run.testId).build();
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
      eventBus.publish("new/run", run);
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

         Run run = getRun(id, token);


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
      sort = sort == null || sort.trim().isEmpty() ? "start" : sort;
      direction = direction == null || direction.trim().isEmpty() ? "Ascending" : direction;
      if (sort != null) {
         sql.append(" order by " + sort);
         if (direction != null) {
            if ("Ascending".equalsIgnoreCase(direction)) {
               sql.append(" ASC");
            } else {
               sql.append(" DESC");
            }
         }
      }
      if (limit != null && limit > 0) {
         sql.append(" limit " + limit);
         if (page != null && page >= 0) {
            sql.append(" offset " + (limit * page));
         }
      }
   }

   @PermitAll
   @GET
   @Path("list")
   public Response list(@QueryParam("limit") Integer limit,
                    @QueryParam("page") Integer page,
                    @QueryParam("sort") String sort,
                    @QueryParam("direction") String direction) {
      StringBuilder sql = new StringBuilder("select id,start,stop,testId,owner,access,token from run");
      addPaging(sql, limit, page, sort, direction);
      try (Connection connection = dataSource.getConnection();
           CloseMeJdbc h = sqlService.withRoles(connection, identity);
           Statement statement = connection.createStatement()){
         statement.executeQuery(sql.toString());
         return Response.ok(SqlService.fromResultSet(statement.getResultSet())).build();
      } catch (SQLException e) {
         log.error("/list failed", e);
         return Response.serverError().entity(Json.fromThrowable(e)).build();
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
   @Path("filter")
   public Response filter(@QueryParam("query") String query,
                          @QueryParam("matchAll") boolean matchAll,
                          @QueryParam("roles") String roles) {
      if ((query == null || query.isEmpty()) && !hasRolesParam(roles)) {
         return Response.noContent().build();
      }
      query = query.trim();

      try (Connection connection = dataSource.getConnection()) {
         PreparedStatement statement = null;
         try (CloseMeJdbc h = sqlService.withRoles(connection, identity)){
            if (!identity.isAnonymous() && hasRolesParam(roles)) {
               statement = connection.prepareStatement(FILTER_JSONPATH_EXISTS_WITH_ROLE);
               Object[] actualRoles;
               if (roles.equals("__my")) {
                  actualRoles = identity.getRoles().toArray();
               } else {
                  actualRoles = new Object[]{ roles };
               }
               statement.setArray(2, connection.createArrayOf("text", actualRoles));
            } else {
               statement = connection.prepareStatement(FILTER_JSONPATH_EXISTS);
            }

            if (query == null || query.isEmpty()) {
               statement.setString(1, "$");
            } else if (query.startsWith("$")) {
               statement.setString(1, query);
            } else if (query.startsWith("@")) {
               statement.setString(1, "$.** ? (" + query + ")");
            } else {
               Set<Integer> ids = null;
               for (String s : query.split("([ \t\n,])|OR")) {
                  if (s.isEmpty()) {
                     continue;
                  }
                  statement.setString(1, "$.**." + s);
                  ResultSet rs = statement.executeQuery();
                  if (matchAll) {
                     Set<Integer> keyIds = new HashSet<>();
                     while (rs.next()) {
                        keyIds.add(rs.getInt(1));
                     }
                     if (ids == null) {
                        ids = keyIds;
                     } else {
                        ids.retainAll(keyIds);
                     }
                  } else {
                     if (ids == null) {
                        ids = new HashSet<>();
                     }
                     while (rs.next()) {
                        ids.add(rs.getInt(1));
                     }
                  }
               }
               Json.ArrayBuilder array = Json.array();
               ids.forEach(array::add);
               return Response.ok(array.build()).build();
            }
            ResultSet rs = statement.executeQuery();
            Json.ArrayBuilder array = Json.array();
            while (rs.next()) {
               array.add(rs.getInt(1));
            }
            return Response.ok(array.build()).build();
         } finally {
            if (statement != null) {
               statement.close();
            }
         }
      } catch (SQLException e) {
         return Response.status(400).entity("Failed processing query '" + query + "':\n" + e.getLocalizedMessage()).build();
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
                            @QueryParam("direction") String direction) {
      // TODO: this is combining EntityManager and JDBC access :-/
      StringBuilder sql = new StringBuilder("WITH access AS (").append(FIND_ACCESSORS).append(") ")
         .append("SELECT run.id, run.start, run.stop, run.testId, run.owner, (")
         .append("    SELECT DISTINCT ON(schemaid) jsonb_object_agg(schemaid, uri) FROM access WHERE run.id = access.runid GROUP BY schemaid")
         .append(")::text as schemas");
      Test test;
      try (CloseMe h = sqlService.withRoles(em, identity)) {
         test = Test.find("id", testId).firstResult();
         if (test == null) {
            return Response.serverError().entity("failed to find test " + testId).build();
         }
         if (test.defaultView != null) {
            for (ViewComponent c : test.defaultView.components) {
               for (String accessor : c.accessors()) {
                  sql.append(", ").append(ViewComponent.isArray(accessor) ? "jsonb_path_query_array" : "jsonb_path_query_first").append("(data, (");
                  sql.append("   SELECT path FROM access WHERE access.accessor = ? AND access.runid = run.id");
                  sql.append(")::jsonpath)#>>'{}'");
               }
            }
         }
      }
      sql.append(" FROM run WHERE run.testid = ?");
      // TODO: use parameters in paging
      addPaging(sql, limit, page, sort, direction);
      try (Connection connection = dataSource.getConnection();
           CloseMeJdbc h = sqlService.withRoles(connection, identity);
           PreparedStatement statement = connection.prepareStatement(sql.toString())){
         statement.setInt(1, testId);
         statement.setInt(2, testId);
         int counter = 1;
         if (test.defaultView != null) {
            for (ViewComponent c : test.defaultView.components) {
               for (String accessor : c.accessors()) {
                  if (ViewComponent.isArray(accessor)) {
                     accessor = ViewComponent.arrayName(accessor);
                  }
                  statement.setString(2 + counter++, accessor);
               }
            }
         }
         statement.setInt(2 + counter, testId);
         ResultSet resultSet = statement.executeQuery();
         Json.ArrayBuilder jsonResult = Json.array();
         while (resultSet.next()) {
            Json.ArrayBuilder view = Json.array();
            int i = 7;
            for (ViewComponent c : test.defaultView.components) {
               String[] accessors = c.accessors();
               if (accessors.length == 1) {
                  String value = resultSet.getString(i++);
                  if (ViewComponent.isArray(accessors[0])) {
                     view.add(Json.fromString(value));
                  } else {
                     view.add(value);
                  }
               } else {
                  Json.MapBuilder map = Json.map();
                  view.add(map);
                  for (String accessor : accessors) {
                     String value = resultSet.getString(i++);
                     if (ViewComponent.isArray(accessor)) {
                        map.add(ViewComponent.arrayName(accessor), Json.fromString(value));
                     } else {
                        map.add(accessor, value);
                     }
                  }
               }
            }
            String schemas = resultSet.getString(6);
            jsonResult.add(Json.map()
                  .add("id", resultSet.getInt(1))
                  .add("start", resultSet.getTimestamp(2).getTime())
                  .add("stop", resultSet.getTimestamp(3).getTime())
                  .add("testId", resultSet.getInt(4))
                  .add("owner", resultSet.getString(5))
                  .add("schema", schemas == null ? null : Json.fromString(schemas))
                  .add("view", view.build()).build());
         }
         return Response.ok(jsonResult.build()).build();
      } catch (SQLException e) {
         log.errorf(e, "GET /list/testId failed, query was:\n%s", sql.toString());
         return Response.serverError().entity(Json.fromThrowable(e)).build();
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
}
