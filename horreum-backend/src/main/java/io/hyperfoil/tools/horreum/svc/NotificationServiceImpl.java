package io.hyperfoil.tools.horreum.svc;

import java.util.Arrays;
import java.util.Collections;
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
import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;

import org.hibernate.exception.ConstraintViolationException;
import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.api.NotificationService;
import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.NotificationSettings;
import io.hyperfoil.tools.horreum.entity.alerting.UserInfo;
import io.hyperfoil.tools.horreum.entity.alerting.Variable;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.notification.Notification;
import io.hyperfoil.tools.horreum.notification.NotificationPlugin;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.security.identity.SecurityIdentity;
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
   SqlServiceImpl sqlService;

   @Inject
   EntityManager em;

   @Inject
   SecurityIdentity identity;

   @Inject
   Instance<NotificationPlugin> notificationPlugins;

   @Inject
   TransactionManager tm;

   @PostConstruct
   public void init() {
      notificationPlugins.forEach(plugin -> plugins.put(plugin.method(), plugin));
   }

   @Transactional
   @ConsumeEvent(value = Change.EVENT_NEW, blocking = true)
   public void onNewChange(Change.Event event) {
      if (!event.notify) {
         log.debug("Notification skipped");
         return;
      }
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, Collections.singletonList(AlertingServiceImpl.HORREUM_ALERTING))) {
         Variable variable = event.change.variable;
         // TODO: breaks storage/alerting separation!
         Test test = Test.findById(variable.testId);
         // Test might be null when it's private
         String testName = test == null ? "unknown" : test.name;
         log.infof("Received new change in test %d (%s), run %d, variable %d (%s)", variable.testId, testName, event.change.runId, variable.id, variable.name);

         @SuppressWarnings("rawtypes")
         List tagsList = em.createNativeQuery("SELECT tags::::text FROM run_tags WHERE runid = ?")
                 .setParameter(1, event.change.runId)
                 .getResultList();
         String tags;
         if (tagsList.size() > 0) {
            Object tagsResult = tagsList.stream().findFirst().get();
            tags = tagsToString(Json.fromString(String.valueOf(tagsResult)));
         } else {
            tags = "";
         }

         notifyAll(variable.testId, n -> n.notifyChange(testName, tags, event.change));
      }
   }

   private void notifyAll(int testId, Consumer<Notification> consumer) {
      @SuppressWarnings("unchecked")
      List<Object[]> results = em.createNativeQuery(GET_NOTIFICATIONS)
            .setParameter(1, testId).getResultList();
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

   private static String tagsToString(Json tagsObject) {
      StringBuilder sb = new StringBuilder();
      tagsObject.forEach((key, value) -> {
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

   @RolesAllowed({ Roles.TESTER, Roles.ADMIN})
   @Override
   public List<NotificationSettings> settings(String name, boolean team) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         return NotificationSettings.list("name = ?1 AND isTeam = ?2", name, team);
      }
   }

   @RolesAllowed({ Roles.TESTER, Roles.ADMIN})
   @Transactional
   @Override
   public void updateSettings(String name, boolean team, NotificationSettings[] settings) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
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
   }

   public void cacheUserTeams(String username, Set<String> teams) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, Collections.singletonList(AlertingServiceImpl.HORREUM_ALERTING))) {
         // Running this without pessimistic lock leads to duplicate inserts at the same time
         UserInfo userInfo = UserInfo.findById(username, LockModeType.PESSIMISTIC_WRITE);
         if (userInfo == null) {
            userInfo = new UserInfo();
            userInfo.username = username;
         } else if (!teams.equals(userInfo.teams)) {
            userInfo.teams = teams;
         }
         userInfo.persistAndFlush();
      } catch (PersistenceException e) {
         if (e.getCause() instanceof ConstraintViolationException) {
            // silently ignore
            // note: alternative would be to define @SQLInsert with INSERT ... ON CONFLICT DO NOTHING
            log.tracef(e, "Concurrent insertion of %s", username);
         } else {
            throw e;
         }
      }
   }

   // must be called with sqlService.withRole
   void notifyMissingRun(int testId, Json tagsJson, long maxStaleness, int runId, long runTimestamp) {
      String tags = tagsToString(tagsJson);
      Test test = Test.findById(testId);
      notifyAll(testId, n -> n.notifyMissingRun(test.name, testId, tags, maxStaleness, runId, runTimestamp));
   }
}
