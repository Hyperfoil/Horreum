package io.hyperfoil.tools.horreum.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.NotificationSettings;
import io.hyperfoil.tools.horreum.entity.alerting.Variable;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.notification.NotificationPlugin;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
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
            "JOIN watch_teams wt ON ns.isteam AND ns.name = wt.teams" +
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
   public void onNewChange(Change change) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, Collections.singletonList(AlertingService.HORREUM_ALERTING))) {
         Variable variable = change.dataPoint.variable;
         // TODO: breaks storage/alerting separation!
         Test test = Test.findById(variable.testId);
         // Test might be null when it's private
         String testName = test == null ? "unknown" : test.name;
         log.infof("Received new change in test %d (%s), variable %d (%s)", variable.testId, testName, variable.id, variable.name);

         @SuppressWarnings("unchecked")
         List<Object[]> results = em.createNativeQuery(GET_NOTIFICATIONS)
               .setParameter(1, variable.testId).getResultList();
         for (Object[] pair : results) {
            if (pair.length != 3) {
               log.errorf("Unexpected result %s", Arrays.toString(pair));
            }
            String method = String.valueOf(pair[0]);
            String data = String.valueOf(pair[1]);
            String name = String.valueOf(pair[2]);
            NotificationPlugin plugin = plugins.get(method);
            if (plugin == null) {
               log.errorf("Cannot notify %s; no plugin for method %s with data %s", name, method, data);
            } else {
               plugin.notify(testName, name, data, change);
            }
         }
      }
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
}
