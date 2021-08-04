package io.hyperfoil.tools.horreum.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

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
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.hibernate.exception.ConstraintViolationException;
import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.NotificationSettings;
import io.hyperfoil.tools.horreum.entity.alerting.UserInfo;
import io.hyperfoil.tools.horreum.entity.alerting.Variable;
import io.hyperfoil.tools.horreum.entity.alerting.Watch;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.notification.Notification;
import io.hyperfoil.tools.horreum.notification.NotificationPlugin;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.ConsumeEvent;

@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/api/notifications")
public class NotificationService {
   private static final Logger log = Logger.getLogger(NotificationService.class);
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
         ") SELECT method, data, name FROM ens JOIN watch ON ens.watch_id = watch.id WHERE testid = ?;";
   //@formatter:on
   public final Map<String, NotificationPlugin> plugins = new HashMap<>();

   @Inject
   SqlService sqlService;

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
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, Collections.singletonList(AlertingService.HORREUM_ALERTING))) {
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
   @GET
   @Path("/methods")
   public Set<String> methods() {
      return plugins.keySet();
   }

   @RolesAllowed({Roles.TESTER, Roles.ADMIN})
   @GET
   @Path("/settings")
   public List<NotificationSettings> settings(@QueryParam("name") String name, @QueryParam("team") boolean team) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         return NotificationSettings.list("name = ?1 AND isTeam = ?2", name, team);
      }
   }

   @RolesAllowed({Roles.TESTER, Roles.ADMIN})
   @POST
   @Path("/settings")
   @Transactional
   public Response updateSettings(@QueryParam("name") String name, @QueryParam("team") boolean team, NotificationSettings[] settings) throws SystemException {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         NotificationSettings.delete("name = ?1 AND isTeam = ?2", name, team);
         for (NotificationSettings s : settings) {
            if (!plugins.containsKey(s.method)) {
               tm.setRollbackOnly();
               return Response.status(Response.Status.BAD_REQUEST).entity("Invalid method " + s.method).build();
            }
            s.name = name;
            s.isTeam = team;
            em.merge(s);
         }
      }
      return Response.ok().build();
   }

   private static Set<String> merge(Set<String> set, String item) {
      if (set == null) {
         set = new HashSet<>();
      }
      set.add(item);
      return set;
   }

   @RolesAllowed({Roles.TESTER, Roles.ADMIN})
   @GET
   @Path("/testwatch")
   public Map<Integer, Set<String>> testwatch() {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         // TODO: do all of this in single obscure PSQL query
         List<Watch> personal = Watch.list("?1 IN elements(users)", identity.getPrincipal().getName());
         List<Watch> team = Watch.list("FROM watch w LEFT JOIN w.teams teams WHERE teams IN ?1", identity.getRoles());
         Map<Integer, Set<String>> result = new HashMap<>();
         personal.forEach(w -> result.compute(w.testId, (i, set) -> merge(set, identity.getPrincipal().getName())));
         team.forEach(w -> result.compute(w.testId, (i, set) -> {
            Set<String> nset = new HashSet<>(w.teams);
            nset.retainAll(identity.getRoles());
            if (set != null) {
               nset.addAll(set);
            }
            return nset;
         }));
         @SuppressWarnings("unchecked")
         Stream<Integer> results = em.createQuery("SELECT id FROM test").getResultStream();
         results.forEach(id -> result.putIfAbsent(id, Collections.emptySet()));
         return result;
      }
   }

   @RolesAllowed({Roles.TESTER, Roles.ADMIN})
   @GET
   @Path("/testwatch/{testId}")
   public Watch testwatch(@PathParam("testId") Integer testId) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         Watch watch = Watch.find("testId = ?1", testId).firstResult();
         if (watch == null) {
            watch = new Watch();
            watch.testId = testId;
            watch.teams = Collections.emptyList();
            watch.users = Collections.emptyList();
         }
         return watch;
      }
   }

   private static List<String> add(List<String> list, String item) {
      if (list == null) {
         list = new ArrayList<>();
      }
      list.add(item);
      return list;
   }

   @RolesAllowed({Roles.TESTER, Roles.ADMIN})
   @POST
   @Path("/testwatch/{testid}/add")
   @Transactional
   public Response addTestWatch(@PathParam("testid") Integer testId, String userOrTeam) {
      if (testId == null) {
         return Response.status(Response.Status.BAD_REQUEST).entity("Missing test id").build();
      } else if (userOrTeam == null) {
         return Response.status(Response.Status.BAD_REQUEST).entity("Missing user/team").build();
      } else if (userOrTeam.startsWith("\"") && userOrTeam.endsWith("\"") && userOrTeam.length() > 2) {
         userOrTeam = userOrTeam.substring(1, userOrTeam.length() - 1);
      }
      boolean isTeam = true;
      if (userOrTeam.equals("__self") || userOrTeam.equals(identity.getPrincipal().getName())) {
         userOrTeam = identity.getPrincipal().getName();
         isTeam = false;
      } else if (!userOrTeam.endsWith("-team")) {
         return Response.status(Response.Status.BAD_REQUEST).entity("Wrong user/team: " + userOrTeam).build();
      }
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         Watch watch = Watch.find("testid", testId).firstResult();
         if (watch == null) {
            watch = new Watch();
            watch.testId = testId;
         }
         if (isTeam) {
            watch.teams = add(watch.teams, userOrTeam);
         } else {
            watch.users = add(watch.users, userOrTeam);
         }
         watch.persist();
         return currentWatches(watch);
      }
   }

   @RolesAllowed({Roles.TESTER, Roles.ADMIN})
   @POST
   @Path("/testwatch/{testid}/remove")
   @Transactional
   public Response removeTestWatch(@PathParam("testid") Integer testId, String who) {
      if (testId == null) {
         return Response.status(Response.Status.BAD_REQUEST).entity("Missing test id").build();
      } else if (who == null) {
         return Response.status(Response.Status.BAD_REQUEST).entity("Missing user/team").build();
      } else if (who.startsWith("\"") && who.endsWith("\"") && who.length() > 2) {
         who = who.substring(1, who.length() - 1);
      }
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         Watch watch = Watch.find("testid", testId).firstResult();
         if (watch == null) {
            return Response.ok("[]").build();
         }
         if (who.equals("__self") || who.equals(identity.getPrincipal().getName())) {
            if (watch.users != null) {
               watch.users.remove(identity.getPrincipal().getName());
            }
         } else if (who.endsWith("-team") && identity.getRoles().contains(who)) {
            if (watch.teams != null) {
               watch.teams.remove(who);
            }
         } else {
            return Response.status(Response.Status.BAD_REQUEST).entity("Wrong user/team: " + who).build();
         }
         watch.persist();
         return currentWatches(watch);
      }
   }

   @RolesAllowed({Roles.TESTER, Roles.ADMIN})
   @POST
   @Path("/testwatch/{testid}")
   @Transactional
   public void addTestWatch(@PathParam("testid") Integer testId, Watch watch) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         Watch existing = Watch.find("testid", testId).firstResult();
         if (existing == null) {
            watch.id = null;
            watch.testId = testId;
            watch.persistAndFlush();
         } else {
            existing.users = watch.users;
            existing.teams = watch.teams;
            existing.persistAndFlush();
         }
      }
   }

   private Response currentWatches(Watch watch) {
      ArrayList<String> own = new ArrayList<>(identity.getRoles());
      own.add(identity.getPrincipal().getName());
      ArrayList<String> all = new ArrayList<>();
      if (watch.teams != null) {
         all.addAll(watch.teams);
      }
      if (watch.users != null) {
         all.addAll(watch.users);
      }
      all.retainAll(own);
      return Response.ok(all).build();
   }

   public void cacheUserTeams(String username, Set<String> teams) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, Collections.singletonList(AlertingService.HORREUM_ALERTING))) {
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
