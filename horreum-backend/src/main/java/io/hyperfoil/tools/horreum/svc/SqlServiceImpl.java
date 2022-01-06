package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.api.SqlService;
import io.hyperfoil.tools.horreum.server.RoleManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnection;

import javax.annotation.PostConstruct;
import javax.annotation.security.PermitAll;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.transaction.TransactionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.JDBCException;
import org.hibernate.transform.ResultTransformer;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SqlServiceImpl implements SqlService {
   private static final Logger log = Logger.getLogger(SqlServiceImpl.class);

   @Inject
   EntityManager em;

   @Inject
   io.vertx.mutiny.pgclient.PgPool client;

   PgConnection listenerConnection;
   final Map<String, List<Consumer<String>>> listeners = new HashMap<>();

   @Inject
   Vertx vertx;

   @Inject
   TransactionManager tm;

   @Inject
   SecurityIdentity identity;

   @Inject
   RoleManager roleManager;

   @ConfigProperty(name = "horreum.debug")
   Optional<Boolean> debug;

   @SuppressWarnings("deprecation")
   public static void setResultTransformer(Query query, ResultTransformer transformer) {
      query.unwrap(org.hibernate.query.Query.class).setResultTransformer(transformer);
   }

   static void setFromException(PersistenceException pe, JsonpathValidation result) {
      result.valid = false;
      if (pe.getCause() instanceof JDBCException) {
         JDBCException je = (JDBCException) pe.getCause();
         result.errorCode = je.getErrorCode();
         result.sqlState = je.getSQLState();
         result.reason = je.getSQLException().getMessage();
         result.sql = je.getSQL();
      } else {
         result.reason = pe.getMessage();
      }
   }

   @Override
   @PermitAll
   public JsonpathValidation testJsonPath(String jsonpath) {
      if (jsonpath == null) {
         throw ServiceException.badRequest("No query");
      }
      return testJsonPathInternal(jsonpath);
   }

   JsonpathValidation testJsonPathInternal(String jsonpath) {
      jsonpath = jsonpath.trim();
      JsonpathValidation result = new JsonpathValidation();
      result.jsonpath = jsonpath;
      if (jsonpath.startsWith("strict") || jsonpath.startsWith("lax")) {
         result.valid = false;
         result.reason = "Horreum always uses lax (default) jsonpaths.";
         return result;
      }
      if (!jsonpath.startsWith("$")) {
         result.valid = false;
         result.reason = "Jsonpath should start with '$'";
         return result;
      }
      Query query = em.createNativeQuery("SELECT jsonb_path_query_first('{}', ?::::jsonpath)::::text");
      query.setParameter(1, jsonpath);
      try {
         query.getSingleResult();
         result.valid = true;
      } catch (PersistenceException pe) {
         setFromException(pe, result);
      }
      return result;
   }

   @PostConstruct
   void init() {
      listenerConnection = (PgConnection) client.getConnectionAndAwait().getDelegate();
      listenerConnection.notificationHandler(notification ->
            vertx.executeBlocking(any -> handleNotification(notification.getChannel(), notification.getPayload())));
   }

   public void registerListener(String channel, Consumer<String> consumer) {
      synchronized (listeners) {
         List<Consumer<String>> consumers = listeners.get(channel);
         if (consumers == null) {
            listenerConnection.query("LISTEN \"" + channel + "\"").execute()
                  .onFailure(e -> log.errorf(e, "Failed to register PostgreSQL notification listener on channel %s", channel))
                  .onSuccess(ignored -> log.infof("Listening for PostgreSQL notification on channel %s", channel));
            consumers = new ArrayList<>();
            listeners.put(channel, consumers);
         }
         consumers.add(consumer);
      }
   }

   private void handleNotification(String channel, String payload) {
      synchronized (listeners) {
         List<Consumer<String>> consumers = listeners.get(channel);
         if (consumers != null) {
            for (Consumer<String> c : consumers) {
               Util.withTx(tm, () -> {
                  c.accept(payload);
                  return null;
               });
            }
         }
      }
   }

   @Override
   @PermitAll
   public String roles() {
      if (!debug.orElse(false)) {
         throw ServiceException.notFound("Not available without debug mode.");
      }
      if (identity.isAnonymous()) {
         return "<anonymous>";
      }
      return roleManager.getDebugQuery(identity);
   }
}
