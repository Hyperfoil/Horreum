package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.api.HookService;
import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.json.AllowedHookPrefix;
import io.hyperfoil.tools.horreum.entity.json.Hook;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
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
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.transaction.Transactional;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

@ApplicationScoped
public class HookServiceImpl implements HookService {
   private static final Logger log = Logger.getLogger(HookServiceImpl.class);
   private static final Pattern FIND_EXPRESSIONS = Pattern.compile("\\$\\{([^}]*)\\}");

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
      List<Hook> hooks = getEventHooks(type, testId);
      JsonNode json = Util.OBJECT_MAPPER.valueToTree(value);
      for (Hook hook : hooks) {
         try {
            String input = hook.url.startsWith("http") ? hook.url : "http://" + hook.url;
            Matcher matcher = FIND_EXPRESSIONS.matcher(input);
            StringBuilder replacedUrl = new StringBuilder();
            int lastMatch = 0;
            while (matcher.find()) {
               replacedUrl.append(input, lastMatch, matcher.start());
               String path = matcher.group(1).trim();
               replacedUrl.append(Util.findJsonPath(json, path));
               lastMatch = matcher.end();
            }
            replacedUrl.append(input.substring(lastMatch));
            URL url = new URL(replacedUrl.toString());
            RequestOptions options = new RequestOptions()
                  .setHost(url.getHost())
                  .setPort(url.getPort() >= 0 ? url.getPort() : url.getDefaultPort())
                  .setURI(url.getFile())
                  .setSsl("https".equalsIgnoreCase(url.getProtocol()));
            http1xClient.request(HttpMethod.POST, options)
                  .putHeader("Content-Type", "application/json")
                  .sendBuffer(Buffer.buffer(json.toString()))
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

   @WithRoles(extras = Roles.ADMIN)
   @Transactional //Transactional is a workaround for #6059
   @ConsumeEvent(value = Test.EVENT_NEW, blocking = true)
   public void newTest(Test test) {
      tellHooks(Test.EVENT_NEW, -1, test);
   }

   @WithRoles(extras = Roles.ADMIN)
   @Transactional
   @ConsumeEvent(value = Run.EVENT_NEW, blocking = true)
   public void newRun(Run run) {
      Integer testId = run.testid;
      tellHooks(Run.EVENT_NEW, testId, run);
   }

   @WithRoles(extras = Roles.ADMIN)
   @Transactional
   @ConsumeEvent(value = Change.EVENT_NEW, blocking = true)
   public void newChange(Change.Event changeEvent) {
      int testId = em.createQuery("SELECT testid FROM run WHERE id = ?1", Integer.class)
            .setParameter(1, changeEvent.dataset.runId).getResultStream().findFirst().orElse(-1);
      tellHooks(Change.EVENT_NEW, testId, changeEvent.change);
   }

   void checkPrefix(Hook hook) {
      if (AllowedHookPrefix.find("?1 LIKE CONCAT(prefix, '%')", hook.url).count() == 0) {
         throw ServiceException.badRequest("The requested URL is not on the list of allowed URL prefixes; " +
               "visit /api/hook/prefixes to see this list. Only the administrator is allowed to add prefixes.");
      }
   }

   @RolesAllowed(Roles.ADMIN)
   @WithRoles
   @Transactional
   @Override
   public Hook add(Hook hook){
      if(hook == null){
         throw ServiceException.badRequest("Send hook as request body.");
      }
      if (hook.id != null && hook.id <= 0) {
         hook.id = null;
      }
      if (hook.target == null) {
         hook.target = -1;
      }
      checkPrefix(hook);
      if (hook.id == null) {
         hook.persist();
      } else {
         em.merge(hook);
      }
      em.flush();
      return hook;
   }

   @RolesAllowed(Roles.ADMIN)
   @WithRoles
   @Override
   public Hook get(Integer id){
      return Hook.find("id", id).firstResult();
   }


   @WithRoles
   @RolesAllowed(Roles.ADMIN)
   @Transactional
   @Override
   public void delete(Integer id){
      Hook.delete("id", id);
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
   @WithRoles
   @Override
   public List<Hook> list(Integer limit, Integer page, String sort, SortDirection direction){
      Sort.Direction sortDirection = direction == null ? null : Sort.Direction.valueOf(direction.name());
      if (limit != null && page != null) {
         return Hook.findAll(Sort.by(sort).direction(sortDirection)).page(Page.of(page, limit)).list();
      } else {
         return Hook.listAll(Sort.by(sort).direction(sortDirection));
      }
   }


   @RolesAllowed({ Roles.ADMIN, Roles.TESTER})
   @WithRoles
   @Override
   public List<Hook> hooks(Integer testId) {
      if (testId != null) {
         return Hook.list("target", testId);
      } else {
         throw ServiceException.badRequest("No test ID set.");
      }
   }

   @PermitAll
   @Override
   public List<AllowedHookPrefix> allowedPrefixes() {
      return AllowedHookPrefix.listAll();
   }

   @RolesAllowed(Roles.ADMIN)
   @WithRoles
   @Transactional
   @Override
   public AllowedHookPrefix addPrefix(String prefix) {
      AllowedHookPrefix p = new AllowedHookPrefix();
      // FIXME: fetchival stringifies the body into JSON string :-/
      p.prefix = Util.destringify(prefix);
      em.persist(p);
      return p;
   }

   @RolesAllowed(Roles.ADMIN)
   @WithRoles
   @Transactional
   @Override
   public void deletePrefix(Long id) {
      AllowedHookPrefix.delete("id", id);
   }
}
