package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.JsonAdapter;
import io.hyperfoil.tools.horreum.api.HookService;
import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.json.AllowedHookPrefix;
import io.hyperfoil.tools.horreum.entity.json.Hook;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.WebClient;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.transaction.Transactional;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class HookServiceImpl implements HookService {
   private static final Logger log = Logger.getLogger(HookServiceImpl.class);
   private static final Pattern FIND_EXPRESSIONS = Pattern.compile("\\$\\{([^}]*)\\}");

   @Inject
   SqlServiceImpl sqlService;

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
   Vertx reactiveVertx;

   @ConfigProperty(name = "horreum.hook.tls.insecure", defaultValue = "false")
   boolean insecureTls;

   WebClient http1xClient;

   @PostConstruct()
   public void postConstruct(){
      int maxConnections = getIntFromEnv("MAX_CONNECTIONS", 20);
      WebClientOptions options = new WebClientOptions()
         .setFollowRedirects(false)
         .setMaxPoolSize(maxConnections)
         .setConnectTimeout(2_000) // only wait 2s
         .setKeepAlive(false);
      if (insecureTls) {
         options.setVerifyHost(false);
         options.setTrustAll(true);
      }
      http1xClient = WebClient.create(reactiveVertx, new WebClientOptions(options).setProtocolVersion(HttpVersion.HTTP_1_1));
   }

   private void tellHooks(String type, int testId, Object value){
      List<Hook> hooks;
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, Collections.singletonList("admin"))) {
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
            RequestOptions options = new RequestOptions()
                  .setHost(url.getHost())
                  .setPort(url.getPort() >= 0 ? url.getPort() : url.getDefaultPort())
                  .setURI(url.getFile())
                  .setSsl("https".equals(url.getProtocol().toLowerCase()));
            http1xClient.request(HttpMethod.POST, options)
                  .putHeader("Content-Type", "application/json")
                  .sendBuffer(Buffer.buffer(json))
                  .subscribe().with(response -> {
                        if (response.statusCode() < 400) {
                           log.debugf("Successfully(%d) notified hook: %s", response.statusCode(), url);
                        } else {
                           log.errorf("Failed to notify hook %s, response %d: %s", url, response.statusCode(), response.bodyAsString());
                        }
                     },
                     cause -> log.errorf(cause, "Failed to notify hook %s", url));
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

   @Transactional
   @ConsumeEvent(value = Change.EVENT_NEW, blocking = true)
   public void newChange(Change.Event changeEvent) {
      Integer runId = changeEvent.change.runId;
      Run run =  Run.find("id", runId).firstResult();
      tellHooks(Change.EVENT_NEW, run.testid, changeEvent.change);
   }

   @RolesAllowed({ Roles.ADMIN, Roles.TESTER})
   @Transactional
   @Override
   public Hook add(Hook hook){
      if(hook == null){
         throw ServiceException.badRequest("Send hook as request body.");
      }
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         if (hook.id != null && hook.id <= 0) {
            hook.id = null;
         }
         if (hook.id == null) {
            em.persist(hook);
         } else {
            em.merge(hook);
         }
         em.flush();
         return hook;
      } catch (PersistenceException e) {
         log.error("Failed to persist hook", e);
         throw ServiceException.serverError("Failed to persist hook");
      }
   }

   @RolesAllowed({ Roles.ADMIN, Roles.TESTER})
   @Override
   public Hook get(Integer id){
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         return Hook.find("id", id).firstResult();
      }
   }


   @RolesAllowed({ Roles.ADMIN, Roles.TESTER})
   @Transactional
   @Override
   public void delete(Integer id){
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
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
   @Override
   public List<Hook> list(Integer limit, Integer page, String sort, Sort.Direction direction){
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         if (limit != null && page != null) {
            return Hook.findAll(Sort.by(sort).direction(direction)).page(Page.of(page, limit)).list();
         } else {
            return Hook.listAll(Sort.by(sort).direction(direction));
         }
      }
   }


   @RolesAllowed({ Roles.ADMIN, Roles.TESTER})
   @Override
   public List<Hook> hooks(Integer testId) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         if (testId != null) {
            return Hook.list("target", testId);
         } else {
            throw ServiceException.badRequest("No test ID set.");
         }
      }
   }

   @PermitAll
   @Override
   public List<AllowedHookPrefix> allowedPrefixes() {
      return AllowedHookPrefix.listAll();
   }

   @RolesAllowed(Roles.ADMIN)
   @Transactional
   @Override
   public AllowedHookPrefix addPrefix(String prefix) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         AllowedHookPrefix p = new AllowedHookPrefix();
         // FIXME: fetchival stringifies the body into JSON string :-/
         p.prefix = Util.destringify(prefix);
         em.persist(p);
         return p;
      }
   }

   @RolesAllowed(Roles.ADMIN)
   @Transactional
   @Override
   public void deletePrefix(Long id) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         AllowedHookPrefix.delete("id", id);
      }
   }
}
