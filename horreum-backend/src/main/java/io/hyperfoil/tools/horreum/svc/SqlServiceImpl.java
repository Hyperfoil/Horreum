package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.api.services.SqlService;
import io.hyperfoil.tools.horreum.server.ErrorReporter;
import io.hyperfoil.tools.horreum.server.RoleManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.subscription.UniSubscriber;
import io.smallrye.mutiny.subscription.UniSubscription;
import io.vertx.core.Vertx;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.pgclient.PgConnection;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.eclipse.microprofile.config.ConfigProvider;
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
   long listenerInitTimer;
   boolean shuttingDown;

   @Inject
   Vertx vertx;

   @Inject
   SecurityIdentity identity;

   @Inject
   RoleManager roleManager;

   @ConfigProperty(name = "horreum.debug")
   Optional<Boolean> debug;

   @Inject
   ErrorReporter errorReporter;

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
      String datasourceJdbcUrl = ConfigProvider.getConfig().getValue("quarkus.datasource.jdbc.url", String.class);
      if (datasourceJdbcUrl != null && !datasourceJdbcUrl.isBlank()) {
         initListenerConnection();
      }
   }

   private void initListenerConnection() {
      client.getConnection().subscribe().withSubscriber(new UniSubscriber<SqlConnection>() {
         @Override
         public void onSubscribe(UniSubscription subscription) {
         }

         @Override
         public void onItem(SqlConnection connection) {
            synchronized (listeners) {
               listenerConnection = (PgConnection) connection.getDelegate();
               listenerConnection.notificationHandler(notification ->
                     handleNotification(notification.getChannel(), notification.getPayload()));
               listenerConnection.exceptionHandler(t -> log.error("Listener connection experienced an exception!"));
               for (String channel : listeners.keySet()) {
                  listenOn(channel);
               }
               listenerConnection.closeHandler(nil -> {
                  synchronized (listeners) {
                     listenerConnection = null;
                     if (shuttingDown) {
                        log.info("Shutting down, listener connection won't be established.");
                     } else {
                        log.warn("Listener connection was closed, reconnecting in 10 ms");
                        listenerInitTimer = vertx.setTimer(10, timerId -> initListenerConnection());
                     }
                  }
               });
            }
         }

         @Override
         public void onFailure(Throwable failure) {
            synchronized (listeners) {
               if (!shuttingDown) {
                  log.error("Failed to allocate listener connection, will try again in 10 ms", failure);
                  // We don't want to overflow the stack if the call fails immediately
                  listenerInitTimer = vertx.setTimer(10, timerId -> initListenerConnection());
               } else {
                  log.info("Horreum is shutting down, listener connection error ignored");
                  log.debug("Connection error (ignored)", failure);
               }
            }
         }
      });
   }

   public void registerListener(String channel, Consumer<String> consumer) {
      synchronized (listeners) {
         List<Consumer<String>> consumers = listeners.get(channel);
         if (consumers == null) {
            listenOn(channel);
            consumers = new ArrayList<>();
            listeners.put(channel, consumers);
         }
         consumers.add(consumer);
      }
   }

   private void listenOn(String channel) {
      if (listenerConnection != null) {
         listenerConnection.query("LISTEN \"" + channel + "\"").execute()
               .onFailure(e -> log.errorf(e, "Failed to register PostgreSQL notification listener on channel %s", channel))
               .onSuccess(ignored -> log.infof("Listening for PostgreSQL notification on channel %s", channel));
      }
   }

   private void handleNotification(String channel, String payload) {
      synchronized (listeners) {
         List<Consumer<String>> consumers = listeners.get(channel);
         if (consumers != null) {
            for (Consumer<String> c : consumers) {
               try {
                  c.accept(payload);
               } catch (Exception e) {
                  errorReporter.reportException(e, "Error in DB listener", "Exception in listener for channel %s, payload %s", channel, payload);
               }
            }
         }
      }
   }

   @PreDestroy
   void stopListenerInitRetries() {
      synchronized (listeners) {
         vertx.cancelTimer(listenerInitTimer);
         shuttingDown = true;
      }
   }

   @Override
   @PermitAll
   public String roles(boolean system) {
      if (!debug.orElse(false)) {
         throw ServiceException.notFound("Not available without debug mode.");
      }
      if (identity.isAnonymous()) {
         return "<anonymous>";
      }
      if (system) {
         if (identity.hasRole(Roles.ADMIN)) {
            return roleManager.getDebugQuery(Collections.singletonList(Roles.HORREUM_SYSTEM));
         } else {
            throw ServiceException.forbidden("Only Admin can request system roles");
         }
      }
      return roleManager.getDebugQuery(identity);
   }
}
