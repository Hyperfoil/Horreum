package io.hyperfoil.tools.repo.api;

import io.hyperfoil.tools.repo.JsFetch;
import io.hyperfoil.tools.repo.JsFileSystem;
import io.hyperfoil.tools.repo.JsProxyObject;
import io.hyperfoil.tools.repo.entity.converter.JsonContext;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.json.ValueConverter;
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import javax.annotation.security.DenyAll;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@DenyAll
@Path("/api/js")
@Consumes({MediaType.APPLICATION_JSON,MediaType.APPLICATION_FORM_URLENCODED})
@Produces(MediaType.APPLICATION_JSON)
public class JsService {

   private JsonContext jsonContext = new JsonContext();

   @POST
   @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
   public Json run(String input){
      System.out.println("text "+input);
      return runJavascript(input);
   }

   @POST
   @Consumes(MediaType.APPLICATION_JSON)
   public Json run(Json input){
      if(input == null){
         Json error = new Json();
         error.set("error","missing json input");
         return error;
      }
      if(!input.has("method")){
         Json error = new Json();
         error.set("error","missing method");
         error.set("input",input);
         return error;
      }

      String code = input.getString("method");
      return runJavascript(code,input.getJson("args",new Json()).values().toArray());
   }

   public Json toJson(Object object){
      Json rtrn = Json.fromString(jsonContext.getContext(object.getClass()).toJson(object));

      return rtrn;
   }

   public Json runJavascript(String function, Object...params){
      Json rtrn = new Json(false);

      try(Context context = Context.newBuilder("js")
         .allowHostAccess(HostAccess.ALL) //workaround until below issue is resolved
         .allowIO(true)
         .allowAllAccess(true)
         .fileSystem(new JsFileSystem())
         //.allowHostAccess(HostAccess.EXPLICIT) //cannot use until https://github.com/graalvm/graaljs/issues/206 is resolved
         .build()
      ) {
         context.getBindings("js").putMember("_http",new JsFetch());
         Source fetchSource = Source.newBuilder("js","fetch = async (url,options)=>{ return new Promise(async (resolve)=>{ const resp = _http.jsApply(url,options); resolve(new Response(options,resp)); } );}","fakeFetch").build();
         context.eval(fetchSource);
         Source sqlSource = Source.newBuilder("js","sql = async (query)=>{ print('sql',query); return new Promise(async (resolve)=>{ const resp = _http.jsApply('http://localhost:8080/api/sql?q='+encodeURI(query),{}); resolve(resp); } ); }","sql").build();
         context.eval(sqlSource);
         context.eval("js","global.btoa = (str)=>Java.type('io.quarkus.test.JsFetch').btoa(str)");
         context.eval("js","global.atob = (str)=>Java.type('io.quarkus.test.JsFetch').atob(str)");

         context.eval(org.graalvm.polyglot.Source.newBuilder("js", new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("jsonpath.js"))).lines()
            .parallel().collect(Collectors.joining("\n")), "jsonpath.js").build());
         context.eval(org.graalvm.polyglot.Source.newBuilder("js", new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("luxon.min.js"))).lines()
            .parallel().collect(Collectors.joining("\n")), "luxon.js").build());

         Value jsonPath = context.getBindings("js").getMember("jsonpath");
         Value dateTime = context.getBindings("js").getMember("luxon").getMember("DateTime");
         Value fetch = context.getBindings("js").getMember("fetch");

         Value factory = context.eval("js","new Function('jsonpath','DateTime','fetch','return '+"+ StringUtil.quote(function)+")");
         Value fn = factory.execute(jsonPath,dateTime,fetch);

         List<Object> executeArgs = new ArrayList<>();
         for(Object param : params){
            if(param instanceof Json){
               executeArgs.add(new JsProxyObject((Json)param));
            }else{
               executeArgs.add(param);
            }
         }
         Value returned = fn.execute(executeArgs.toArray());

         if(returned.toString().startsWith("Promise{[")){//hack to check if the function returned a promise
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
            if(rejected.size() > 0){
               returned = rejected.get(0);
            }else if(resolved.size() == 1){
               returned = resolved.get(0);
            }
         }
         Object converted = ValueConverter.convert(returned);
         if(converted instanceof JsProxyObject){
            System.out.println("converted is JSProxyObject for "+((JsProxyObject)converted).getJson());
            return ((JsProxyObject)converted).getJson();
         }
         System.out.println("converted "+converted.getClass()+" "+converted);
         String convertedStr = ValueConverter.asString(converted);
         if(!convertedStr.isEmpty()){
            rtrn.add(converted);
         }

      } catch (IOException e) {
         e.printStackTrace();
         return Json.fromThrowable(e);
      } catch (PolyglotException e){
         e.printStackTrace();
         return Json.fromThrowable(e);
         
      }
      return rtrn;
   }
}
