package io.hyperfoil.tools.horreum.svc.user;

import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import io.hyperfoil.tools.horreum.api.services.UserService;
import io.hyperfoil.tools.horreum.svc.Roles;
import io.hyperfoil.tools.horreum.svc.ServiceException;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.logging.Log;

/**
 * Implementation of {@link UserBackEnd} using an external Keycloak server.
 * Relies on keycloak-admin-client to manage user information.
 */
@ApplicationScoped
@LookupIfProperty(name = "horreum.roles.provider", stringValue = "keycloak")
public class KeycloakUserBackend implements UserBackEnd {

    private static final String[] ROLE_TYPES = new String[] { "team", Roles.VIEWER, Roles.TESTER, Roles.UPLOADER,
            Roles.MANAGER };

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm", defaultValue = "horreum")
    String realm;

    // please make sure all calls to this object are in a try/catch block to avoid leaking information
    @Inject
    Keycloak keycloak;

    private static UserService.UserData toUserInfo(UserRepresentation rep) {
        return new UserService.UserData(rep.getId(), rep.getUsername(), rep.getFirstName(), rep.getLastName(), rep.getEmail());
    }

    private static String getTeamPrefix(String team) {
        return team.substring(0, team.length() - 4);
    }

    private static boolean isTeam(String role) {
        return role.endsWith("-team"); // definition of a "team role"
    }

    @Override
    public List<UserService.UserData> searchUsers(String query) {
        try {
            return keycloak.realm(realm).users().search(query, null, null).stream().map(KeycloakUserBackend::toUserInfo)
                    .toList();
        } catch (Throwable t) {
            throw ServiceException.serverError("Unable to search for users");
        }
    }

    @Override
    public List<String> getRoles(String username) {
        List<RoleRepresentation> representations = keycloak.realm(realm).users().get(findMatchingUserId(username)).roles()
                .realmLevel().listAll();

        // the realm level roles does not include the base roles, only the composites, so add them manually
        Set<String> roles = new HashSet<>(representations.stream().map(RoleRepresentation::getName).toList());
        for (String type : ROLE_TYPES) {
            Optional<String> composite = roles.stream().filter(role -> role.endsWith(type)).findAny();
            if (composite.isPresent()) {
                roles.add(type);
                roles.add(composite.get().substring(0, composite.get().length() - type.length() - 1) + "-team");
            }
        }
        return new ArrayList<>(roles);

        // the right way to do this would be something like this (avoided because it does call keycloak a bunch of times)
        // return representations.stream().flatMap(this::getRoleAndComposites).toList();
    }

    private Stream<String> getRoleAndComposites(RoleRepresentation representation) {
        Set<String> roles = new HashSet<>();
        if (representation.isComposite()) {
            keycloak.realm(realm).rolesById().getRealmRoleComposites(representation.getId()).stream()
                    .flatMap(this::getRoleAndComposites).forEach(roles::add);
        }
        roles.add(representation.getName());
        return roles.stream();
    }

    @Override
    public List<UserService.UserData> info(List<String> usernames) {
        List<UserService.UserData> users = new ArrayList<>();
        for (String username : usernames) {
            try {
                keycloak.realm(realm).users().search(username).stream().filter(u -> username.equals(u.getUsername()))
                        .map(KeycloakUserBackend::toUserInfo).forEach(users::add);
            } catch (Throwable t) {
                Log.warnf(t, "Failed to fetch info for user '%s'", username);
                throw ServiceException.serverError("Failed to fetch info for user " + username);
            }
        }
        return users;
    }

    @Override
    public void createUser(UserService.NewUser user) {
        UserRepresentation rep = convertUserRepresentation(user); // do not blindly use the provided representation

        try (Response response = keycloak.realm(realm).users().create(rep)) {
            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                Log.warnf("Failed to create new user '%s': %s", rep.getUsername(), response.getStatusInfo());
                if (!keycloak.realm(realm).users().search(rep.getUsername(), true).isEmpty()) {
                    throw ServiceException.badRequest("User exists with same username");
                } else if (!keycloak.realm(realm).users().searchByEmail(rep.getEmail(), true).isEmpty()) {
                    throw ServiceException.badRequest("User exists with same email");
                } else {
                    throw ServiceException
                            .badRequest("Failed to create new user: " + response.getStatusInfo().getReasonPhrase());
                }
            }
        } catch (ServiceException se) {
            throw se; // thrown above, re-throw
        } catch (Throwable t) {
            throw ServiceException.serverError("Failed to create new user " + rep.getUsername());
        }

