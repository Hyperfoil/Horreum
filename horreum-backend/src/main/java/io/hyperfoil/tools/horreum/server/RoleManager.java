package io.hyperfoil.tools.horreum.server;

import java.util.ArrayList;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import io.quarkus.security.identity.SecurityIdentity;

@ApplicationScoped
public class RoleManager {
   static final String SET_ROLES = "SELECT current_setting('horreum.userroles', true), set_config('horreum.userroles', ?, false)";
   static final String SET_TOKEN = "SELECT set_config('horreum.token', ?, false)";
   static final CloseMe NOOP = () -> {};

   @Inject
   EntityManager em;

   String setRoles(Iterable<String> roles) {
      return setRoles(String.join(",", roles));
   }

   String setRoles(String roles) {
      Query setRoles = em.createNativeQuery(SET_ROLES);
      setRoles.setParameter(1, roles == null ? "" : roles);
      Object[] row = (Object[]) setRoles.getSingleResult();
      return (String) row[0];
   }

   public CloseMe withRoles(Iterable<String> roles) {
      if (roles == null || !roles.iterator().hasNext()) {
         return NOOP;
      }
      String previous = setRoles(roles);
      return () -> setRoles(previous);
   }

   // --- //

   void setToken(EntityManager em, String token) {
      Query setToken = em.createNativeQuery(SET_TOKEN);
      setToken.setParameter(1, token == null ? "" : token);
      setToken.getSingleResult();
   }

   // --- //

   public String getDebugQuery(SecurityIdentity identity) {
      List<String> roles = new ArrayList<>(identity.getRoles());
      if (identity.getPrincipal() != null) {
         roles.add(identity.getPrincipal().getName());
      }
      return getDebugQuery(roles);
   }

   public String getDebugQuery(Iterable<String> roles) {
      return SET_ROLES.replace("?", '\'' + String.join(",", roles) + '\'');
   }
}
