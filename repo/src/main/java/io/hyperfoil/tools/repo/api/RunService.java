package io.hyperfoil.tools.repo.api;

import io.hyperfoil.tools.repo.JsFetch;
import io.hyperfoil.tools.repo.JsProxyObject;
import io.hyperfoil.tools.repo.entity.converter.JsonContext;
import io.hyperfoil.tools.repo.entity.json.Run;
import io.hyperfoil.tools.repo.entity.json.Test;
import io.hyperfoil.tools.yaup.HashedLists;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.json.JsonValidator;
import io.hyperfoil.tools.yaup.json.ValueConverter;
import io.vertx.core.eventbus.EventBus;
import org.apache.commons.collections.map.HashedMap;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

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
import java.time.Instant;
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


   private static final int WITH_THRESHOLD = 2;

   @Inject
   EntityManager em;

   @GET
   @Path("{id}")
   public Run getRun(@PathParam("id") Integer id) {
      return Run.find("id", id).firstResult();
   }

   @Inject
   SqlService sqlService;


   @Inject
   EventBus eventBus;

   @Inject
   TestService testService;

   @POST
   @Path("test/{testId}")
   @Consumes(MediaType.APPLICATION_JSON)
   public Response addToTest(@PathParam("testId") Integer testId, Run run) {
      run.testId = testId;
      return add(run);
   }

   @POST
   @Path("hyperfoil")
   public Response addHyperfoilRun(Json json){
      return addRunFromData("$.info.startTime","$.info.terminateTime","$.info.benchmark",json);
   }

   @POST
   @Path("data")
   public Response addRunFromData(@QueryParam("start") String start, @QueryParam("stop") String stop, @QueryParam("test") String test, Json data) {
      Object foundTest = test != null && test.startsWith("$.") ? Json.find(data, test, "") : test;
      Object foundStart = start != null && start.startsWith("$.") ? Json.find(data, start, "") : start;
      Object foundStop = stop != null && stop.startsWith("$.") ? Json.find(data, stop, "") : stop;

      if (foundTest == null || foundTest.toString().trim().isEmpty()) {
         return Response.noContent().entity("cannot find " + test + " in data").build();
      }

      Test testEntity = testService.getByNameOrId(foundTest.toString());
      if (testEntity == null && !foundTest.toString().matches("-?\\d+")) {
         testEntity = new Test();
         testEntity.name = foundTest.toString();
         testEntity.description = "created by data upload";
         Object response = testService.add(testEntity);
         if (response instanceof Test) {
            testEntity = (Test) response;
         } else {
            System.out.println("addTest.response = " + response);
         }
      }
      if (testEntity == null) {
         return Response.serverError().entity("failed to find or create test " + foundTest.toString()).build();
      }

      Run run = new Run();
      run.testId = testEntity.id;
      run.start = foundStart instanceof Number ? Instant.ofEpochMilli(Long.parseLong(foundStart.toString())) :Instant.parse(foundStart.toString());
      run.stop = foundStop instanceof Number ? Instant.ofEpochMilli(Long.parseLong(foundStop.toString())) :Instant.parse(foundStop.toString());
      run.data = data;

      Object runResponse = add(run);

      return Response.ok(run.id).build();
   }

   @POST
   @Consumes(MediaType.APPLICATION_JSON)
   @Transactional
   public Response add(Run run) {
      Test test = Test.find("id", run.testId).firstResult();
      if (test == null) {
         return Response.serverError().entity("failed to find test " + run.testId).build();
      }
      if (test.schema != null && !test.schema.isEmpty()) {
         JsonValidator validator = new JsonValidator(test.schema);

         Json result = validator.validate(run.data);
         if (result.has("messages") && result.has("details")) {
            return Response.serverError().entity(result.toString(2)).build();
         }
      }
      try {
         if (run.id == null) {
            run.persistAndFlush();
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

   @POST
   @Path("{id}/js")
   public Json jsAction(@PathParam("id") Integer id, String body) {
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

         Run run = getRun(id);


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

   @GET
   @Path("list")
   public Json list(@QueryParam("limit") Integer limit, @QueryParam("page") Integer page, @QueryParam("sort") String sort, @QueryParam("direction") String direction) {
      StringBuilder sql = new StringBuilder("select id,start,stop,testId from run");
      addPaging(sql, limit, page, sort, direction);
      return sqlService.get(sql.toString());
   }

   @GET
   @Path("list/{testId}/")
   public Response testList(@PathParam("testId") Integer testId, @QueryParam("limit") Integer limit, @QueryParam("page") Integer page, @QueryParam("sort") String sort, @QueryParam("direction") String direction) {
      Test test = Test.find("id", testId).firstResult();
      if (test == null) {
         return Response.serverError().entity("failed to find test " + testId).build();
      }
      StringBuilder sql = new StringBuilder("select id,start,stop,testId from run where testId = " + testId);
      addPaging(sql, limit, page, sort, direction);
      return Response.ok(sqlService.get(sql.toString())).build();
   }

   @POST
   @Path("list/{testId}/")
   public Response testList(@PathParam("testId") Integer testId, @QueryParam("limit") Integer limit, @QueryParam("page") Integer page, @QueryParam("sort") String sort, @QueryParam("direction") String direction, Json options) {
      Test test = Test.find("id", testId).firstResult();
      if (test == null) {
         return Response.serverError().entity("failed to find test " + testId).build();
      }
      if (options == null || options.isEmpty()) {
         return Response.serverError().entity("could not read post body as json").build();
      }
      StringBuilder sql = new StringBuilder();

      if (options.has("map") && options.get("map") instanceof Json) {

         Json map = options.getJson("map");
         List<String> jsonpaths = map.values().stream().map(v -> v.toString()).filter(v -> v.startsWith("$.")).collect(Collectors.toList());
         HashedLists grouped = StringUtil.groupCommonPrefixes(jsonpaths, (a, b) -> {
            String prefix = a.substring(0, StringUtil.commonPrefixLength(a, b));
            if (prefix.length() == 0) {
               return 0;
            }
            //TODO shorten prefix if there are an odd number of ( or [
            if (prefix.contains(".")) {
               prefix = prefix.substring(0, prefix.lastIndexOf("."));
            }
            return prefix.length();
         });
         Set<String> keys = new HashSet(grouped.keys());
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
            Map<String, String> pathToPrefix = new HashedMap();
            Map<String, String> pathToGroup = new HashMap<>();
            keys = grouped.keys();
            sql.append("WITH jpgroup AS MATERIALIZED ( select id,start,stop,data,testId");
            AtomicInteger counter = new AtomicInteger(0);
            for (String key : keys) {
               sql.append(",");
               sql.append("jsonb_path_query_first(data," + StringUtil.quote(key, "'") + ") as g" + counter.get());

               List<String> paths = grouped.get(key);
               for (String path : paths) {
                  pathToGroup.putIfAbsent(path, "g" + counter.get());
                  pathToPrefix.putIfAbsent(path, key);
               }
               counter.incrementAndGet();
            }
            sql.append(" from run where testId = " + testId);
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

                     sql.append("jsonb_path_query_first(" + group + ",");
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
               } else {
                  sql.append(key);
               }
            });
            sql.append(" from run where testId = " + testId);
            if (options.has("contains") && options.get("contains") instanceof Json) {
               sql.append(" and data @> ");
               sql.append(StringUtil.quote(options.getJson("contains").toString(0), "'"));
            }
            addPaging(sql, limit, page, sort, direction);
         }
      } else {
         sql.append("select id,start,stop,testId from run where testId = " + testId);
         if (options.has("contains") && options.get("contains") instanceof Json) {
            sql.append(" and data @> ");
            sql.append(StringUtil.quote(options.getJson("contains").toString(0), "'"));
         }
         addPaging(sql, limit, page, sort, direction);
      }
      return Response.ok(sqlService.get(sql.toString())).build();
   }
}
