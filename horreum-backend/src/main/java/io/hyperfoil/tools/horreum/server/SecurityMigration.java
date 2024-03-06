package io.hyperfoil.tools.horreum.server;

import io.hyperfoil.tools.horreum.entity.user.Team;
import io.hyperfoil.tools.horreum.entity.user.TeamMembership;
import io.hyperfoil.tools.horreum.entity.user.TeamRole;
import io.hyperfoil.tools.horreum.entity.user.UserInfo;
import io.hyperfoil.tools.horreum.entity.user.UserRole;
import io.hyperfoil.tools.horreum.svc.Roles;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SecurityMigration {

    private static final Logger LOGGER = Logger.getLogger("SecurityMigration");

    @ConfigProperty(name = "quarkus.keycloak.admin-client.server-url") Optional<String> keycloakURL;
    @ConfigProperty(name = "horreum.keycloak.realm", defaultValue = "horreum") String realm;

    @Inject RoleManager roleManager;

    void onStart(@Observes StartupEvent event, Keycloak keycloak) {
        if (keycloakURL.isPresent() && performRolesMigration()) {
            LOGGER.info("Perform roles migration from keycloak...");
            for (UserRepresentation kcUser : keycloak.realm(realm).users().list(0, Integer.MAX_VALUE)) {
                performUserMigration(kcUser, keycloak.realm(realm).users().get(kcUser.getId()).roles().getAll().getRealmMappings());
            }
            LOGGER.info("Migration from keycloak complete");
        }
    }

    private boolean performRolesMigration() {
        // an empty `userinfo_teams` table is the hint to perform migration of roles from keycloak
        try {
            roleManager.setRoles(Roles.HORREUM_SYSTEM);
            return TeamMembership.count() == 0;
        } finally {
            roleManager.setRoles("");
        }
    }

    @Transactional
    void performUserMigration(UserRepresentation kcUser, List<RoleRepresentation> kcRoles) {
        LOGGER.infov("Migration of user {0} {1} with username {2}", kcUser.getFirstName(), kcUser.getLastName(), kcUser.getUsername());
        String previousRoles = roleManager.setRoles(kcUser.getUsername());
        try {
            
            Optional<UserInfo> storedUserInfo = UserInfo.findByIdOptional(kcUser.getUsername());
            UserInfo userInfo = storedUserInfo.orElseGet(() -> new UserInfo(kcUser.getUsername()));
            userInfo.email = kcUser.getEmail();
            userInfo.fistName = kcUser.getFirstName();
            userInfo.lastName = kcUser.getLastName();

            for (RoleRepresentation kcRole : kcRoles) {
                String role = kcRole.getName();
                if (role.endsWith("-viewer")) {
                    addTeamMembership(userInfo, role.substring(0, role.length() - 7), TeamRole.TEAM_VIEWER);
                } else if (role.endsWith("-tester")) {
                    addTeamMembership(userInfo, role.substring(0, role.length() - 7), TeamRole.TEAM_TESTER);
                } else if (role.endsWith("-uploader")) {
                   addTeamMembership(userInfo, role.substring(0, role.length() - 9), TeamRole.TEAM_UPLOADER);
                } else if (role.endsWith("-manager")) {
                    addTeamMembership(userInfo, role.substring(0, role.length() - 8), TeamRole.TEAM_MANAGER);
                } else if ("admin".equals(role)) {
                    userInfo.roles.add(UserRole.ADMIN);
                } else {
                    LOGGER.infov("Dropping role {0} for user {1} {2}", role, kcUser.getFirstName(), kcUser.getLastName());
                }
            }
            userInfo.persist();
        } catch (Exception e) {
            LOGGER.warnv("Unable to perform migration for user {0} {1} due to {2}", kcUser.getFirstName(), kcUser.getLastName(), e.getMessage());
        } finally {
            roleManager.setRoles(previousRoles);
        }
    }

    private void addTeamMembership(UserInfo userInfo, String teamName, TeamRole role) {
        Optional<Team> storedTeam = Team.find("teamName", teamName).firstResultOptional();
        userInfo.teams.add(new TeamMembership(userInfo, storedTeam.orElseGet(() -> Team.getEntityManager().merge(new Team(teamName))), role));
    }
}