        try { // assign the provided roles to the realm
            UsersResource usersResource = keycloak.realm(realm).users();
            String userId = findMatchingUserId(rep.getUsername());

            if (user.team != null) {
                String prefix = getTeamPrefix(user.team);
                usersResource.get(userId).roles().realmLevel()
                        .add(user.roles.stream().map(r -> ensureRole(prefix + r)).toList());
            }

            // also add the "view-profile" role
            ClientsResource clientsResource = keycloak.realm(realm).clients();
            ClientRepresentation account = clientsResource.query("account").stream().filter(c -> "account".equals(c.getName()))
                    .findFirst().orElse(null);
            if (account != null) {
                RoleRepresentation viewProfile = clientsResource.get(account.getId()).roles().get("view-profile")
                        .toRepresentation();
                if (viewProfile != null) {
                    usersResource.get(userId).roles().clientLevel(account.getClientId()).add(List.of(viewProfile));
                }
            }
        } catch (ServiceException se) {
            throw se; // thrown above, re-throw
        } catch (Throwable t) {
            Log.warnf(t, "Unable to assign roles to new user '%s'", rep.getUsername());
            throw ServiceException.serverError("Unable to assign roles to new user " + rep.getUsername());
        }
    }

    @Override
    public void removeUser(String username) {
        try (Response response = keycloak.realm(realm).users().delete(findMatchingUserId(username))) {
            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                Log.warnf("Got %s response for removing user '%s'", response.getStatusInfo(), username);
                throw ServiceException.serverError("Unable to remove user " + username);
            }
        } catch (ServiceException se) {
            throw se; // thrown above, re-throw
        } catch (Throwable t) {
            Log.warnf(t, "Unable to remove user '%s'", username);
            throw ServiceException.serverError("Unable to remove user " + username);
        }
    }

    private static UserRepresentation convertUserRepresentation(UserService.NewUser user) {
        UserRepresentation rep = new UserRepresentation();
        rep.setUsername(user.user.username);
        rep.setEmail(user.user.email);
        rep.setFirstName(user.user.firstName);
        rep.setLastName(user.user.lastName);
        rep.setEnabled(true);

        if (user.password != null && !user.password.isBlank()) {
            CredentialRepresentation credentials = new CredentialRepresentation();
            credentials.setType(CredentialRepresentation.PASSWORD);
            credentials.setTemporary(true);
            credentials.setValue(user.password);
            rep.setCredentials(List.of(credentials));
        }

        return rep;
    }

    @Override
    public List<String> getTeams() { // get the "team roles" in the realm
        try {
            return keycloak.realm(realm).roles().list().stream().map(RoleRepresentation::getName)
                    .filter(KeycloakUserBackend::isTeam).toList();
        } catch (Throwable t) {
            throw ServiceException.serverError("Unable to get list of teams");
        }
    }

    private String findMatchingUserId(String username) { // find the clientID of a single user
        List<UserRepresentation> matchingUsers = keycloak.realm(realm).users().search(username, true);
        if (matchingUsers == null || matchingUsers.isEmpty()) {
            Log.warnf("Cannot find user with username '%s'", username);
            throw ServiceException.notFound("User " + username + " does not exist");
        } else if (matchingUsers.size() > 1) {
            Log.warnf("Multiple matches for exact search for username '%s': %s", username,
                    matchingUsers.stream().map(UserRepresentation::getId).collect(joining(" ")));
            throw ServiceException.serverError("More than one user with username " + username);
        }
        return matchingUsers.get(0).getId();
    }

    @Override
    public Map<String, List<String>> teamMembers(String team) { // get a list of members of a team and their "UI roles"
        String prefix = getTeamPrefix(team);
        Map<String, List<String>> userMap = new HashMap<>();
        for (String role : ROLE_TYPES) {
            try {
                // the call below does not consider transitivity with composite roles
                keycloak.realm(realm).roles().get(prefix + role).getUserMembers(0, Integer.MAX_VALUE)
                        .forEach(user -> userMap.computeIfAbsent(user.getUsername(), u -> new ArrayList<>()).add(role));
            } catch (NotFoundException e) {
                Log.warnf("Cannot find role '%s%s' in Keycloak", prefix, role); // was there a failure when creating the team?
            } catch (Throwable t) {
                Log.warnf("Error querying keycloak: %s", t.getMessage());
                throw ServiceException.serverError("Failed to retrieve role users from Keycloak");
            }
        }
        return userMap;
    }

    @Override
    public void updateTeamMembers(String team, Map<String, List<String>> roles) { // update the team membership. the roles provided here are "UI roles"
        String prefix = getTeamPrefix(team);
        for (Map.Entry<String, List<String>> entry : roles.entrySet()) {
            List<String> existingRoles;
            RoleMappingResource rolesMappingResource;

            try { // fetch the current roles for the user
                String userId = findMatchingUserId(entry.getKey());
                rolesMappingResource = keycloak.realm(realm).users().get(userId).roles();
                existingRoles = rolesMappingResource.realmLevel().listAll().stream().map(RoleRepresentation::getName).toList();
            } catch (Throwable t) {
                Log.warnf(t, "Failed to retrieve current roles of user '%s'", entry.getKey());
                throw ServiceException
                        .serverError("Failed to retrieve current roles of user " + entry.getKey());
            }

            try { // add new roles that are not in the list of current roles and then remove the existing roles that are not on the new roles
                List<RoleRepresentation> rolesToAdd = entry.getValue().stream()
                        .filter(uiRole -> !existingRoles.contains(prefix + uiRole)).map(uiRole -> ensureRole(prefix + uiRole))
                        .toList();
                if (!rolesToAdd.isEmpty()) {
                    rolesMappingResource.realmLevel().add(rolesToAdd);
                }
                List<RoleRepresentation> rolesToRemove = existingRoles.stream()
                        .filter(r -> r.startsWith(prefix) && !entry.getValue().contains(r.substring(prefix.length())))
                        .map(this::ensureRole).toList();
                if (!rolesToRemove.isEmpty()) {
                    rolesMappingResource.realmLevel().remove(rolesToRemove);
                }
            } catch (Throwable t) {
                Log.warnf(t, "Failed to modify roles of user '%s'", entry.getKey());
                throw ServiceException.serverError("Failed to modify roles of user " + entry.getKey());
            }
        }

        try { // remove all team roles to users not in the provided roles map
            UsersResource usersResource = keycloak.realm(realm).users();
            for (String type : ROLE_TYPES) {
                RoleResource roleResource = keycloak.realm(realm).roles().get(prefix + type);
                RoleRepresentation role = roleResource.toRepresentation();
                for (UserRepresentation user : roleResource.getUserMembers(0, Integer.MAX_VALUE)) {
                    if (!roles.containsKey(user.getUsername())) {
                        usersResource.get(user.getId()).roles().realmLevel().remove(List.of(role));
                    }
                }
            }
        } catch (NotFoundException e) {
            throw ServiceException.serverError("The team " + team + " does not exist");
        } catch (Throwable t) {
            Log.warnv(t, "Failed to remove all roles of team '%s'", team);
            throw ServiceException.serverError("Failed to remove all roles of team " + team);
        }
    }

    private RoleRepresentation ensureRole(String roleName) {
        try {
            return keycloak.realm(realm).roles().get(roleName).toRepresentation();
        } catch (NotFoundException e) {
            keycloak.realm(realm).roles().create(new RoleRepresentation(roleName, null, false));
            return keycloak.realm(realm).roles().get(roleName).toRepresentation();
        } catch (Throwable t) {
            throw ServiceException.serverError("Unable to fetch role " + roleName);
        }
    }

    @Override
    public List<String> getAllTeams() {
        try {
            return keycloak.realm(realm).roles().list().stream().map(RoleRepresentation::getName)
                    .filter(KeycloakUserBackend::isTeam).toList();
        } catch (Exception e) {
            throw ServiceException
                    .serverError("Please check with the System Administrators that you have the correct permissions");
        }
    }

    @Override
    public void addTeam(String team) { // create the "team roles"
        String prefix = getTeamPrefix(team); // perform validation of the team name
        createRole(team, null);
        for (String role : List.of(Roles.MANAGER, Roles.TESTER, Roles.VIEWER, Roles.UPLOADER)) {
            createRole(prefix + role, Set.of(role, team));
        }
    }

    private void createRole(String roleName, Set<String> compositeRoles) {
        RoleRepresentation role = new RoleRepresentation(roleName, null, false);
        if (compositeRoles != null) {
            role.setComposite(true);
            RoleRepresentation.Composites composites = new RoleRepresentation.Composites();
            composites.setRealm(compositeRoles);
            role.setComposites(composites);
        }
        try {
            keycloak.realm(realm).roles().create(role);
        } catch (ClientErrorException e) {
            if (e.getResponse().getStatus() == Response.Status.CONFLICT.getStatusCode()) {
                Log.warnv("Registration of role '%s' failed because it already exists", roleName);
            } else {
                throw ServiceException.serverError("Unable to create role " + roleName);
            }
        } catch (Throwable t) {
            throw ServiceException.serverError("Unable to create role " + roleName);
        }
    }

    @Override
    public void deleteTeam(String team) { // delete a team by deleting all the "team roles"
        String prefix = getTeamPrefix(team);
        for (String type : ROLE_TYPES) {
            try {
                keycloak.realm(realm).roles().deleteRole(prefix + type);
            } catch (NotFoundException e) {
                Log.warnf("Role '%s%s' was not found when deleting it", prefix, type);
                throw ServiceException.notFound("Team " + team + " not found");
            } catch (Throwable t) {
                Log.warnf(t, "Unable to delete team '%s'", team);
                throw ServiceException.serverError("Unable to delete team " + team);
            }
        }
    }

    @Override
    public List<UserService.UserData> administrators() { // get the list of all the users with administrator role
        try {
            return keycloak.realm(realm).roles().get(Roles.ADMIN).getUserMembers(0, Integer.MAX_VALUE).stream()
                    .map(KeycloakUserBackend::toUserInfo).toList();
        } catch (Throwable t) {
            Log.warnv(t, "Unable to list administrators");
            throw ServiceException
                    .serverError("Please verify with the System Administrators that you have the correct permissions");
        }
    }

    @Override
    public void updateAdministrators(List<String> newAdmins) { // update the list of administrator users
        try {
            UsersResource usersResource = keycloak.realm(realm).users();
            RoleResource adminRoleResource = keycloak.realm(realm).roles().get(Roles.ADMIN);
            RoleRepresentation adminRole = adminRoleResource.toRepresentation();

            List<UserRepresentation> oldAdmins = adminRoleResource.getUserMembers(0, Integer.MAX_VALUE);

            for (UserRepresentation user : oldAdmins) { // remove admin role from `oldAdmins` not in `newAdmins`
                if (!newAdmins.contains(user.getUsername())) {
                    try {
                        usersResource.get(user.getId()).roles().realmLevel().remove(List.of(adminRole));
                        Log.infof("Removed administrator role from user '%s'", user.getUsername());
                    } catch (Throwable t) {
                        Log.warnf("Could not remove admin role from user '%s' due to %s", user.getUsername(), t.getMessage());
                    }
                }
            }

            for (String username : newAdmins) { // add admin role for `newAdmins` not in `oldAdmins`
                if (oldAdmins.stream().noneMatch(old -> username.equals(old.getUsername()))) {
                    try {
                        usersResource.get(findMatchingUserId(username)).roles().realmLevel().add(List.of(adminRole));
                        Log.infof("Added administrator role to user '%s'", username);
                    } catch (Throwable t) {
                        Log.warnf("Could not add admin role to user '%s' due to %s", username, t.getMessage());
                    }
                }
            }
        } catch (ServiceException se) {
            throw se; // thrown above, re-throw
        } catch (Throwable t) {
            Log.warn("Cannot fetch representation for admin role", t);
            throw ServiceException.serverError("Cannot find admin role");
        }
    }

    @Override
    public void setPassword(String username, String password) {
        try {
            CredentialRepresentation credentials = new CredentialRepresentation();
            credentials.setType(CredentialRepresentation.PASSWORD);
            credentials.setValue(password);

            keycloak.realm(realm).users().get(findMatchingUserId(username)).resetPassword(credentials);
        } catch (Throwable t) {
            Log.warnf(t, "Failed to retrieve current representation of user '%s'", username);
            throw ServiceException
                    .serverError("Failed to retrieve current representation of user " + username);
        }
    }
}
