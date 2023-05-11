package io.hyperfoil.tools.horreum.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.security.identity.SecurityIdentity;

@ApplicationScoped
public class RoleManager {
   private static final Logger log = Logger.getLogger(RoleManager.class);

   static final String SET_ROLES = "SELECT current_setting('horreum.userroles', true), set_config('horreum.userroles', ?, false)";
   static final String SET_TOKEN = "SELECT set_config('horreum.token', ?, false)";
   static final CloseMe NOOP = () -> {};

   private final Map<String, String> signedRoleCache = new ConcurrentHashMap<>();

   @ConfigProperty(name = "horreum.db.secret")
   String dbSecret;
   byte[] dbSecretBytes;

   @Inject
   EntityManager em;

   @PostConstruct
   void init() {
      dbSecretBytes = dbSecret.getBytes(StandardCharsets.UTF_8);
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

   String setRoles(Collection<String> roles) {
      String signedRoles;
      if (roles == null || roles.isEmpty()) {
         signedRoles = "";
      } else {
         try {
            signedRoles = getSignedRoles(roles);
         } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
         }
      }
      return setRoles(signedRoles);
   }

   String setRoles(String signedRoles) {
      Query setRoles = em.createNativeQuery(SET_ROLES);
      setRoles.setParameter(1, signedRoles == null ? "" : signedRoles);
      Object[] row = (Object[]) setRoles.getSingleResult();
      return (String) row[0];
   }

   public CloseMe withRoles(Iterable<String> roles) {
      String signedRoles;
      try {
         signedRoles = getSignedRoles(roles);
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
      return withRoles(signedRoles);
   }

   public CloseMe withRoles(String signedRoles) {
      if (signedRoles == null || signedRoles.isEmpty()) {
         return NOOP;
      }
      String previous = setRoles(signedRoles);
      return () -> setRoles(previous);
   }

   void setToken(EntityManager em, String token) {
      if (token == null) {
         token = "";
      }
      Query setToken = em.createNativeQuery(SET_TOKEN);
      setToken.setParameter(1, token);
      setToken.getSingleResult();
   }

   public String getDebugQuery(SecurityIdentity identity) {
      List<String> roles = new ArrayList<>(identity.getRoles());
      if (identity.getPrincipal() != null) {
         roles.add(identity.getPrincipal().getName());
      }
      return getDebugQuery(roles);
   }

   public String getDebugQuery(Collection<String> roles) {
      try {
         return SET_ROLES.replace("?", '\'' + getSignedRoles(roles) + '\'');
      } catch (NoSuchAlgorithmException e) {
         return "<error>";
      }
   }
}
