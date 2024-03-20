package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.api.internal.services.UserService;
import io.hyperfoil.tools.horreum.entity.user.UserInfo;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.hyperfoil.tools.horreum.svc.user.UserBackEnd;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

import static java.text.MessageFormat.format;

@Authenticated
@ApplicationScoped
public class UserServiceImpl implements UserService {
    private static final Logger LOG = Logger.getLogger(UserServiceImpl.class);

    @Inject SecurityIdentity identity;

    @Inject Instance<UserBackEnd> backend;

    @Override public List<String> getRoles() {
        return identity.getRoles().stream().toList();
    }

    @Override public List<UserData> searchUsers(String query) {
        return backend.get().searchUsers(query);
    }

    // @RolesAllowed({ Roles.VIEWER, Roles.TESTER, Roles.ADMIN })
    @Override public List<UserData> info(List<String> usernames) {
        return backend.get().info(usernames);
    }

    // ideally we want to enforce these roles in some of the endpoints, but for now this has to be done in the code
    // @RolesAllowed({ Roles.ADMIN, Roles.MANAGER })
    @Override public void createUser(NewUser user) {
        if (user == null) {
            throw ServiceException.badRequest("Missing user as the request body");
        } else if (user.team != null && !user.team.endsWith("-team")) {
            throw ServiceException.badRequest(format("Team {0} does not end with ´´-team´´ suffix", user.team));
        }
        userIsManagerForTeam(user.team);

        backend.get().createUser(user);
        LOG.infov("{0} created user {1} {2} with username {3} on team {4}", identity.getPrincipal().getName(), user.user.firstName, user.user.lastName, user.user.username, user.team);
    }

    @Override public List<String> getTeams() {
        return backend.get().getTeams();
    }

    @WithRoles(addUsername = true)
    @Override public String defaultTeam() {
        UserInfo userInfo = UserInfo.findById(identity.getPrincipal().getName());
        return userInfo != null ? userInfo.defaultTeam : null;
    }

    @Transactional
    @WithRoles(addUsername = true)
    @Override public void setDefaultTeam(String team) {
        UserInfo userInfo = UserInfo.findById(identity.getPrincipal().getName());
        userInfo.defaultTeam = Util.destringify(team);
        userInfo.persistAndFlush();
    }

    // @RolesAllowed({ Roles.ADMIN, Roles.MANAGER })
    @Override public Map<String, List<String>> teamMembers(String team) {
        userIsManagerForTeam(team);
        return backend.get().teamMembers(team);
    }

    // @RolesAllowed({ Roles.ADMIN, Roles.MANAGER })
    @Override public void updateTeamMembers(String team, Map<String, List<String>> roles) {
        userIsManagerForTeam(team);
        backend.get().updateTeamMembers(team, roles);
    }

    @RolesAllowed(Roles.ADMIN)
    @Override public List<String> getAllTeams() {
        return backend.get().getAllTeams();
    }

    @RolesAllowed(Roles.ADMIN)
    @Override public void addTeam(String team) {
        backend.get().addTeam(team);
        LOG.infov("{0} created team {0}", identity.getPrincipal().getName(), team);
    }

    @RolesAllowed(Roles.ADMIN)
    @Override public void deleteTeam(String team) {
        backend.get().deleteTeam(team);
        LOG.infov("{0} deleted team {0}", identity.getPrincipal().getName(), team);
    }

    @RolesAllowed(Roles.ADMIN)
    @Override public List<UserData> administrators() {
        return backend.get().administrators();
    }

    @RolesAllowed(Roles.ADMIN)
    @Override public void updateAdministrators(List<String> newAdmins) {
        if (!newAdmins.contains(identity.getPrincipal().getName())) {
            throw ServiceException.badRequest("Cannot remove yourself from administrator list");
        }
        backend.get().updateAdministrators(newAdmins);
    }

    private void userIsManagerForTeam(String team) {
        if (!identity.getRoles().contains(Roles.ADMIN) && !identity.hasRole(getTeamPrefix(team) + Roles.MANAGER)) {
            throw ServiceException.badRequest(format("This user is not a manager for team {0}", team));
        }
    }

    public static String getTeamPrefix(String team) {
        if (team == null || team.isBlank()) {
            throw ServiceException.badRequest("No team name!!!");
        } else if (team.startsWith("horreum.")) {
            throw ServiceException.badRequest("Team names starting with 'horreum.' are reserved for internal use");
        } else if (!team.endsWith("-team")) {
            throw ServiceException.badRequest("Team name must end with '-team' suffix");
        } else if (team.length() > 64) {
            throw ServiceException.badRequest("Team name too long. Please think on a shorter team name!!!");
        }
        return team.substring(0, team.length() - 4);
    }
}
