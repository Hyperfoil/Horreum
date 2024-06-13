package io.hyperfoil.tools.horreum.svc.user;

import io.hyperfoil.tools.horreum.api.internal.services.UserService;
import io.hyperfoil.tools.horreum.entity.user.Team;
import io.hyperfoil.tools.horreum.entity.user.TeamMembership;
import io.hyperfoil.tools.horreum.entity.user.TeamRole;
import io.hyperfoil.tools.horreum.entity.user.UserInfo;
import io.hyperfoil.tools.horreum.entity.user.UserRole;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.hyperfoil.tools.horreum.svc.Roles;
import io.hyperfoil.tools.horreum.svc.ServiceException;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.text.MessageFormat.format;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toSet;

/**
 * Implementation of {@link UserBackEnd} that uses Horreum database for storage.
 * <p>
 * Relies on the {@link UserInfo} entity and it's mappings.
 */
@ApplicationScoped
@LookupIfProperty(name = "horreum.roles.provider", stringValue = "database")
public class DatabaseUserBackend implements UserBackEnd {

    private static final Logger LOG = Logger.getLogger(DatabaseUserBackend.class);

    private static UserService.UserData toUserInfo(UserInfo info) {
        return new UserService.UserData("", info.username, info.firstName, info.lastName, info.email);
    }

    private static String removeTeamSuffix(String team) {
        return team.substring(0, team.length() - 5);
    }

    @Transactional
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Override public List<UserService.UserData> searchUsers(String query) {
        List<UserInfo> users = UserInfo.list("lower(firstName) like ?1 or lower(lastName) like ?1 or lower(username) like ?1", "%" + query.toLowerCase() + "%");
        return users.stream().filter(user -> !user.roles.contains(UserRole.MACHINE)).map(DatabaseUserBackend::toUserInfo).toList();
    }

    @Transactional
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Override public List<UserService.UserData> info(List<String> usernames) {
        List<UserInfo> users = UserInfo.list("username in ?1", usernames);
        return users.stream().map(DatabaseUserBackend::toUserInfo).toList();
    }

    @Transactional
    @WithRoles(fromParams = NewUserParameterConverter.class, extras = Roles.HORREUM_SYSTEM)
    @Override public void createUser(UserService.NewUser user) {
        if (UserInfo.findByIdOptional(user.user.username).isPresent()) {
            throw ServiceException.badRequest("User exists with same username");
        }
        if (UserInfo.count("email", user.user.email) > 0) {
            throw ServiceException.badRequest("User exists with same email");
        }
        UserInfo userInfo = new UserInfo(user.user.username);
        userInfo.email = user.user.email;
        userInfo.firstName = user.user.firstName;
        userInfo.lastName = user.user.lastName;
        userInfo.setPassword(user.password);

        if (user.team != null) {
            // userInfo.defaultTeam = user.team; // Don't set default team to be consistent with keycloak backend
            String teamName = removeTeamSuffix(user.team);
            for (String role : user.roles) {
                if (Roles.VIEWER.equals(role)) {
                    addTeamMembership(userInfo, teamName, TeamRole.TEAM_VIEWER);
                } else if (Roles.TESTER.equals(role)) {
                    addTeamMembership(userInfo, teamName, TeamRole.TEAM_TESTER);
                } else if (Roles.UPLOADER.equals(role)) {
                    addTeamMembership(userInfo, teamName, TeamRole.TEAM_UPLOADER);
                } else if (Roles.MANAGER.equals(role)) {
                    addTeamMembership(userInfo, teamName, TeamRole.TEAM_MANAGER);
                } else if (!Roles.MACHINE.equals(role)) {
                    LOG.infov("Dropping role {0} for user {1} {2}", role, userInfo.firstName, userInfo.lastName);
                }
            }
        }
        if (user.roles != null && user.roles.contains(Roles.MACHINE)) {
            userInfo.roles.add(UserRole.MACHINE);
        }
        userInfo.persist();
    }

    private void addTeamMembership(UserInfo userInfo, String teamName, TeamRole role) {
        Optional<Team> storedTeam = Team.find("teamName", teamName).firstResultOptional();
        userInfo.teams.add(new TeamMembership(userInfo, storedTeam.orElseGet(() -> Team.getEntityManager().merge(new Team(teamName))), role));
    }

    @Transactional
    @WithRoles(fromParams = RemoveUserParameterConverter.class)
    @Override public void removeUser(String username) {
        if (!UserInfo.deleteById(username)) {
            throw ServiceException.notFound("User does not exist");
        }
    }

    @Transactional
    @Override public List<String> getTeams() {
        List<Team> teams = Team.listAll();
        return teams.stream().map(t -> t.teamName + "-team").toList();
    }

    @Transactional
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Override public Map<String, List<String>> teamMembers(String team) {
        Team teamEntity = Team.find("teamName", removeTeamSuffix(team)).firstResult();
        if (teamEntity == null) {
            return emptyMap();
        }

        Map<String, List<String>> userMap = new HashMap<>();
        teamEntity.teams.forEach(membership -> userMap.computeIfAbsent(membership.user.username, s -> new ArrayList<>()).add(membership.asUIRole()));
        return userMap;
    }

