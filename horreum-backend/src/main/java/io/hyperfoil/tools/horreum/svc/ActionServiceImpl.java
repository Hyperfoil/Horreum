package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.api.ExperimentService;
import io.hyperfoil.tools.horreum.api.ActionService;
import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.entity.ActionLog;
import io.hyperfoil.tools.horreum.entity.PersistentLog;
import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.json.AllowedSite;
import io.hyperfoil.tools.horreum.entity.json.Action;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.action.ActionPlugin;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;

import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ApplicationScoped
public class ActionServiceImpl implements ActionService {
   private static final Logger log = Logger.getLogger(ActionServiceImpl.class);

   @Inject
   Instance<ActionPlugin> actionPlugins;
   Map<String, ActionPlugin> plugins;

   @Inject
   EntityManager em;

   @Inject
   Vertx vertx;

   @PostConstruct()
   public void postConstruct(){
      plugins = actionPlugins.stream().collect(Collectors.toMap(ActionPlugin::type, Function.identity()));
   }

   private void executeActions(String event, int testId, Object payload){
      List<Action> actions = getActions(event, testId);
      if (actions.isEmpty()) {
         new ActionLog(PersistentLog.INFO, testId, event, null, "No actions found.").persist();
         return;
      }
      for (Action action : actions) {
         try {
            ActionPlugin plugin = plugins.get(action.type);
            if (plugin == null) {
               log.errorf("No plugin for action type %s", action.type);
               new ActionLog(PersistentLog.ERROR, testId, event, action.type, "No plugin for action type " + action.type).persist();
               continue;
            }
            plugin.execute(action.config, action.secrets, payload).subscribe()
                  .with(item -> {}, throwable -> logActionError(testId, event, action.type, throwable));
         } catch (Exception e) {
            log.errorf(e, "Failed to invoke action %d", action.id);
            new ActionLog(PersistentLog.ERROR, testId, event, action.type, "Failed to invoke: " + e.getMessage()).persist();
            new ActionLog(PersistentLog.DEBUG, testId, event, action.type,
                  "Configuration: <pre>\n<code>" + action.config.toPrettyString() +
                  "\n<code></pre>Payload: <pre>\n<code>" + Util.OBJECT_MAPPER.valueToTree(payload).toPrettyString() +
                  "</code>\n</pre>").persist();
         }
      }
   }

   void logActionError(int testId, String event, String type, Throwable throwable) {
      vertx.executeBlocking(Uni.createFrom().item(() -> {
         doLogActionError(testId, event, type, throwable);
         return null;
      })).subscribe().with(item -> {}, t -> {
         log.error("Cannot log error in action!", t);
         log.error("Logged error: ", throwable);
      });
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional(Transactional.TxType.REQUIRES_NEW)
   void doLogActionError(int testId, String event, String type, Throwable throwable) {
      new ActionLog(PersistentLog.ERROR, testId, event, type, throwable.getMessage()).persist();
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional //Transactional is a workaround for #6059
   @ConsumeEvent(value = Test.EVENT_NEW, blocking = true)
   public void onNewTest(Test test) {
      executeActions(Test.EVENT_NEW, -1, test);
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   @ConsumeEvent(value = Run.EVENT_NEW, blocking = true)
   public void onNewRun(Run run) {
      Integer testId = run.testid;
      executeActions(Run.EVENT_NEW, testId, run);
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   @ConsumeEvent(value = Change.EVENT_NEW, blocking = true)
   public void onNewChange(Change.Event changeEvent) {
      int testId = em.createQuery("SELECT testid FROM run WHERE id = ?1", Integer.class)
            .setParameter(1, changeEvent.dataset.runId).getResultStream().findFirst().orElse(-1);
      executeActions(Change.EVENT_NEW, testId, changeEvent);
   }

   void validate(Action action) {
      ActionPlugin plugin = plugins.get(action.type);
      if (plugin == null) {
         throw ServiceException.badRequest("Unknown hook type " + action.type);
      }
      plugin.validate(action.config, action.secrets);
   }

   @RolesAllowed(Roles.ADMIN)
   @WithRoles
   @Transactional
   @Override
   public Action add(Action action){
      if(action == null){
         throw ServiceException.badRequest("Send action as request body.");
      }
      if (action.id != null && action.id <= 0) {
         action.id = null;
      }
      if (action.testId == null) {
         action.testId = -1;
      }
      validate(action);
      if (action.id == null) {
         action.persist();
      } else {
         merge(action);
      }
      em.flush();
      return action;
   }

   void merge(Action action) {
      if (action.secrets == null || !action.secrets.isObject()) {
         action.secrets = JsonNodeFactory.instance.objectNode();
      }
      if (!action.secrets.path("modified").asBoolean(false)) {
         Action old = Action.findById(action.id);
         action.secrets = old == null ? JsonNodeFactory.instance.objectNode() : old.secrets;
      } else {
         ((ObjectNode) action.secrets).remove("modified");
      }
      em.merge(action);
   }

   @RolesAllowed(Roles.ADMIN)
   @WithRoles
   @Override
   public Action get(int id){
      return Action.find("id", id).firstResult();
   }


   @WithRoles
   @RolesAllowed(Roles.ADMIN)
   @Transactional
   @Override
   public void delete(int id){
      Action.delete("id", id);
   }

   public List<Action> getActions(String event, int testId) {
      if (testId < 0) {
         return Action.find("event = ?1", event).list();
      } else {
         return Action.find("event = ?1 and (test_id = ?2 or test_id < 0)", event, testId).list();
      }
   }

   @RolesAllowed(Roles.ADMIN)
   @WithRoles
   @Override
   public List<Action> list(Integer limit, Integer page, String sort, SortDirection direction){
      Sort.Direction sortDirection = direction == null ? null : Sort.Direction.valueOf(direction.name());
      PanacheQuery<Action> query = Action.find("test_id < 0", Sort.by(sort).direction(sortDirection));
      if (limit != null && page != null) {
         query = query.page(Page.of(page, limit));
      }
      return query.list();
   }


   @RolesAllowed({ Roles.ADMIN, Roles.TESTER})
   @WithRoles
   @Override
   public List<Action> getTestActions(int testId) {
      return Action.list("testId", testId);
   }

   @PermitAll
   @Override
   public List<AllowedSite> allowedSites() {
      return AllowedSite.listAll();
   }

   @RolesAllowed(Roles.ADMIN)
   @WithRoles
   @Transactional
   @Override
   public AllowedSite addSite(String prefix) {
      AllowedSite p = new AllowedSite();
      // FIXME: fetchival stringifies the body into JSON string :-/
      p.prefix = Util.destringify(prefix);
      em.persist(p);
      return p;
   }

   @RolesAllowed(Roles.ADMIN)
   @WithRoles
   @Transactional
   @Override
   public void deleteSite(long id) {
      AllowedSite.delete("id", id);
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   @ConsumeEvent(value = ExperimentService.ExperimentResult.NEW_RESULT, blocking = true)
   public void onNewExperimentResult(ExperimentService.ExperimentResult result) {
      executeActions(ExperimentService.ExperimentResult.NEW_RESULT, result.profile.test.id, result);
   }
}
