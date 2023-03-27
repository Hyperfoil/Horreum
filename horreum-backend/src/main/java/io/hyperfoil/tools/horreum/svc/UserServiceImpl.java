package io.hyperfoil.tools.horreum.svc;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;
import javax.transaction.Transactional;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.config.ConfigProvider;
import org.hibernate.exception.ConstraintViolationException;
import org.jboss.logging.Logger;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import io.hyperfoil.tools.horreum.api.services.UserService;
import io.hyperfoil.tools.horreum.entity.UserInfo;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import io.vertx.core.Vertx;

@PermitAll
@ApplicationScoped
public class UserServiceImpl implements UserService {
   private static final Logger log = Logger.getLogger(UserServiceImpl.class);
   private static final String[] ROLE_TYPES = new String[] { "team", Roles.VIEWER, Roles.TESTER, Roles.UPLOADER, Roles.MANAGER };
   private static final String REALM = "horreum";

   Keycloak keycloak;

   @Inject
   SecurityIdentity identity;

   @Inject
   Vertx vertx;

   private static UserData toUserInfo(UserRepresentation rep) {
      return new UserData(rep.getId(), rep.getUsername(), rep.getFirstName(), rep.getLastName(), rep.getEmail());
   }


   @PostConstruct
   public void init() throws MalformedURLException {
      // horreum.keycloak.url is the URL advertised to clients; we need the url on internal network
      String serverUrl = ConfigProvider.getConfig().getOptionalValue("horreum.keycloak.internal.url", String.class).orElse(null);
      if (serverUrl == null) {
         URL url = new URL(ConfigProvider.getConfig().getValue("quarkus.oidc.auth-server-url", String.class));
         serverUrl = url.getProtocol() + "://" + url.getAuthority();
      }
      keycloak = KeycloakBuilder.builder()
            .serverUrl(serverUrl)
            .realm(REALM)
            .clientId("horreum")
            .clientSecret(ConfigProvider.getConfig().getValue("quarkus.oidc.credentials.secret", String.class))
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .build();

   }

   @Override
   @Blocking
   public List<UserData> searchUsers(String query) {
      if (identity.isAnonymous()) {
         throw new WebApplicationException(Response.Status.FORBIDDEN);
      }
      return keycloak.realm(REALM).users().search(query, null, null).stream()
            .map(UserServiceImpl::toUserInfo).collect(Collectors.toList());
   }

   @RolesAllowed({Roles.VIEWER, Roles.TESTER, Roles.ADMIN})
   @Blocking // TODO: identity.isAnonymous() is a blocking call
   @Override
   public CompletionStage<List<UserData>> info(List<String> usernames) {
      if (identity.isAnonymous()) {
         throw new WebApplicationException(Response.Status.FORBIDDEN);
      }
      List<UserData> users = new ArrayList<>();
      CountDownFuture<List<UserData>> future = new CountDownFuture<>(users, usernames.size());
      for (String username: usernames) {
         vertx.executeBlocking(promise -> {
            List<UserRepresentation> res = keycloak.realm(REALM).users().search(username);
            synchronized (users) {
               for (var u : res) {
                  if (username.equals(u.getUsername())) {
                     users.add(toUserInfo(u));
                  }
               }
            }
            promise.complete();
         }).onSuccess(future).onFailure(t -> {
            log.errorf(t, "Failed to fetch info for user %s", username);
            future.completeExceptionally(new WebApplicationException());
         });
      }
      return future;

   }

   @Override
   @Blocking
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

