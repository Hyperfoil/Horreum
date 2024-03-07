package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.api.internal.services.UserService;
import io.hyperfoil.tools.horreum.entity.user.UserInfo;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.hyperfoil.tools.horreum.svc.user.UserBackEnd;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;
import java.util.Map;

@PermitAll
@ApplicationScoped
public class UserServiceImpl implements UserService {
   private static final Logger log = Logger.getLogger(UserServiceImpl.class);
   @Inject
   SecurityIdentity identity;

   @Inject
   Instance<UserBackEnd> backend;

   @Authenticated
   @Override public List<String> getRoles() {
      return identity.getRoles().stream().toList();
   }

   @Override
   public List<UserData> searchUsers(String query) {

      if (identity.isAnonymous()) {
         throw ServiceException.forbidden("Please log in and try again");
      }
      return backend.get().searchUsers(query);
   }

   @RolesAllowed({Roles.VIEWER, Roles.TESTER, Roles.ADMIN})
   @Override
   public List<UserData> info(List<String> usernames) {
      if (identity.isAnonymous()) {
         throw ServiceException.forbidden("Please log in and try again");
      }
      return backend.get().info(usernames);
   }

   @Override
   @RolesAllowed({Roles.MANAGER, Roles.ADMIN})
   public void createUser(NewUser user) {
      if (user == null) {
         throw ServiceException.badRequest("Missing user as the request body");
      } else if (user.team != null && !user.team.endsWith("-team")) {
         throw ServiceException.badRequest("Team must end with -team: " + user.team);
      }

      backend.get().createUser(user);
   }

   @Override
   public List<String> getTeams() {
      if (identity.isAnonymous()) {
         throw ServiceException.forbidden("Please log in and try again");
      }
      return backend.get().getTeams();
   }

   @WithRoles(addUsername = true)
   @Override
   public String defaultTeam() {
      UserInfo userInfo = UserInfo.findById(identity.getPrincipal().getName());
      return userInfo != null ? userInfo.defaultTeam : null;
   }

   @WithRoles(addUsername = true)
   @Override
   @Transactional
   public void setDefaultTeam(String team) {
      UserInfo userInfo = UserInfo.findById(identity.getPrincipal().getName());
      userInfo.defaultTeam = Util.destringify(team);
      userInfo.persistAndFlush();
   }

   @Override
   public Map<String, List<String>> teamMembers(String team) {
      return backend.get().teamMembers(team);
   }

   @Override
   public void updateTeamMembers(String team, Map<String, List<String>> roles) {
      backend.get().updateTeamMembers(team, roles);
   }

   @RolesAllowed(Roles.ADMIN)
   @Override
   public List<String> getAllTeams() {
      return backend.get().getAllTeams();
   }

   @RolesAllowed(Roles.ADMIN)
   @Override
   public void addTeam(String team) {
      backend.get().addTeam(team);
   }


   public static  String getTeamPrefix(String team) {
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
   @Override
   public void deleteTeam(String team) {
      backend.get().deleteTeam(team);
   }

   @RolesAllowed(Roles.ADMIN)
   @Override
   public List<UserData> administrators() {
      return backend.get().administrators();
   }

   @RolesAllowed(Roles.ADMIN)
   @Override
   public void updateAdministrators(List<String> newAdmins) {
      backend.get().updateAdministrators(newAdmins);
   }
}