    @Transactional
    @WithRoles(fromParams = UpdateTeamMembersParameterConverter.class)
    @Override public void updateTeamMembers(String team, Map<String, List<String>> roles) {
        Team teamEntity = Team.find("teamName", removeTeamSuffix(team)).firstResult();
        if (teamEntity == null) {
            throw ServiceException.notFound(format("The team {0} does not exist", team));
        }

        // need to remove from the "owning" side of the relationship
        roles.forEach((username, teamRoles) -> {
            Optional<UserInfo> user = UserInfo.findByIdOptional(username);
            user.ifPresent(u -> {
                List<TeamMembership> removedMemberships = u.teams.stream().filter(t -> t.team == teamEntity && !teamRoles.contains(t.asUIRole())).toList();
                removedMemberships.forEach(TeamMembership::delete);
                removedMemberships.forEach(u.teams::remove);

                u.teams.addAll(teamRoles.stream().map(uiRole -> TeamMembership.getEntityManager().merge(new TeamMembership(user.get(), teamEntity, uiRole))).collect(toSet()));
            });
        });
    }

    @Transactional
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Override public List<String> getAllTeams() {
        List<Team> teams = Team.listAll();
        return teams.stream().map(t -> t.teamName + "-team").toList();
    }

    @Transactional
    @Override public void addTeam(String team) {
        Team.getEntityManager().merge(new Team(removeTeamSuffix(team)));
    }

    @Transactional
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Override public void deleteTeam(String team) {
        Team teamEntity = Team.find("teamName", removeTeamSuffix(team)).firstResult();
        if (teamEntity == null) {
            throw ServiceException.notFound(format("The team {0} does not exist", team));
        }
        // need to delete memberships with roles
        deleteTeamAndMemberships(teamEntity);
    }

    @Transactional
    @WithRoles(fromParams = DeleteTeamAndMembershipsParameterConverter.class)
    void deleteTeamAndMemberships(Team teamEntity) {
        try {
            // need to remove from the "owning" side of the relationship
            teamEntity.teams.stream().map(membership -> membership.user.username).forEach(username -> {
                Optional<UserInfo> user = UserInfo.findByIdOptional(username);
                user.ifPresent(u -> u.teams.removeIf(membership -> teamEntity.equals(membership.team)));
            });
            teamEntity.delete();
        } catch (Throwable t) {
            LOG.warnv("Unable to delete team {0} due to {1}", teamEntity.teamName, t.getMessage());
            throw ServiceException.serverError(format("Unable to delete team {0}", teamEntity.teamName));
        }
    }

    @Transactional
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Override public List<UserService.UserData> administrators() {
        return getAdministratorUsers().stream().map(DatabaseUserBackend::toUserInfo).toList();
    }

    @Transactional
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Override public void updateAdministrators(List<String> newAdmins) {
        getAdministratorUsers().forEach(u -> {
            if (!newAdmins.contains(u.username)) {
                u.roles.remove(UserRole.ADMIN);
                u.persist();
                LOG.infov("Removed administrator role from user {0}", u.username);
            }
        });
        newAdmins.forEach(username -> {
            Optional<UserInfo> user = UserInfo.findByIdOptional(username);
            user.ifPresent(u -> {
                u.roles.add(UserRole.ADMIN);
                u.persist();
                LOG.infov("Added administrator role to user {0}", username);
            });
        });
    }

    private List<UserInfo> getAdministratorUsers() {
        CriteriaBuilder cb = UserInfo.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<UserInfo> query = cb.createQuery(UserInfo.class);
        query.where(cb.isMember(UserRole.ADMIN, query.from(UserInfo.class).get("roles")));
        return UserInfo.getEntityManager().createQuery(query).getResultList();
    }

    @Transactional
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Override public List<UserService.UserData> machineAccounts(String team) {
        CriteriaBuilder cb = UserInfo.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<UserInfo> query = cb.createQuery(UserInfo.class);
        Root<UserInfo> userInfoRoot = query.from(UserInfo.class);
        query.where(cb.equal(userInfoRoot.get("defaultTeam"), team), cb.isMember(UserRole.MACHINE, userInfoRoot.get("roles")));
        return UserInfo.getEntityManager().createQuery(query).getResultStream().map(DatabaseUserBackend::toUserInfo).toList();
    }
    
    @Transactional
    @WithRoles(fromParams = ResetPasswordParameterConverter.class)
    @Override public void setPassword(String username, String password) {
        UserInfo user = UserInfo.findById(username);
        if (user == null) {
            throw ServiceException.notFound(format("User {0} not found", username));
        }
        user.setPassword(password);
    }

    /**
     * Extracts username from parameters of `createUser()`
     */
    public static final class NewUserParameterConverter implements Function<Object[], String[]> {
        @Override public String[] apply(Object[] objects) {
            return new String[] { ((UserService.NewUser) objects[0]).user.username };
        }
    }

    /**
     * Extract usernames from parameters of `removeUser()`
     */
    public static final class RemoveUserParameterConverter implements Function<Object[], String[]> {
        @Override public String[] apply(Object[] objects) {
            return new String[] {(String) objects[0]};
        }
    }

    /**
     * Extract usernames from parameters of `updateTeamMembers()`
     */
    public static final class UpdateTeamMembersParameterConverter implements Function<Object[], String[]> {
        @SuppressWarnings("unchecked")
        @Override public String[] apply(Object[] objects) {
            return ((Map<String, List<String>>) objects[1]).keySet().toArray(String[]::new);
        }
    }

    /**
     * Extract usernames from parameters of `deleteTeamAndMemberships()`
     */
    public static final class DeleteTeamAndMembershipsParameterConverter implements Function<Object[], String[]> {
        @Override public String[] apply(Object[] objects) {
            return ((Team) objects[0]).teams.stream().map(membership -> membership.user.username).toArray(String[]::new);
        }
    }

    /**
     * Extract usernames from parameters of `resetPassword()`
     */
    public static final class ResetPasswordParameterConverter implements Function<Object[], String[]> {
        @Override public String[] apply(Object[] objects) {
            return new String[] {(String) objects[0]};
        }
    }
}