      Response response = keycloak.realm(REALM).users().create(rep);
      if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
         log.errorf("Failed to create new user %s: %s", rep, response);
         throw ServiceException.badRequest("Failed to create new user.");
      }
      List<UserRepresentation> matchingUsers = keycloak.realm(REALM).users().search(rep.getUsername(), true);
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
         keycloak.realm(REALM).users().get(userId).roles().realmLevel().add(addedRoles);
      }

      ClientRepresentation account = keycloak.realm(REALM).clients().query("account").stream()
            .filter(c -> "account".equals(c.getName())).findFirst().orElse(null);
      if (account != null) {
         RoleRepresentation viewProfile = keycloak.realm(REALM).clients().get(account.getId()).roles().get("view-profile").toRepresentation();
         if (viewProfile != null) {
            keycloak.realm(REALM).users().get(userId).roles().clientLevel(account.getClientId()).add(Collections.singletonList(viewProfile));
         }
      }
   }

   @Override
   @Blocking
   public List<String> getTeams() {
      if (identity.isAnonymous()) {
         throw new WebApplicationException(Response.Status.FORBIDDEN);
      }
      return keycloak.realm(REALM).roles().list().stream().map(RoleRepresentation::getName)
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
         if (e.getCause() instanceof ConstraintViolationException) {
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
      List<UserRepresentation> matchingUsers = keycloak.realm(REALM).users().search(username, true);
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
   @Blocking //TODO: identity.getRoles() could block
   public CompletionStage<Map<String, List<String>>> teamMembers(String team) {
      String prefix = getTeamPrefix(team);
      if (!identity.getRoles().contains(prefix + Roles.MANAGER) && !identity.getRoles().contains(Roles.ADMIN)) {
         throw ServiceException.badRequest("This user is not a manager for team " + team);
      }
      Map<String, List<String>> userMap = new HashMap<>();
      CountDownFuture<Map<String, List<String>>> future = new CountDownFuture<>(userMap, ROLE_TYPES.length);
      for (String role : ROLE_TYPES) {
         vertx.executeBlocking(promise -> {
            try {
               // The call below does not consider transitivity with composite roles
               Set<UserRepresentation> users = keycloak.realm(REALM).roles().get(prefix + role).getRoleUserMembers();
               synchronized (userMap) {
                  for (UserRepresentation user : users) {
                     List<String> userRoles = userMap.computeIfAbsent(user.getUsername(), u -> new ArrayList<>());
                     userRoles.add(role);
                  }
               }
            } catch (NotFoundException e) {
               // Was there a failure when creating the team?
               log.warnf("Cannot find role %s%s in Keycloak", prefix, role);
            }
            promise.complete();
         }).onSuccess(future).onFailure(t -> {
            log.errorf(t, "Failed to retrieve users for role %s", prefix + role);
            future.completeExceptionally(ServiceException.serverError("Failed to retrieve role users from Keycloak."));
         });
      }
      return future;
   }

   @Override
   @Blocking //TODO: identity.getRoles() could block
   public CompletionStage<Void> updateTeamMembers(String team, Map<String, List<String>> roles) {
      String prefix = getTeamPrefix(team);
      if (!identity.getRoles().contains(prefix + Roles.MANAGER) && !identity.getRoles().contains(Roles.ADMIN)) {
         throw ServiceException.forbidden("This user is does not have the manager role for team " + team);
      }
      CountDownFuture<Void> future = new CountDownFuture<>(null, roles.size() + ROLE_TYPES.length);
      ConcurrentMap<String, RoleRepresentation> roleMap = new ConcurrentHashMap<>();
      for (var entry : roles.entrySet()) {
         vertx.executeBlocking(promise -> {
            String userId = findMatchingUserId(entry.getKey());
            RoleMappingResource rolesMappingResource = keycloak.realm(REALM).users().get(userId).roles();
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
            RoleResource roleResource = keycloak.realm(REALM).roles().get(roleName);
            RoleRepresentation role = roleResource.toRepresentation();
            for (var user : roleResource.getRoleUserMembers()) {
               if (!roles.containsKey(user.getUsername())) {
                  keycloak.realm(REALM).users().get(user.getId()).roles().realmLevel().remove(
                     Collections.singletonList(role));
               }
            }
            promise.complete();
         }).onSuccess(future).onFailure(t -> future.completeExceptionally(ServiceException.serverError("Cannot remove user roles")));
      }
      return future;
   }

   private RoleRepresentation ensureRole(String roleName) {
      try {
         return keycloak.realm(REALM).roles().get(roleName).toRepresentation();
      } catch (NotFoundException e) {
         keycloak.realm(REALM).roles().create(new RoleRepresentation(roleName, null, false));
         return keycloak.realm(REALM).roles().get(roleName).toRepresentation();
      }
   }

   private <T> T wrapException(Throwable t) {
      throw new WebApplicationException(t);
   }

   @RolesAllowed(Roles.ADMIN)
   @Override
   public CompletionStage<List<String>> getAllTeams() {
      return vertx.<List<String>>executeBlocking(promise ->
            promise.complete(keycloak.realm(REALM).roles().list().stream()
               .map(RoleRepresentation::getName).filter(role -> role.endsWith("-team")).collect(Collectors.toList()))).toCompletionStage().exceptionally(this::wrapException);
   }

   @RolesAllowed(Roles.ADMIN)
   @Override
   public CompletionStage<Void> addTeam(String team) {
      String prefix = getTeamPrefix(team);
      return vertx.<Void>executeBlocking(promise -> {
            createRole(team, null);
            createRole(prefix + Roles.MANAGER, Set.of(team));
            for (String type : Arrays.asList(Roles.TESTER, Roles.VIEWER, Roles.UPLOADER)) {
               createRole(prefix + type, Set.of(type, team));
            }
            promise.complete();
      }).toCompletionStage().exceptionally(this::wrapException);
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
         keycloak.realm(REALM).roles().create(role);
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
   public CompletionStage<Void> deleteTeam(String team) {
      String prefix = getTeamPrefix(team);
      return vertx.<Void>executeBlocking(promise -> {
         for (String type : ROLE_TYPES) {
            try {
               keycloak.realm(REALM).roles().deleteRole(prefix + type);
            } catch (NotFoundException e) {
               log.warnf("Role %s%s was not found when we tried to delete it", prefix, type);
            }
         }
         promise.complete();
      }).toCompletionStage().exceptionally(this::wrapException);
   }

   @RolesAllowed(Roles.ADMIN)
   @Override
   public CompletionStage<List<UserData>> administrators() {
      return vertx.<List<UserData>>executeBlocking(promise -> {
         List<UserData> admins = new ArrayList<>();
         for (var user : keycloak.realm(REALM).roles().get(Roles.ADMIN).getRoleUserMembers()) {
            admins.add(new UserData(user.getId(), user.getUsername(), user.getFirstName(), user.getLastName(), user.getEmail()));
         }
         promise.complete(admins);
      }).toCompletionStage().exceptionally(this::wrapException);
   }

   @RolesAllowed(Roles.ADMIN)
   @Override
   public CompletionStage<Void> updateAdministrators(List<String> newAdmins) {
      if (!newAdmins.contains(identity.getPrincipal().getName())) {
         throw ServiceException.badRequest("Cannot remove yourselves from administrator list");
      }
      RoleResource roleResource = keycloak.realm(REALM).roles().get(Roles.ADMIN);

      CountDownFuture<Void> future = new CountDownFuture<>(null, 1 + newAdmins.size());
      vertx.<RoleRepresentation>executeBlocking(promise ->
         promise.complete(roleResource.toRepresentation())
      ).onSuccess(adminRole -> {
         for (String username : newAdmins) {
            vertx.executeBlocking(promise -> {
               String userId = findMatchingUserId(username);
               RoleScopeResource userRoles = keycloak.realm(REALM).users().get(userId).roles().realmLevel();
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
                  keycloak.realm(REALM).users().get(user.getId()).roles().realmLevel().remove(Collections.singletonList(adminRole));
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
      return future;
   }
}
