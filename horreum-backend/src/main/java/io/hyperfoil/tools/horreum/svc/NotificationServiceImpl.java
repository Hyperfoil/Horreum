package io.hyperfoil.tools.horreum.svc;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;

import org.hibernate.Session;

import io.hyperfoil.tools.horreum.api.alerting.NotificationSettings;
import io.hyperfoil.tools.horreum.api.internal.services.NotificationService;
import io.hyperfoil.tools.horreum.entity.alerting.NotificationSettingsDAO;
import io.hyperfoil.tools.horreum.entity.data.DatasetDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.entity.user.UserApiKey;
import io.hyperfoil.tools.horreum.events.DatasetChanges;
import io.hyperfoil.tools.horreum.mapper.NotificationSettingsMapper;
import io.hyperfoil.tools.horreum.notification.Notification;
import io.hyperfoil.tools.horreum.notification.NotificationPlugin;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;

@ApplicationScoped
@Startup
public class NotificationServiceImpl implements NotificationService {

    //@formatter:off
    private static final String GET_NOTIFICATIONS = """
         WITH ens AS (
            SELECT ns.*, watch_id FROM notificationsettings ns
               JOIN watch_users wu ON NOT ns.isteam AND ns.name = wu.users
            UNION
            SELECT ns.*, watch_id FROM notificationsettings ns
               JOIN watch_teams wt ON ns.isteam AND ns.name = wt.teams
            UNION
            SELECT ns.*, watch_id FROM notificationsettings ns
               JOIN team t ON NOT ns.isteam
                  AND ns.name = t.team_name
               JOIN watch_teams wt ON wt.teams = t.team_name
         )
         SELECT method, data, name
         FROM ens
         JOIN watch ON ens.watch_id = watch.id
         WHERE testid = ?
          AND name NOT IN (SELECT optout FROM watch_optout WHERE ens.watch_id  = watch_optout.watch_id)
         """;
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

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    public void onNewChanges(DatasetChanges event) {
        if (!event.isNotify()) {
            Log.debug("Notification skipped");
            return;
        }
        Log.debugf("Received new changes in test %d (%s), dataset %d/%d (fingerprint: %s)",
                event.dataset.testId, event.testName, event.dataset.runId, event.dataset.ordinal, event.fingerprint);
        notifyAll(event.dataset.testId, n -> n.notifyChanges(event));
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    public void onMissingValues(MissingValuesEvent event) {
        if (!event.notify) {
            Log.debugf("Skipping notification for missing run values on test %d, run %d", event.dataset.testId,
                    event.dataset.id);
            return;
        }
        // TODO: breaks storage/alerting separation!
        TestDAO test = TestDAO.findById(event.dataset.testId);
        String testName = test == null ? "unknown" : test.name;
        Log.debugf("Received missing values event in test %d (%s), run %d, variables %s", event.dataset.testId, testName,
                event.dataset.id, event.variables);

        String fingerprint = em.getReference(DatasetDAO.class, event.dataset.id).getFingerprint();
        notifyAll(event.dataset.testId, n -> n.notifyMissingValues(testName, fingerprint, event));
    }

    private void notifyAll(int testId, Consumer<Notification> consumer) {
        List<Object[]> results = em.unwrap(Session.class).createNativeQuery(GET_NOTIFICATIONS, Object[].class)
                .setParameter(1, testId).getResultList();
        if (results.isEmpty()) {
            Log.infof("There are no subscribers for notification on test %d!", testId);
        }
        for (Object[] pair : results) {
            if (pair.length != 3) {
                Log.errorf("Unexpected result %s", Arrays.toString(pair));
            }
            String method = String.valueOf(pair[0]);
            String data = String.valueOf(pair[1]);
            String userName = String.valueOf(pair[2]);
            NotificationPlugin plugin = plugins.get(method);
            if (plugin == null) {
                Log.errorf("Cannot notify %s; no plugin for method %s with data %s", userName, method, data);
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
    @RolesAllowed({ Roles.VIEWER, Roles.TESTER, Roles.ADMIN })
    @Override
    public List<NotificationSettings> settings(String name, boolean team) {
        List<NotificationSettingsDAO> notifications = NotificationSettingsDAO.list("name = ?1 AND isTeam = ?2", name, team);
        return notifications.stream().map(NotificationSettingsMapper::from).collect(Collectors.toList());
    }

    @WithRoles(addUsername = true)
    @RolesAllowed({ Roles.VIEWER, Roles.TESTER, Roles.ADMIN })
    @Transactional
    @Override
    // remove and re-create the settings for the user
    public void updateSettings(String name, boolean team, NotificationSettings[] settings) {
        NotificationSettingsDAO.delete("name = ?1 AND isTeam = ?2", name, team);
        Arrays.stream(settings).map(NotificationSettingsMapper::to).forEach(ns -> {
            if (!plugins.containsKey(ns.method)) {
                try {
                    tm.setRollbackOnly();
                } catch (SystemException e) {
                    Log.error("Cannot rollback", e);
                }
                throw ServiceException.badRequest("Invalid method " + ns.method);
            }
            // reset the id
            ns.id = null;
            ns.name = name;
            ns.isTeam = team;
            ns.persist();
        });
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

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    public void notifyApiKeyExpiration(UserApiKey key, long toExpiration) {
        NotificationSettingsDAO.<NotificationSettingsDAO> stream("name", key.user.username).forEach(notification -> {
            NotificationPlugin plugin = plugins.get(notification.method);
            if (plugin == null) {
                Log.errorf("Cannot notify %s of API key '%s' expiration: no plugin for method %s",
                        notification.name, key.name, notification.method);
            } else {
                plugin.create(notification.name, notification.data)
                        .notifyApiKeyExpiration(key.name, key.creation, key.access, toExpiration, key.active);
            }
        });
    }
}
