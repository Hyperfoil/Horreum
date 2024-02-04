package io.hyperfoil.tools.horreum.svc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.exception.ConstraintViolationException;
import org.jboss.logging.Logger;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import io.hyperfoil.tools.horreum.api.internal.services.UserService;
import io.hyperfoil.tools.horreum.entity.UserInfo;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.Vertx;

@PermitAll
@ApplicationScoped
public class UserServiceImpl implements UserService {
   private static final Logger log = Logger.getLogger(UserServiceImpl.class);
   private static final String[] ROLE_TYPES = new String[] { "team", Roles.VIEWER, Roles.TESTER, Roles.UPLOADER, Roles.MANAGER };

   @ConfigProperty(name="horreum.keycloak.realm", defaultValue="horreum")
   String realm;

   @Inject
   Keycloak keycloak;

   @Inject
   SecurityIdentity identity;

   @Inject
   Vertx vertx;

   private static UserData toUserInfo(UserRepresentation rep) {
      return new UserData(rep.getId(), rep.getUsername(), rep.getFirstName(), rep.getLastName(), rep.getEmail());
   }

   @Override
   public List<UserData> searchUsers(String query) {
      if (identity.isAnonymous()) {
         throw ServiceException.forbidden("Please log in and try again");
      }
      return keycloak.realm(realm).users().search(query, null, null).stream()
                     .map(UserServiceImpl::toUserInfo).collect(Collectors.toList());
   }

   @RolesAllowed({Roles.VIEWER, Roles.TESTER, Roles.ADMIN})
   @Override
   public List<UserData> info(List<String> usernames) {
      if (identity.isAnonymous()) {
         throw ServiceException.forbidden("Please log in and try again");
      }
      List<UserData> users = new ArrayList<>();
      for (String username: usernames) {
            try {
               List<UserRepresentation> res = keycloak.realm(realm).users().search(username);
               for (var u : res) {
                  if (username.equals(u.getUsername())) {
                     users.add(toUserInfo(u));
                  }
               }
            } catch (Exception e) {
               log.errorf(e, "Failed to fetch info for user %s", username);
               throw ServiceException.serverError(String.format("Failed to fetch info for user %s", username));
            }

      }
      return users;
   }

   @Override
   @RolesAllowed({Roles.MANAGER, Roles.ADMIN})
   public void createUser(NewUser user) {
      if (user == null) {
         throw ServiceException.badRequest("Missing user as the request body");
      } else if (user.team != null && !user.team.endsWith("-team")) {
         throw ServiceException.badRequest("Team must end with -team: " + user.team);
      }
      String prefix = user.team == null ? null : user.team.substring(0, user.team.length() - 4);
      if (prefix != null && !identity.getRoles().contains(prefix + Roles.MANAGER) && !identity.getRoles().contains(Roles.ADMIN)) {
         throw ServiceException.forbidden("This user is not a manager for team " + user.team);
      }
      // do not blindly use the existing representation
      UserRepresentation rep = new UserRepresentation();
      rep.setUsername(user.user.username);
      rep.setEmail(user.user.email);
      rep.setFirstName(user.user.firstName);
      rep.setLastName(user.user.lastName);
      rep.setEnabled(true);

      CredentialRepresentation credentials = new CredentialRepresentation();
      credentials.setType(CredentialRepresentation.PASSWORD);
      credentials.setTemporary(true);
      credentials.setValue(user.password);
      rep.setCredentials(Collections.singletonList(credentials));

      Response response = keycloak.realm(realm).users().create(rep);
      if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
         log.errorf("Failed to create new user %s: %s", rep, response);
         if (!keycloak.realm(realm).users().search(rep.getUsername(), true).isEmpty()) {
            throw ServiceException.badRequest("User exists with same username.");
         } else if (!keycloak.realm(realm).users().searchByEmail(rep.getEmail(), true).isEmpty()) {
            throw ServiceException.badRequest("User exists with same email.");
         } else {
            throw ServiceException.badRequest("Failed to create new user: " + response.getStatusInfo().getReasonPhrase());
         }
      }
      List<UserRepresentation> matchingUsers = keycloak.realm(realm).users().search(rep.getUsername(), true);
      if (matchingUsers == null || matchingUsers.isEmpty()) {
         throw ServiceException.badRequest("User " + rep.getUsername() + " does not exist.");
      } else if (matchingUsers.size() > 1) {
         throw ServiceException.serverError("More than one user with username " + rep.getUsername());
      }
      String userId = matchingUsers.get(0).getId();

