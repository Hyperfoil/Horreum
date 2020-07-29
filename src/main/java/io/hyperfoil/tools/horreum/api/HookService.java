package io.hyperfoil.tools.horreum.api;

import io.hyperfoil.tools.horreum.JsonAdapter;
import io.hyperfoil.tools.horreum.entity.json.Hook;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;

import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Path("/api/hook")
@Consumes({MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class HookService {
   private static final Logger log = Logger.getLogger(HookService.class);
   private static final Pattern FIND_EXPRESSIONS = Pattern.compile("\\$\\{([^}]*)\\}");

   @Inject
   SqlService sqlService;

   @Inject
   SecurityIdentity identity;

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
      int maxConnections = getIntFromEnv("MAX_CONNECTIONS", 20);
      WebClientOptions options = new WebClientOptions()
         .setFollowRedirects(true)
         .setMaxPoolSize(maxConnections)
         .setConnectTimeout(2_000) // only wait 2s
         .setKeepAlive(false);
      http1xClient = WebClient.create(reactiveVertx, new WebClientOptions(options).setProtocolVersion(HttpVersion.HTTP_1_1));
   }

   private void tellHooks(String type, int testId, Object value){
      List<Hook> hooks;
      try (CloseMe h = sqlService.withRoles(em, Arrays.asList("admin"))) {
         hooks = getEventHooks(type, testId);
      }
      JsonbConfig jsonbConfig = new JsonbConfig();
      jsonbConfig.withAdapters(new JsonAdapter());
      final Jsonb jsonb = JsonbBuilder.create(jsonbConfig);
      String json = jsonb.toJson(value);
      Json yetAnotherJson = Json.fromString(json);
      for (Hook hook : hooks) {
         try {
            String input = hook.url.startsWith("http") ? hook.url : "http://" + hook.url;
            Matcher matcher = FIND_EXPRESSIONS.matcher(input);
            StringBuilder replacedUrl = new StringBuilder();
            int lastMatch = 0;
            while (matcher.find()) {
               replacedUrl.append(input, lastMatch, matcher.start());
               String path = matcher.group(1).trim();
               replacedUrl.append(Json.find(yetAnotherJson, path));
               lastMatch = matcher.end();
            }
            replacedUrl.append(input.substring(lastMatch));
            URL url = new URL(replacedUrl.toString());
            int port = url.getPort() >= 0 ? url.getPort() : url.getDefaultPort();
            http1xClient.post(port, url.getHost(), url.getFile())
                  .putHeader("Content-Type", "application/json")
                  .sendBuffer(Buffer.buffer(json), ar -> {
                     if (ar.succeeded()) {
                        HttpResponse<Buffer> response = ar.result();
                        if (response.statusCode() < 400) {
                           log.debugf("Successfully(%d) notified hook: %s", response.statusCode(), url);
                        } else {
                           log.errorf("Failed to notify hook %s, response %d: ", url, response.statusCode(), response.bodyAsString());
                        }
                     } else {
                        log.errorf(ar.cause(), "Failed to notify hook %s", url);
                     }
                  });
         } catch (Exception e) {
            log.errorf(e, "Failed to invoke hook to %s", hook.url);
         }
      }
   }

   @Transactional //Transactional is a workaround for #6059
   @ConsumeEvent(value = Test.EVENT_NEW, blocking = true)
   public void newTest(Test test) {
      tellHooks(Test.EVENT_NEW, -1, test);
   }

   @Transactional
   @ConsumeEvent(value = Run.EVENT_NEW, blocking = true)
   public void newRun(Run run) {
      Integer testId = run.testid;
      tellHooks(Run.EVENT_NEW, testId, run);
   }

   @RolesAllowed(Roles.ADMIN)
   @POST
   @Transactional
   public Response add(Hook hook){
      System.out.println("HOOKSERVER.add "+hook);
      if(hook == null){
         return Response.serverError().entity("hook is null").build();
      }
      try (CloseMe h = sqlService.withRoles(em, identity)) {
         if (hook.id != null) {
            em.merge(hook);
         } else {
            em.persist(hook);
         }
         em.flush();
         System.out.println("HOOKSERVER.add merge|persist "+hook.id+" "+hook.isPersistent());
         return Response.ok(hook).build();
      } catch (Exception e) {

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

   @RolesAllowed(Roles.ADMIN)
   @GET
   @Path("{id}")
   public Hook get(@PathParam("id")Integer id){
      try (CloseMe h = sqlService.withRoles(em, identity)) {
         return Hook.find("id", id).firstResult();
      }
   }

   @RolesAllowed(Roles.ADMIN)
   @DELETE
   @Path("{id}")
   @Transactional
   public void delete(@PathParam("id")Integer id){
      try (CloseMe h = sqlService.withRoles(em, identity)) {
         Hook.find("id", id).firstResult().delete();
      }
   }

   public List<Hook> getEventHooks(String type, int testId) {
      try {
         List<Hook> rtrn;
         if (testId == -1) {
            rtrn = Hook.find("type = ?1 and active = true", type).list();
         } else {
            rtrn = Hook.find("type = ?1 and ( target = ?2 or target = -1) and active = true", type, testId).list();
         }
         return rtrn;
      } catch (Exception e) {
         log.error("Failed to get event hooks.", e);
      }
      return Collections.emptyList();
   }

   @RolesAllowed(Roles.ADMIN)
   @GET
   @Path("list")
   public List<Hook> list(@QueryParam("limit") Integer limit,
                          @QueryParam("page") Integer page,
                          @QueryParam("sort") @DefaultValue("url") String sort,
                          @QueryParam("direction") @DefaultValue("Ascending") Sort.Direction direction){
      try (CloseMe h = sqlService.withRoles(em, identity)) {
         if (limit != null && page != null) {
            return Hook.findAll(Sort.by(sort).direction(direction)).page(Page.of(page, limit)).list();
         } else {
            return Hook.listAll(Sort.by(sort).direction(direction));
         }
      }
   }

}
