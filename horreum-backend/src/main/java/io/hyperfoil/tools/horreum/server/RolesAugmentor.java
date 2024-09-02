package io.hyperfoil.tools.horreum.server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.hyperfoil.tools.horreum.entity.user.TeamMembership;
import io.hyperfoil.tools.horreum.entity.user.UserInfo;
import io.hyperfoil.tools.horreum.entity.user.UserRole;
import io.hyperfoil.tools.horreum.svc.ServiceException;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.arc.profile.UnlessBuildProfile;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
@UnlessBuildProfile("test")
@LookupIfProperty(name = "horreum.roles.provider", stringValue = "database")
public class RolesAugmentor implements SecurityIdentityAugmentor {

    @Inject
    RoleManager roleManager;

    @ConfigProperty(name = "horreum.roles.database.override", defaultValue = "true")
    boolean override;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        return identity.isAnonymous() ? Uni.createFrom().item(identity) : context.runBlocking(() -> addHorreumRoles(identity));
    }

    private SecurityIdentity addHorreumRoles(SecurityIdentity identity) {
        String username = identity.getPrincipal().getName();
        String previousRoles = roleManager.setRoles(username);
        try {
            UserInfo user = UserInfo.findById(username);
            if (override) {
                if (user == null) {
                    throw ServiceException.serverError("Unable to fetch user entity");
                }
                QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder();
                builder.setAnonymous(false);
                builder.setPrincipal(identity.getPrincipal());
                builder.addAttributes(identity.getAttributes());
                builder.addCredentials(identity.getCredentials());
                builder.addPermissionChecker(identity::checkPermission);

                addRoles(builder, user);
                return builder.build();
            } else {
                if (user == null) {
                    return identity;
                }
                QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);
                addRoles(builder, user);
                return builder.build();
            }
        } finally {
            roleManager.setRoles(previousRoles);
        }
    }

    private void addRoles(QuarkusSecurityIdentity.Builder builder, UserInfo user) {
        user.roles.stream().map(UserRole::toString).map(String::toLowerCase).forEach(builder::addRole);
        user.teams.stream().map(TeamMembership::asRole).forEach(builder::addRole);
        user.teams.stream().map(TeamMembership::asTeam).forEach(builder::addRole);
        user.teams.stream().map(TeamMembership::asUIRole).distinct().forEach(builder::addRole);
    }
}