      if (prefix != null) {
         List<RoleRepresentation> addedRoles = new ArrayList<>();
         for (String role : user.roles) {
            addedRoles.add(ensureRole(prefix + role));
         }
         keycloak.realm(realm).users().get(userId).roles().realmLevel().add(addedRoles);
      }

      ClientRepresentation account = keycloak.realm(realm).clients().query("account").stream()
                                             .filter(c -> "account".equals(c.getName())).findFirst().orElse(null);
      if (account != null) {
         RoleRepresentation viewProfile = keycloak.realm(realm).clients().get(account.getId()).roles().get("view-profile").toRepresentation();
         if (viewProfile != null) {
            keycloak.realm(realm).users().get(userId).roles().clientLevel(account.getClientId()).add(Collections.singletonList(viewProfile));
         }
      }
   }

   @Override
   public List<String> getTeams() {
      if (identity.isAnonymous()) {
         throw ServiceException.forbidden("Please log in and try again");
      }
      return keycloak.realm(realm).roles().list().stream().map(RoleRepresentation::getName)
                     .filter(n -> n.endsWith("-team")).collect(Collectors.toList());
   }

   @WithRoles(addUsername = true)
   @Transactional
   public void cacheUserTeams(String username, Set<String> teams) {
      try {
         // Running this without pessimistic lock leads to duplicate inserts at the same time
         UserInfo userInfo = UserInfo.findById(username, LockModeType.PESSIMISTIC_WRITE);
         if (userInfo == null) {
            userInfo = new UserInfo();
            userInfo.username = username;
            userInfo.teams = teams;
         } else if (!teams.equals(userInfo.teams)) {
            userInfo.teams = teams;
         }
         userInfo.persistAndFlush();
      } catch (PersistenceException e) {
         if (e instanceof ConstraintViolationException) {
            // silently ignore
            // note: alternative would be to define @SQLInsert with INSERT ... ON CONFLICT DO NOTHING
            log.tracef(e, "Concurrent insertion of %s", username);
         } else {
            throw e;
         }
      }
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

   private String findMatchingUserId(String username) {
      List<UserRepresentation> matchingUsers = keycloak.realm(realm).users().search(username, true);
      if (matchingUsers == null || matchingUsers.isEmpty()) {
         log.errorf("Cannot find user with username %s", username);
         throw ServiceException.badRequest("User " + username + " does not exist.");
      } else if (matchingUsers.size() > 1) {
         log.errorf("Multiple matches for exact search for username %s: %s", username, matchingUsers);
         throw ServiceException.serverError("More than one user with username " + username);
      }
      return matchingUsers.get(0).getId();
   }

   @Override
   public Map<String, List<String>> teamMembers(String team) {
      String prefix = getTeamPrefix(team);
      if (!identity.getRoles().contains(prefix + Roles.MANAGER) && !identity.getRoles().contains(Roles.ADMIN)) {
         throw ServiceException.badRequest("This user is not a manager for team " + team);
      }
      Map<String, List<String>> userMap = new HashMap<>();
      for (String role : ROLE_TYPES) {
         try {
            // The call below does not consider transitivity with composite roles
            Set<UserRepresentation> users = keycloak.realm(realm).roles().get(prefix + role).getRoleUserMembers();
            for (UserRepresentation user : users) {
               List<String> userRoles = userMap.computeIfAbsent(user.getUsername(), u -> new ArrayList<>());
               userRoles.add(role);
            }
         } catch (NotFoundException e) {
            // Was there a failure when creating the team?
            log.warnf("Cannot find role %s%s in Keycloak", prefix, role);
         } catch (Exception e) {
            log.warnf("Error querying keycloak: %s", e.getMessage());
            ServiceException.serverError("Failed to retrieve role users from Keycloak.");
         }
      }
      return userMap;
   }

   @Override
   public void updateTeamMembers(String team, Map<String, List<String>> roles) {
      String prefix = getTeamPrefix(team);
      if (!identity.getRoles().contains(prefix + Roles.MANAGER) && !identity.getRoles().contains(Roles.ADMIN)) {
         throw ServiceException.forbidden("This user is does not have the manager role for team " + team);
      }
      CountDownFuture<Void> future = new CountDownFuture<>(null, roles.size() + ROLE_TYPES.length);
      ConcurrentMap<String, RoleRepresentation> roleMap = new ConcurrentHashMap<>();
      for (var entry : roles.entrySet()) {
         vertx.executeBlocking(promise -> { //leave call to vertx.executeBlocking as this will make calls to keycloack in parrallel
            String userId = findMatchingUserId(entry.getKey());
            RoleMappingResource rolesMappingResource = keycloak.realm(realm).users().get(userId).roles();
            List<RoleRepresentation> userRoles = rolesMappingResource.getAll().getRealmMappings();
            if (userRoles == null) {
               userRoles = Collections.emptyList();
            }
            List<RoleRepresentation> removed = null;
            Set<String> existingTeamRoles = new HashSet<>();
            for (RoleRepresentation role : userRoles) {
               if (!role.getName().startsWith(prefix)) {
                  // other team
                  continue;
               }
               String roleWithoutPrefix = role.getName().substring(prefix.length());
               if (entry.getValue().contains(roleWithoutPrefix)) {
                  // already has this role
                  existingTeamRoles.add(roleWithoutPrefix);
                  continue;
               }
               if (removed == null) {
                  removed = new ArrayList<>();
               }
               removed.add(role);
            }
            if (removed != null) {
               rolesMappingResource.realmLevel().remove(removed);
            }
            List<RoleRepresentation> added = null;
            for (String role : entry.getValue()) {
               if (!existingTeamRoles.contains(role)) {
                  if (added == null) {
                     added = new ArrayList<>();
                  }
                  RoleRepresentation rep = roleMap.computeIfAbsent(role, r -> ensureRole(prefix + role));
                  if (rep != null) {
                     added.add(rep);
                  } else {
                     log.errorf("Role %s is not present!", prefix + role);
                     promise.fail(ServiceException.serverError("Cannot add role " + prefix + role + " to user " + entry.getKey()));
                     return;
                  }
               }
            }
            if (added != null) {
               rolesMappingResource.realmLevel().add(added);
            }
            promise.complete();
         }).onSuccess(future).onFailure(t -> {
            log.errorf(t, "Cannot update roles for user %s", entry.getKey());
            future.completeExceptionally(ServiceException.serverError("Cannot update roles for user " + entry.getKey()));
         });
      }
      for (String type : ROLE_TYPES) {
         vertx.executeBlocking(promise -> {
            String roleName = prefix + type;
            RoleResource roleResource = keycloak.realm(realm).roles().get(roleName);
            RoleRepresentation role = roleResource.toRepresentation();
            for (var user : roleResource.getRoleUserMembers()) {
               if (!roles.containsKey(user.getUsername())) {
                  keycloak.realm(realm).users().get(user.getId()).roles().realmLevel().remove(
                     Collections.singletonList(role));
               }
            }
            promise.complete();
         }).onSuccess(future).onFailure(t -> future.completeExceptionally(ServiceException.serverError("Cannot remove user roles")));
      }
      try {
         future.join();
      } catch (Exception e){
         throw new WebApplicationException(e);
      }
   }

   private RoleRepresentation ensureRole(String roleName) {
      try {
         return keycloak.realm(realm).roles().get(roleName).toRepresentation();
      } catch (NotFoundException e) {
         keycloak.realm(realm).roles().create(new RoleRepresentation(roleName, null, false));
         return keycloak.realm(realm).roles().get(roleName).toRepresentation();
      }
   }

   @RolesAllowed(Roles.ADMIN)
   @Override
   public List<String> getAllTeams() {
      List<String> teams;
      try {
         teams = keycloak.realm(realm).roles().list().stream()
                         .map(RoleRepresentation::getName).filter(role -> role.endsWith("-team")).collect(Collectors.toList());
      } catch (Exception e) {
         throw ServiceException.serverError("Please check with the System Administrators that you have the correct permissions.");
      }
      return teams;
   }

   @RolesAllowed(Roles.ADMIN)
   @Override
   public void addTeam(String team) {
      String prefix = getTeamPrefix(team);
         createRole(team, null);
         for (String type : Arrays.asList(Roles.MANAGER, Roles.TESTER, Roles.VIEWER, Roles.UPLOADER)) {
            createRole(prefix + type, Set.of(type, team));
         }
   }

   private void createRole(String roleName, Set<String> compositeRoles) {
      RoleRepresentation role = new RoleRepresentation(roleName, null, false);
      if (compositeRoles != null) {
         role.setComposite(true);
         var composites = new RoleRepresentation.Composites();
         composites.setRealm(compositeRoles);
         role.setComposites(composites);
      }
      try {
         keycloak.realm(realm).roles().create(role);
      } catch (ClientErrorException e) {
         if (e.getResponse().getStatus() == Response.Status.CONFLICT.getStatusCode()) {
            log.warnf("Role %s already exists, registration failed", roleName);
         }
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
   @Override
   public void deleteTeam(String team) {
      String prefix = getTeamPrefix(team);
      for (String type : ROLE_TYPES) {
         try {
            keycloak.realm(realm).roles().deleteRole(prefix + type);
         } catch (NotFoundException e) {
            log.warnf("Role %s%s was not found when we tried to delete it", prefix, type);
         } catch (Exception e) {
            throw ServiceException.serverError(String.format("unable to delete team: %s", team));
         }
      }
   }

   @RolesAllowed(Roles.ADMIN)
   @Override
   public List<UserData> administrators() {
         List<UserData> admins = new ArrayList<>();
         try {
            for (var user : keycloak.realm(realm).roles().get(Roles.ADMIN).getRoleUserMembers()) {
               admins.add(new UserData(user.getId(), user.getUsername(), user.getFirstName(), user.getLastName(), user.getEmail()));
            }
            return admins;
         } catch (Exception e){
            throw ServiceException.serverError("Please verify with the System Administrators that you have the correct permissions");
         }
   }

   @RolesAllowed(Roles.ADMIN)
   @Override
   public void updateAdministrators(List<String> newAdmins) {
      if (!newAdmins.contains(identity.getPrincipal().getName())) {
         throw ServiceException.badRequest("Cannot remove yourselves from administrator list");
      }
      RoleResource roleResource = keycloak.realm(realm).roles().get(Roles.ADMIN);

      CountDownFuture<Void> future = new CountDownFuture<>(null, 1 + newAdmins.size());
      vertx.<RoleRepresentation>executeBlocking(promise ->
         promise.complete(roleResource.toRepresentation())
      ).onSuccess(adminRole -> {
         for (String username : newAdmins) {
            vertx.executeBlocking(promise -> {
               String userId = findMatchingUserId(username);
               RoleScopeResource userRoles = keycloak.realm(realm).users().get(userId).roles().realmLevel();
               for (var role : userRoles.listAll()) {
                  if (Roles.ADMIN.equals(role.getName())) {
                     promise.complete();
                     return;
                  }
               }
               userRoles.add(Collections.singletonList(adminRole));
               promise.complete();
            }).onSuccess(future).onFailure(t -> {
               log.errorf(t, "Cannot add admin role to user %s", username);
               future.completeExceptionally(ServiceException.serverError("Cannot add admin role to user " + username));
            });
         }
         vertx.executeBlocking(promise -> {
            Set<UserRepresentation> oldAdmins = roleResource.getRoleUserMembers();
            for (UserRepresentation user : oldAdmins) {
               if (!newAdmins.contains(user.getUsername())) {
                  keycloak.realm(realm).users().get(user.getId()).roles().realmLevel().remove(Collections.singletonList(adminRole));
               }
            }
            promise.complete();
         }).onSuccess(future).onFailure(t -> {
            log.error("Cannot remove admin role", t);
            future.completeExceptionally(ServiceException.serverError("Cannot remove admin role"));
         });
      }).onFailure(t -> {
         log.error("Cannot fetch representation for admin role", t);
         future.completeExceptionally(ServiceException.serverError("Cannot find admin role"));
      });
      try {
         future.join();
      } catch (Exception e){
         throw ServiceException.serverError(e.getMessage());
      }
   }
}
