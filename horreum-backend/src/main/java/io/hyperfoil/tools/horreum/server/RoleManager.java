package io.hyperfoil.tools.horreum.server;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;

import io.hyperfoil.tools.horreum.svc.Roles;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;

@ApplicationScoped
public class RoleManager {
    // from `set_config`documentation: "If is_local is true, the new value will only apply during the current transaction."
    static final String SET_ROLES = "SELECT current_setting('horreum.userroles', true), set_config('horreum.userroles', ?, true)";
    static final CloseMe NOOP = () -> {
    };

    @Inject
    EntityManager em;

    @Inject
    TransactionManager txManager;

    String setRoles(Iterable<String> roles) {
        return setRoles(String.join(",", roles));
    }

    public String setRoles(String roles) {
        if (roles == null || roles.isEmpty() || Roles.HORREUM_SYSTEM.equals(roles)) {
            return "";
        }
        Object[] row = (Object[]) em.createNativeQuery(SET_ROLES).setParameter(1, roles).getSingleResult();

        if (Log.isDebugEnabled()) { // enable with: `quarkus.log.category."io.hyperfoil.tools.horreum.server.RoleManager".level=DEBUG`
            try {
                Log.debugf("Setting roles '%s' (replacing '%s') on transaction %s", roles, row[0], txManager.getTransaction());
            } catch (SystemException e) {
                Log.debugf("Setting roles '%s' (replacing '%s'), but obtaining current transaction failed due to %s", roles,
                        row[0], e.getMessage());
            }
        }
        return (String) row[0];
    }

    public CloseMe withRoles(Iterable<String> roles) {
        if (roles == null || !roles.iterator().hasNext()) {
            return NOOP;
        }
        String previous = setRoles(roles);
        return Roles.HORREUM_SYSTEM.equals(previous) ? NOOP : () -> setRoles(previous);
    }

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
