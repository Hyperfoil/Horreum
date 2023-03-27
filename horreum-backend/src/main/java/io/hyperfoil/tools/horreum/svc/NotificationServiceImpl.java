package io.hyperfoil.tools.horreum.svc;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

import io.hyperfoil.tools.horreum.api.alerting.NotificationSettings;
import io.hyperfoil.tools.horreum.mapper.NotificationSettingsMapper;
import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.api.services.NotificationService;
import io.hyperfoil.tools.horreum.bus.MessageBus;
import io.hyperfoil.tools.horreum.entity.alerting.NotificationSettingsDAO;
import io.hyperfoil.tools.horreum.entity.data.DataSetDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.events.DatasetChanges;
import io.hyperfoil.tools.horreum.notification.Notification;
import io.hyperfoil.tools.horreum.notification.NotificationPlugin;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.runtime.Startup;

@ApplicationScoped
@Startup
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

   @Inject
   MessageBus messageBus;

   @PostConstruct
   public void init() {
      notificationPlugins.forEach(plugin -> plugins.put(plugin.method(), plugin));
      messageBus.subscribe(DatasetChanges.EVENT_NEW, "NotificationService", DatasetChanges.class, this::onNewChanges);
      messageBus.subscribe(DataSetDAO.EVENT_MISSING_VALUES, "NotificationService", MissingValuesEvent.class, this::onMissingValues);
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   public void onNewChanges(DatasetChanges event) {
      if (!event.isNotify()) {
         log.debug("Notification skipped");
         return;
      }
      log.debugf("Received new changes in test %d (%s), dataset %d/%d (fingerprint: %s)",
            event.dataset.testId, event.testName, event.dataset.runId, event.dataset.ordinal, event.fingerprint);
      notifyAll(event.dataset.testId, n -> n.notifyChanges(event));
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   public void onMissingValues(MissingValuesEvent event) {
      if (!event.notify) {
         log.debugf("Skipping notification for missing run values on test %d, run %d", event.dataset.testId, event.dataset.id);
         return;
      }
      // TODO: breaks storage/alerting separation!
      TestDAO test = TestDAO.findById(event.dataset.testId);
      String testName = test == null ? "unknown" : test.name;
      log.debugf("Received missing values event in test %d (%s), run %d, variables %s", event.dataset.testId, testName, event.dataset.id, event.variables);

      String fingerprint = em.getReference(DataSetDAO.class, event.dataset.id).getFingerprint();
      notifyAll(event.dataset.testId, n -> n.notifyMissingValues(testName, fingerprint, event));
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

   @PermitAll
   @Override
   public Collection<String> methods() {
      return plugins.keySet();
   }

   @WithRoles(addUsername = true)
   @RolesAllowed({ Roles.VIEWER, Roles.TESTER, Roles.ADMIN})
   @Override
   public List<NotificationSettings> settings(String name, boolean team) {
      List<NotificationSettingsDAO> notifications = NotificationSettingsDAO.list("name = ?1 AND isTeam = ?2", name, team);
      return notifications.stream().map(NotificationSettingsMapper::from).collect(Collectors.toList());
   }

   @WithRoles(addUsername = true)
   @RolesAllowed({ Roles.VIEWER, Roles.TESTER, Roles.ADMIN})
   @Transactional
   @Override
   public void updateSettings(String name, boolean team, NotificationSettings[] settings) {
      NotificationSettingsDAO.delete("name = ?1 AND isTeam = ?2", name, team);
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

   @RolesAllowed(Roles.ADMIN)
   @Override
   public void testNotifications(String method, String data) {
      if (method == null) {
         for (var plugin : plugins.values()) {
            plugin.test(data);
         }
      } else {
         var plugin = plugins.get(method);
         if (plugin == null) {
            throw ServiceException.badRequest("Method " + method + " is not available");
         }
         plugin.test(data);
      }
   }

   public void notifyMissingDataset(int testId, String ruleName, long maxStaleness, Instant lastTimestamp) {
      TestDAO test = TestDAO.findById(testId);
      String testName = test != null ? test.name : "<unknown test>";
      notifyAll(testId, n -> n.notifyMissingDataset(testName, testId, ruleName, maxStaleness, lastTimestamp));
   }

   public void notifyExpectedRun(int testId, long expectedBefore, String expectedBy, String backlink) {
      TestDAO test = TestDAO.findById(testId);
      String name = test != null ? test.name : "<unknown test>";
      notifyAll(testId, n -> n.notifyExpectedRun(name, testId, expectedBefore, expectedBy, backlink));
   }
}
