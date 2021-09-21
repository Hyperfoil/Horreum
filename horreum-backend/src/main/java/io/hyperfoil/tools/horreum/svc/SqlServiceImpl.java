package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.api.SqlService;
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
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.JDBCException;
import org.hibernate.transform.ResultTransformer;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SqlServiceImpl implements SqlService {
   private static final Logger log = Logger.getLogger(SqlServiceImpl.class);

   private static final String SET_ROLES = "SELECT set_config('horreum.userroles', ?, ?)";
   private static final String SET_TOKEN = "SELECT set_config('horreum.token', ?, ?)";
   private static final CloseMe NOOP = () -> {};

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

   @ConfigProperty(name = "horreum.db.secret")
   String dbSecret;
   byte[] dbSecretBytes;

   @ConfigProperty(name = "horreum.debug")
   Optional<Boolean> debug;

   private final Map<String, String> signedRoleCache = new ConcurrentHashMap<>();

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
      dbSecretBytes = dbSecret.getBytes(StandardCharsets.UTF_8);
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
               withTx(() -> {
                  c.accept(payload);
                  return null;
               });
            }
         }
      }
   }

   private String getSignedRoles(Iterable<String> roles) throws NoSuchAlgorithmException {
      StringBuilder sb = new StringBuilder();
      for (String role : roles) {
         String signedRole = signedRoleCache.get(role);
         if (signedRole == null) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(role.getBytes(StandardCharsets.UTF_8));
            String salt = Long.toHexString(ThreadLocalRandom.current().nextLong());
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            digest.update(dbSecretBytes);
            String signature = Base64.getEncoder().encodeToString(digest.digest());
            signedRole = role + ':' + salt + ':' + signature;
            // We don't care about race conditions, all signed roles are equally correct
            signedRoleCache.putIfAbsent(role, signedRole);
         }
         if (sb.length() != 0) {
            sb.append(',');
         }
         sb.append(signedRole);
      }
      return sb.toString();
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
      List<String> roles = new ArrayList<>(identity.getRoles());
      roles.add(identity.getPrincipal().getName());
      try {
         return SET_ROLES.replace("?", '\'' + getSignedRoles(roles) + '\'');
      } catch (NoSuchAlgorithmException e) {
         return "<error>";
      }
   }

   CloseMe withRoles(EntityManager em, Iterable<String> roles) {
      String signedRoles;
      try {
         signedRoles = getSignedRoles(roles);
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
      return withRoles(em, signedRoles);
   }

   CloseMe withRoles(EntityManager em, SecurityIdentity identity) {
      if (identity.isAnonymous()) {
         return NOOP;
      }
      String signedRoles;
      try {
         List<String> roles = new ArrayList<>(identity.getRoles());
         roles.add(identity.getPrincipal().getName());
         signedRoles = getSignedRoles(roles);
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
      return withRoles(em, signedRoles);
   }

   CloseMe withRoles(EntityManager em, String signedRoles) {
      if (signedRoles == null || signedRoles.isEmpty()) {
         return NOOP;
      }
      Query setRoles = em.createNativeQuery(SET_ROLES);
      setRoles.setParameter(1, signedRoles);
      boolean inTx = isInTx();
      setRoles.setParameter(2, inTx);
      setRoles.getSingleResult(); // ignored
      // The config was set only for the scope of current transaction
      return inTx ? NOOP : (() -> {
         Query unsetRoles = em.createNativeQuery(SET_ROLES);
         unsetRoles.setParameter(1, "");
         unsetRoles.setParameter(2, false);
         unsetRoles.getSingleResult(); // ignored
      });
   }

   private boolean isInTx() {
      try {
         return tm.getStatus() != Status.STATUS_NO_TRANSACTION;
      } catch (SystemException e) {
         log.error("Error retrieving TX status", e);
         return false;
      }
   }

   CloseMe withToken(EntityManager em, String token) {
      if (token == null || token.isEmpty()) {
         return NOOP;
      } else {
         Query setToken = em.createNativeQuery(SET_TOKEN);
         setToken.setParameter(1, token);
         boolean inTx = isInTx();
         setToken.setParameter(2, inTx);
         setToken.getSingleResult();
         return inTx ? NOOP : (() -> {
            Query unsetToken = em.createNativeQuery(SET_TOKEN);
            unsetToken.setParameter(1, "");
            unsetToken.setParameter(2, false);
            unsetToken.getSingleResult();
         });
      }
   }

   <T> T withTx(Supplier<T> supplier) {
      try {
         tm.begin();
         try {
            return supplier.get();
         } catch (Throwable t) {
            log.error("Failure in transaction", t);
            tm.setRollbackOnly();
            throw t;
         } finally {
            if (tm.getStatus() == Status.STATUS_ACTIVE) {
               tm.commit();
            } else {
               tm.rollback();
            }
         }
      } catch (SystemException | RollbackException | HeuristicMixedException | HeuristicRollbackException | NotSupportedException ex) {
         log.error("Failed to run transaction", ex);
      }
      return null;
   }
}
