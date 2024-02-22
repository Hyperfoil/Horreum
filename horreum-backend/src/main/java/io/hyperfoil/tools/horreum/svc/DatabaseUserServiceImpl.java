package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.api.internal.services.UserService;
import io.hyperfoil.tools.horreum.entity.user.Team;
import io.hyperfoil.tools.horreum.entity.user.TeamMembership;
import io.hyperfoil.tools.horreum.entity.user.TeamRole;
import io.hyperfoil.tools.horreum.entity.user.UserInfo;
import io.hyperfoil.tools.horreum.entity.user.UserRole;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.arc.Unremovable;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@PermitAll
@ApplicationScoped
@Unremovable
@IfBuildProperty(name = "horreum.roles.provider", stringValue = "database")
public class DatabaseUserServiceImpl implements UserService {

   private static final Logger LOG = Logger.getLogger(DatabaseUserServiceImpl.class);

   @Inject SecurityIdentity identity;

   private static UserData toUserInfo(UserInfo info) {
      return new UserData("", info.username, info.fistName, info.lastName, info.email);
   }

   @Authenticated
   @Override public List<String> getRoles() {
      return identity.getRoles().stream().toList();
   }

   @Authenticated
   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Override public List<UserData> searchUsers(String query) {
      List<UserInfo> users = UserInfo.list("username like ?1", "%" + query + "%");
      return users.stream().map(DatabaseUserServiceImpl::toUserInfo).collect(toList());
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @RolesAllowed({Roles.VIEWER, Roles.TESTER, Roles.ADMIN})
   @Override public List<UserData> info(List<String> usernames) {
      List<UserInfo> users = UserInfo.list("username in ?1", usernames);
      return users.stream().map(DatabaseUserServiceImpl::toUserInfo).collect(toList());
   }

   @Transactional
   @WithRoles(fromParams = NewUserParameterConverter.class)
   @RolesAllowed({Roles.MANAGER, Roles.ADMIN})
   @Override public void createUser(NewUser user) {
      if (user == null) {
         throw ServiceException.badRequest("Missing user as the request body");
      } else if (user.team != null && !user.team.endsWith("-team")) {
         throw ServiceException.badRequest("Team must end with -team: " + user.team);
      }
      String prefix = user.team == null ? null : user.team.substring(0, user.team.length() - 4);
      if (prefix != null && !identity.getRoles().contains(prefix + Roles.MANAGER) && !identity.getRoles().contains(Roles.ADMIN)) {
         throw ServiceException.forbidden("This user is not a manager for team " + user.team);
      }

      Optional<UserInfo> storedUserInfo = UserInfo.findByIdOptional(user.user.username);
      UserInfo userInfo = storedUserInfo.orElseGet(() -> new UserInfo(user.user.username));
      userInfo.email = user.user.email;
      userInfo.fistName = user.user.firstName;
      userInfo.lastName = user.user.lastName;
      userInfo.setPassword(user.password);

      if (prefix != null) {
         for (String role : user.roles) {
            if ("viewer".equals(role)) {
               addTeamMembership(userInfo, prefix.substring(0, prefix.length() - 1), TeamRole.TEAM_VIEWER);
            } else if ("tester".equals(role)) {
               addTeamMembership(userInfo, prefix.substring(0, prefix.length() - 1), TeamRole.TEAM_TESTER);
            } else if ("uploader".equals(role)) {
               addTeamMembership(userInfo, prefix.substring(0, prefix.length() - 1), TeamRole.TEAM_UPLOADER);
            } else if ("manager".equals(role)) {
               addTeamMembership(userInfo, prefix.substring(0, prefix.length() - 1), TeamRole.TEAM_MANAGER);
            } else if ("admin".equals(role)) {
               userInfo.roles.add(UserRole.ADMIN);
            } else {
               LOG.infov("Dropping role {0} for user {1} {2}", role, userInfo.fistName, userInfo.lastName);
            }
         }
      }
      userInfo.persist();
   }

   private void addTeamMembership(UserInfo userInfo, String teamName, TeamRole role) {
      Optional<Team> storedTeam = Team.find("teamName", teamName).firstResultOptional();
      userInfo.teams.add(new TeamMembership(userInfo, storedTeam.orElseGet(() -> Team.getEntityManager().merge(new Team(teamName))), role));
   }

   public static final class NewUserParameterConverter implements Function<Object[], String[]> {
      @Override public String[] apply(Object[] objects) {
         return new String[] { ((NewUser) objects[0]).user.username };
      }
   }

   @Authenticated
   @Override public List<String> getTeams() {
      List<Team> teams = Team.listAll();
      return teams.stream().map(t -> t.teamName + "-team").collect(toList());
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

   @Authenticated
   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Override public Map<String, List<String>> teamMembers(String team) {
      if (!identity.getRoles().contains(getTeamPrefix(team) + Roles.MANAGER) && !identity.getRoles().contains(Roles.ADMIN)) {
         throw ServiceException.badRequest("This user is not a manager for team " + team);
      }
      Team teamEntity = Team.find("teamName", team.substring(0, team.length() - 5)).firstResult();
      if (teamEntity == null) {
         throw ServiceException.notFound("The team " + team + " does not exist");
      }

      Map<String, List<String>> userMap = new HashMap<>();
      teamEntity.teams.forEach(membership -> userMap.computeIfAbsent(membership.user.username, s -> new ArrayList<>()).add(membership.asUIRole()));
      return userMap;
   }

   @Transactional
   @Authenticated
   @WithRoles(fromParams = UpdateTeamMembersParameterConverter.class)
   @Override public void updateTeamMembers(String team, Map<String, List<String>> roles) {
      if (!identity.getRoles().contains(getTeamPrefix(team) + Roles.MANAGER) && !identity.getRoles().contains(Roles.ADMIN)) {
         throw ServiceException.forbidden("This user is does not have the manager role for team " + team);
      }
      Team teamEntity = Team.find("teamName", team.substring(0, team.length() - 5)).firstResult();
      if (teamEntity != null) {
         roles.forEach((username, teamRoles) -> {
            Optional<UserInfo> user = UserInfo.findByIdOptional(username);
            user.ifPresent(u -> {
               List<TeamMembership> removedMemberships = u.teams.stream().filter(t -> t.team == teamEntity && !teamRoles.contains(t.asUIRole())).toList();
               removedMemberships.forEach(TeamMembership::delete);
               u.teams.removeAll(removedMemberships);

               u.teams.addAll(teamRoles.stream().map(uiRole -> TeamMembership.getEntityManager().merge(new TeamMembership(user.get(), teamEntity, uiRole))).collect(toSet()));
            });
         });
      } else {
         throw ServiceException.notFound("The team " + team + " does not exist");
      }
   }
   
   public static final class UpdateTeamMembersParameterConverter implements Function<Object[], String[]> {
      @SuppressWarnings("unchecked") @Override public String[] apply(Object[] objects) {
         return ((Map<String, List<String>>) objects[1]).keySet().toArray(new String[0]);
      }
   }

   @RolesAllowed(Roles.ADMIN)
   @Override public List<String> getAllTeams() {
      List<Team> teams = Team.listAll();
      return teams.stream().map(t -> t.teamName + "-team").collect(toList());
   }

   @Transactional
   @RolesAllowed(Roles.ADMIN)
   @Override public void addTeam(String team) {
      Team.getEntityManager().merge(new Team(team.substring(0, team.length() - 5)));
   }

   @Transactional
   @RolesAllowed(Roles.ADMIN)
   @WithRoles
   @Override public void deleteTeam(String team) {
      String prefix = getTeamPrefix(team);
      Team teamEntity = Team.find("teamName", prefix.substring(0, prefix.length() - 1)).firstResult();
      if (teamEntity != null) {
         TeamMembership.delete("team", teamEntity);
         teamEntity.delete();
      } else {
         throw ServiceException.notFound("The team " + team + " does not exist");
      }
   }

   private String getTeamPrefix(String team) {
      if (team == null || team.isBlank()) {
         throw ServiceException.badRequest("No team name!");
      } else if (team.startsWith("horreum.")) {
         throw ServiceException.badRequest("Team name starting with 'horreum.' is illegal; this is reserved for internal use.");
      } else if (!team.endsWith("-team")) {
         throw ServiceException.badRequest("Team name must end with '-team' suffix");
      } else if (team.length() > 64) {
         throw ServiceException.badRequest("C'mon, can you think on a shorter team name?");
      }
      return team.substring(0, team.length() - 4);
   }

   @RolesAllowed(Roles.ADMIN)
   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Override public List<UserData> administrators() {
      return getAdministratorUsers().stream().map(DatabaseUserServiceImpl::toUserInfo).collect(toList());
   }

   @Transactional
   @RolesAllowed(Roles.ADMIN)
   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Override public void updateAdministrators(List<String> newAdmins) {
      if (!newAdmins.contains(identity.getPrincipal().getName())) {
         throw ServiceException.badRequest("Cannot remove yourselves from administrator list");
      }
      getAdministratorUsers().forEach(u -> {
         if (!newAdmins.contains(u.username)) {
            u.roles.remove(UserRole.ADMIN);
            u.persist();
         }
      });
      newAdmins.forEach(username -> {
         Optional<UserInfo> user = UserInfo.findByIdOptional(username);
         user.ifPresent(u -> {
            u.roles.add(UserRole.ADMIN);
            u.persist();
         });
      });
   }

   private List<UserInfo> getAdministratorUsers() {
      CriteriaBuilder cb = UserInfo.getEntityManager().getCriteriaBuilder();
      CriteriaQuery<UserInfo> query = cb.createQuery(UserInfo.class);
      query.where(cb.isMember(UserRole.ADMIN, query.from(UserInfo.class).get("roles")));
      return UserInfo.getEntityManager().createQuery(query).getResultList();
   }
}
