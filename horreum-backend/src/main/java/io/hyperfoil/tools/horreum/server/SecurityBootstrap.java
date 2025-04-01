package io.hyperfoil.tools.horreum.server;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import io.hyperfoil.tools.horreum.api.services.UserService;
import io.hyperfoil.tools.horreum.entity.user.Team;
import io.hyperfoil.tools.horreum.entity.user.TeamMembership;
import io.hyperfoil.tools.horreum.entity.user.TeamRole;
import io.hyperfoil.tools.horreum.entity.user.UserInfo;
import io.hyperfoil.tools.horreum.entity.user.UserRole;
import io.hyperfoil.tools.horreum.svc.Roles;
import io.hyperfoil.tools.horreum.svc.UserServiceImpl;
import io.hyperfoil.tools.horreum.svc.user.UserBackEnd;
import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class SecurityBootstrap {

    @ConfigProperty(name = "quarkus.keycloak.admin-client.server-url")
    Optional<String> keycloakURL;
    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm", defaultValue = "horreum")
    String realm;

    @ConfigProperty(name = "horreum.roles.provider", defaultValue = "keycloak")
    String provider;

    @ConfigProperty(name = "horreum.bootstrap.password")
    Optional<String> providedBootstrapPassword;

    private static final String MIGRATION_PROVIDER = "database";
    private static final String BOOTSTRAP_ACCOUNT = "horreum.bootstrap";

    private static final char[] RANDOM_PASSWORD_CHARS = ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789")
            .toCharArray();
    private static final int RANDOM_PASSWORD_DEFAULT_LENGTH = 16;

    @Inject
    RoleManager roleManager;

    @Inject
    Instance<UserBackEnd> backend;

    void onStart(@Observes StartupEvent event, Keycloak keycloak) {
        if (keycloakURL.isPresent() && performRolesMigration()) {
            Log.info("Perform roles migration from keycloak...");
            for (UserRepresentation kcUser : keycloak.realm(realm).users().list(0, Integer.MAX_VALUE)) {
                performUserMigration(kcUser,
                        keycloak.realm(realm).users().get(kcUser.getId()).roles().getAll().getRealmMappings());
            }
            Log.info("Migration from keycloak complete");
        }
        checkBootstrapAccount();
    }

    private boolean performRolesMigration() {
        // an empty `userinfo_teams` table is the hint to perform migration of roles from keycloak
        // only migrate if we users have defined the "database" provider
        try {
            roleManager.setRoles(Roles.HORREUM_SYSTEM);
            return MIGRATION_PROVIDER.equals(provider) && TeamMembership.count() == 0;
        } finally {
            roleManager.setRoles("");
        }
    }

    @Transactional
    void performUserMigration(UserRepresentation kcUser, List<RoleRepresentation> kcRoles) {
        Log.infof("Migration of user '%s %s' with username '%s'", kcUser.getFirstName(), kcUser.getLastName(),
                kcUser.getUsername());
        String previousRoles = roleManager.setRoles(kcUser.getUsername());
        try {

            Optional<UserInfo> storedUserInfo = UserInfo.findByIdOptional(kcUser.getUsername());
            UserInfo userInfo = storedUserInfo.orElseGet(() -> new UserInfo(kcUser.getUsername()));
            userInfo.email = kcUser.getEmail();
            userInfo.firstName = kcUser.getFirstName();
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
                    Log.infof("Dropping role '%s' for user '%s %s'}", role, kcUser.getFirstName(), kcUser.getLastName());
                }
            }
            userInfo.persist();
        } catch (Exception e) {
            Log.warnf("Unable to perform migration for user '%s %s' due to '%s'", kcUser.getFirstName(), kcUser.getLastName(),
                    e.getMessage());
        } finally {
            roleManager.setRoles(previousRoles);
        }
    }

    private void addTeamMembership(UserInfo userInfo, String teamName, TeamRole role) {
        Optional<Team> storedTeam = Team.find("teamName", teamName).firstResultOptional();
        userInfo.teams.add(new TeamMembership(userInfo,
                storedTeam.orElseGet(() -> Team.getEntityManager().merge(new Team(teamName))), role));
    }

    // --- //

    /**
     * Create an admin account if there are no accounts in the system.
     * The account should be removed once other accounts are created.
     */
    public void checkBootstrapAccount() {
        // checks the list of administrators. a user cannot remove himself nor create the bootstrap account (restricted namespace)
        List<String> administrators = backend.get().administrators().stream().map(userData -> userData.username).toList();
        if (administrators.isEmpty()) {
            UserService.NewUser user = new UserService.NewUser();
            user.user = new UserService.UserData("", BOOTSTRAP_ACCOUNT, "Bootstrap", "Account", "horreum@example.com");
            user.password = providedBootstrapPassword.orElseGet(() -> LaunchMode.current().isDevOrTest() ? "secret"
                    : generateRandomPassword(RANDOM_PASSWORD_DEFAULT_LENGTH));

            // create bootstrap account with admin role
            backend.get().createUser(user);
            backend.get().setPassword(BOOTSTRAP_ACCOUNT, user.password); // KeycloakUserBackend.createUser() creates a temp password, with this call the password is usable
            backend.get().updateAdministrators(List.of(BOOTSTRAP_ACCOUNT));

            // create dev-team managed by bootstrap
            backend.get().addTeam("dev-team");
            backend.get().updateTeamMembers("dev-team",
                    Map.of(BOOTSTRAP_ACCOUNT, List.of(Roles.MANAGER, Roles.TESTER, Roles.UPLOADER, Roles.VIEWER)));

            UserServiceImpl.createLocalUser(BOOTSTRAP_ACCOUNT, "dev-team");

            Log.infof("\n>>>\n>>> Created temporary account '%s' with password %s\n>>>", BOOTSTRAP_ACCOUNT, user.password);
        } else if (administrators.size() > 1 && administrators.contains(BOOTSTRAP_ACCOUNT)) {
            Log.warnf("The temporary account '%s' can be removed", BOOTSTRAP_ACCOUNT);
        }
    }

    public static String generateRandomPassword(int length) {
        StringBuilder builder = new StringBuilder(length);
        new SecureRandom().ints(length, 0, RANDOM_PASSWORD_CHARS.length).mapToObj(i -> RANDOM_PASSWORD_CHARS[i])
                .forEach(builder::append);
        return builder.toString();
    }

}
