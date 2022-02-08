package io.hyperfoil.tools.horreum.svc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.api.NotificationService;
import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.NotificationSettings;
import io.hyperfoil.tools.horreum.entity.alerting.Variable;
import io.hyperfoil.tools.horreum.entity.alerting.Watch;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.notification.Notification;
import io.hyperfoil.tools.horreum.notification.NotificationPlugin;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.vertx.ConsumeEvent;

@ApplicationScoped
public class NotificationServiceImpl implements NotificationService {
   private static final Logger log = Logger.getLogger(NotificationServiceImpl.class);
   //@formatter:off
   private static final String GET_NOTIFICATIONS =
         "WITH ens AS (" +
            "SELECT ns.*, watch_id FROM notificationsettings ns " +
            "JOIN watch_users wu ON NOT ns.isteam AND ns.name = wu.users " +
            "UNION " +
            "SELECT ns.*, watch_id FROM notificationsettings ns " +
            "JOIN watch_teams wt ON ns.isteam AND ns.name = wt.teams " +
            "UNION " +
            "SELECT ns.*, watch_id FROM notificationsettings ns " +
            "JOIN userinfo_teams ut ON NOT ns.isteam AND ns.name = ut.username " +
            "JOIN watch_teams wt ON wt.teams = ut.team " +
         ") SELECT method, data, name FROM ens JOIN watch ON ens.watch_id = watch.id WHERE testid = ?" +
         " AND name NOT IN (SELECT optout FROM watch_optout WHERE ens.watch_id  = watch_optout.watch_id)";
   //@formatter:on
   public final Map<String, NotificationPlugin> plugins = new HashMap<>();

   @Inject
   EntityManager em;

   @Inject
   Instance<NotificationPlugin> notificationPlugins;

   @Inject
   TransactionManager tm;

   @PostConstruct
   public void init() {
      notificationPlugins.forEach(plugin -> plugins.put(plugin.method(), plugin));
   }

   @WithRoles(extras = Roles.HORREUM_ALERTING)
   @Transactional
   @ConsumeEvent(value = Change.EVENT_NEW, blocking = true)
   public void onMissingRunValues(Change.Event event) {
      if (!event.notify) {
         log.debug("Notification skipped");
         return;
      }
      Variable variable = event.change.variable;
      // TODO: breaks storage/alerting separation!
      Test test = Test.findById(variable.testId);
      // Test might be null when it's private
      String testName = test == null ? "unknown" : test.name;
      log.infof("Received new change in test %d (%s), run %d, variable %d (%s)", variable.testId, testName, event.change.run.id, variable.id, variable.name);

      String tags = getTags(event.change.run.id);

      notifyAll(variable.testId, n -> n.notifyChange(testName, tags, event.change));
   }

   private String getTags(int runId) {
      @SuppressWarnings("rawtypes")
      List tagsList = em.createNativeQuery("SELECT tags::::text FROM run_tags WHERE runid = ?")
              .setParameter(1, runId)
              .getResultList();
      String tags;
      if (tagsList.size() > 0) {
         Object tagsResult = tagsList.stream().findFirst().get();
         tags = tagsToString(Util.toJsonNode(String.valueOf(tagsResult)));
      } else {
         tags = "";
      }
      return tags;
   }

   @WithRoles(extras = Roles.HORREUM_ALERTING)
   @Transactional
   @ConsumeEvent(value = Run.EVENT_MISSING_VALUES, blocking = true)
   public void onMissingRunValues(MissingRunValuesEvent event) {
      if (!event.notify) {
         log.debugf("Skipping notification for missing run values on test %d, run %d", event.testId, event.runId);
         return;
      }
      // TODO: breaks storage/alerting separation!
      Test test = Test.findById(event.testId);
      String testName = test == null ? "unknown" : test.name;
      log.infof("Received missing values event in test %d (%s), run %d, variables %s", event.testId, testName, event.runId, event.variables);

      String tags = getTags(event.runId);
      notifyAll(event.testId, n -> n.notifyMissingRunValues(testName, tags, event));
   }

   private void notifyAll(int testId, Consumer<Notification> consumer) {
      @SuppressWarnings("unchecked")
      List<Object[]> results = em.createNativeQuery(GET_NOTIFICATIONS)
            .setParameter(1, testId).getResultList();
      if (results.isEmpty()) {
         log.warnf("There are no subscribers for notification on test %d!", testId);
      }
      for (Object[] pair : results) {
         if (pair.length != 3) {
            log.errorf("Unexpected result %s", Arrays.toString(pair));
         }
         String method = String.valueOf(pair[0]);
         String data = String.valueOf(pair[1]);
         String userName = String.valueOf(pair[2]);
         NotificationPlugin plugin = plugins.get(method);
         if (plugin == null) {
            log.errorf("Cannot notify %s; no plugin for method %s with data %s", userName, method, data);
         } else {
            consumer.accept(plugin.create(userName, data));
         }
      }
   }

   private static String tagsToString(JsonNode tagsObject) {
      if (tagsObject == null) {
         return null;
      }
      StringBuilder sb = new StringBuilder();
      Util.toMap(tagsObject).forEach((key, value) -> {
         if (sb.length() != 0) {
            sb.append(';');
         }
         sb.append(key).append(':').append(value);
      });
      return sb.toString();
   }

   @PermitAll
   @Override
   public Set<String> methods() {
      return plugins.keySet();
   }

   @WithRoles(addUsername = true)
   @RolesAllowed({ Roles.VIEWER, Roles.TESTER, Roles.ADMIN})
   @Override
   public List<NotificationSettings> settings(String name, boolean team) {
      return NotificationSettings.list("name = ?1 AND isTeam = ?2", name, team);
   }

   @WithRoles(addUsername = true)
   @RolesAllowed({ Roles.VIEWER, Roles.TESTER, Roles.ADMIN})
   @Transactional
   @Override
   public void updateSettings(String name, boolean team, NotificationSettings[] settings) {
      NotificationSettings.delete("name = ?1 AND isTeam = ?2", name, team);
      for (NotificationSettings s : settings) {
         if (!plugins.containsKey(s.method)) {
            try {
               tm.setRollbackOnly();
            } catch (SystemException e) {
               log.error("Cannot rollback", e);
            }
            throw ServiceException.badRequest("Invalid method " + s.method);
         }
         s.name = name;
         s.isTeam = team;
         em.merge(s);
      }
   }

   // must be called with sqlService.withRole
   void notifyMissingRun(int testId, JsonNode tagsJson, long maxStaleness, int runId, long runTimestamp) {
      String tags = tagsToString(tagsJson);
      Test test = Test.findById(testId);
      Watch watch = Watch.find("testId = ?1", testId).firstResult();
      if (!watch.mutemissingruns) {
         String name = test != null ? test.name : "<unknown test>";
         notifyAll(testId, n -> n.notifyMissingRun(name, testId, tags, maxStaleness, runId, runTimestamp));
      }
   }

   public void notifyExpectedRun(int testId, JsonNode tagsJson, long expectedBefore, String expectedBy, String backlink) {
      String tags = tagsToString(tagsJson);
      Test test = Test.findById(testId);
      String name = test != null ? test.name : "<unknown test>";
      notifyAll(testId, n -> n.notifyExpectedRun(name, testId, tags, expectedBefore, expectedBy, backlink));
   }
}
