package io.hyperfoil.tools.repo.api;

import io.hyperfoil.tools.repo.entity.json.Hook;
import io.hyperfoil.tools.repo.entity.json.Run;
import io.hyperfoil.tools.repo.entity.json.Test;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.postgresql.util.PSQLException;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Path("/api/hook")
@Consumes({MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class HookService {

   private static int getIntFromEnv(String var, int def) {
      String env = System.getenv(var);
      if (env == null || env.isEmpty()) return def;
      return Integer.parseInt(env);
   }

   @Inject
   EntityManager em;

   @Inject
   io.vertx.reactivex.core.Vertx reactiveVertx;

   WebClient http1xClient;

   @PostConstruct()
   public void postConstruct(){
      System.out.println("HookService.postConstruct");
      int maxConnections = getIntFromEnv("MAX_CONNECTIONS", 20);
      WebClientOptions options = new WebClientOptions()
         .setFollowRedirects(true)
         .setMaxPoolSize(maxConnections)
         .setConnectTimeout(2_000) // only wait 2s
         .setKeepAlive(false);
      http1xClient = WebClient.create(reactiveVertx, new WebClientOptions(options).setProtocolVersion(HttpVersion.HTTP_1_1));
   }
   private void tellHooks(String type,Integer id,Object value){
      List<Hook> hooks = getEventHooks(type,id);
//      Hook h = new Hook();
//      h.url="http://laptop:8080/api/log";
//      hooks.add(h);
//      hooks.add(h);
      final Jsonb jsonb = JsonbBuilder.create();
      String json = jsonb.toJson(value);
      System.out.println("toJson -> "+json);
      for(Hook hook : hooks){
         System.out.println("Hook:"+hook.url);
         try{
            String input = hook.url.startsWith("http") ? hook.url : "http://"+hook.url;
            URL url = new URL(input);
            http1xClient.post(url.getPort(),url.getHost(),url.getFile())
               .putHeader("Content-Type", "application/json")
               .sendBuffer(Buffer.buffer(json), ar->{
                  if(ar.succeeded()){
                     System.out.println("sent to "+url);
                  }else{
                     System.out.println("failed to send to "+url);
                     //TODO disable the callback?
                  }
               });

         }catch(Exception e){
            e.printStackTrace();
            //TODO disable the hook?
         }

      }
   }

   @Transactional //Transactional is a workaround for #6059
   @ConsumeEvent(value="new/test",blocking = true)
   public void newTest(Test test){
      tellHooks("new/test",-1,test);
   }

   @Transactional //Transactional is a workaround for #6059
   @ConsumeEvent(value="new/run",blocking = true)
   public void newRun(Run run){
      Integer testId = run.testId;
      //tellHooks("new/run",testId,run);
   }


   @POST
   @Transactional
   public Response add(Hook hook){
      System.out.println("HOOKSERVER.add "+hook);
      if(hook == null){
         return Response.serverError().entity("hook is null").build();
      }
      try {
         if (hook.id != null) {
            em.merge(hook);
         } else {
            em.persist(hook);
         }
         em.flush();
         System.out.println("HOOKSERVER.add merge|persist "+hook.id+" "+hook.isPersistent());
         return Response.ok(hook).build();
      }catch(Exception e){

         Throwable root = e;
//         Our favorite Object is not assignable to java/lang/Throwable
//         HashSet<Throwable> seen = new HashSet<>();
//         seen.add(root);
//         while(root.getCause()!=null && !seen.contains(root.getCause())){
//            root = root.getCause();
//         }
         Json errorJson = new Json();
         errorJson.set("message",root.getMessage());
         System.out.println("HOOKSERVICE errorJson "+errorJson.toString(0));

         return Response.serverError().entity(errorJson.toString(0)).build();
      }
   }
   @GET
   @Path("{id}")
   public Hook get(@PathParam("id")Integer id){
      return Hook.find("id",id).firstResult();
   }

   @DELETE
   @Path("{id}")
   @Transactional
   public void delete(@PathParam("id")Integer id){
      Hook.find("id",id).firstResult().delete();
   }

   public List<Hook> getEventHooks(String type,Integer target){
      System.out.println("getEventHooks("+type+" , "+target+")");
      try {
         List<Hook> rtrn = new ArrayList<>();
         if(target == null || target == -1){
            rtrn = Hook.find("type = ?1 and active = true",type).list();
         }else {
            rtrn = Hook.find("type = ?1 and ( target = ?2 or target = -1) and active = true", type, target).list();
         }
         return rtrn;
      }catch(Exception e){
         e.printStackTrace();
      }
      return new ArrayList<>();
   }

   @GET
   @Path("list")
   public List<Hook> list(@QueryParam("limit") Integer limit, @QueryParam("page") Integer page, @QueryParam("sort") String sort, @QueryParam("direction") String direction){
      if(sort == null || sort.isEmpty()){
         sort = "url";
      }
      if(direction == null || direction.isEmpty()){
         direction = "Ascending";
      }
      if(limit != null && page != null){
         return Hook.findAll(Sort.by(sort).direction(Sort.Direction.valueOf(direction))).page(Page.of(page,limit)).list();
      }else{
         return Hook.listAll(Sort.by(sort).direction(Sort.Direction.valueOf(direction)));
      }
   }

}
