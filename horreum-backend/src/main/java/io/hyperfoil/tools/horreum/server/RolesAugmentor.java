package io.hyperfoil.tools.horreum.server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.hyperfoil.tools.horreum.svc.ServiceException;
import io.hyperfoil.tools.horreum.svc.user.UserBackEnd;
import io.quarkus.arc.profile.UnlessBuildProfile;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
@UnlessBuildProfile("test")
public class RolesAugmentor implements SecurityIdentityAugmentor {

    @Inject
    RoleManager roleManager;

    @ConfigProperty(name = "horreum.roles.database.override", defaultValue = "true")
    boolean override;

    @ConfigProperty(name = "horreum.roles.provider")
    String provider;

    @Inject
    Instance<UserBackEnd> backend;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        return identity.isAnonymous() ? Uni.createFrom().item(identity) : context.runBlocking(() -> addHorreumRoles(identity));
    }

    private SecurityIdentity addHorreumRoles(SecurityIdentity identity) {
        return switch (provider) {
            case "database" -> rolesFromDB(identity);
            case "keycloak" -> rolesFromKeycloak(identity);
            default -> identity;
        };
    }

    private SecurityIdentity rolesFromDB(SecurityIdentity identity) {
        String username = identity.getPrincipal().getName();
        String previousRoles = roleManager.setRoles(username);
        try {
            QuarkusSecurityIdentity.Builder builder;
            if (override) {
                builder = QuarkusSecurityIdentity.builder();
                builder.setAnonymous(false);
                builder.setPrincipal(identity.getPrincipal());
                builder.addAttributes(identity.getAttributes());
                builder.addCredentials(identity.getCredentials());
                builder.addPermissionChecker(identity::checkPermission);
            } else {
                builder = QuarkusSecurityIdentity.builder(identity);
            }
            backend.get().getRoles(username).forEach(builder::addRole);
            return builder.build();
        } catch (Exception e) {
            if (override) {
                throw ServiceException.serverError("Unable to fetch user entity");
            } else {
                return identity; // ignore exception when the user does not exist
            }
        } finally {
            roleManager.setRoles(previousRoles);
        }
    }

    private SecurityIdentity rolesFromKeycloak(SecurityIdentity identity) {
        // no roles mean authentication from a horreum auth token. only in that case fetch roles from keycloak
        if (identity.getRoles().isEmpty()) {
            QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);
            backend.get().getRoles(identity.getPrincipal().getName()).forEach(builder::addRole);
            return builder.build();
        } else {
            return identity;
        }
    }
}
