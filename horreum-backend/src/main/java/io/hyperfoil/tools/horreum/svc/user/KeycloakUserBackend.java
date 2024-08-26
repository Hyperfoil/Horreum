package io.hyperfoil.tools.horreum.svc.user;

import static java.text.MessageFormat.format;
import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import io.hyperfoil.tools.horreum.api.internal.services.UserService;
import io.hyperfoil.tools.horreum.svc.Roles;
import io.hyperfoil.tools.horreum.svc.ServiceException;
import io.quarkus.arc.lookup.LookupIfProperty;

/**
 * Implementation of {@link UserBackEnd} using an external Keycloak server.
 * Relies on keycloak-admin-client to manage user information.
 */
@ApplicationScoped
@LookupIfProperty(name = "horreum.roles.provider", stringValue = "keycloak")
public class KeycloakUserBackend implements UserBackEnd {

    private static final Logger LOG = Logger.getLogger(KeycloakUserBackend.class);

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
            Set<String> machineIds = safeMachineIds();
            return keycloak.realm(realm).users().search(query, null, null).stream()
                    .filter(rep -> !machineIds.contains(rep.getId())).map(KeycloakUserBackend::toUserInfo).toList();
        } catch (Throwable t) {
            throw ServiceException.serverError("Unable to search for users");
        }
    }

    private Set<String> safeMachineIds() {
        try {
            return keycloak.realm(realm).roles().get(Roles.MACHINE).getUserMembers(0, Integer.MAX_VALUE).stream()
                    .map(UserRepresentation::getId).collect(Collectors.toSet());
        } catch (Exception e) {
            // ignore exception
            return Set.of();
        }
    }

    @Override
    public List<String> getRoles(String username) {
        return keycloak.realm(realm).users().get(findMatchingUser(username).getId()).roles().realmLevel().listAll().stream()
                .map(RoleRepresentation::getName).toList();
    }

    @Override
    public List<UserService.UserData> info(List<String> usernames) {
        List<UserService.UserData> users = new ArrayList<>();
        for (String username : usernames) {
            try {
                keycloak.realm(realm).users().search(username).stream().filter(u -> username.equals(u.getUsername()))
                        .map(KeycloakUserBackend::toUserInfo).forEach(users::add);
            } catch (Throwable t) {
                LOG.warnv(t, "Failed to fetch info for user {0}", username);
                throw ServiceException.serverError(format("Failed to fetch info for user {0}", username));
            }
        }
        return users;
    }

    @Override
    public void createUser(UserService.NewUser user) {
        UserRepresentation rep = convertUserRepresentation(user); // do not blindly use the provided representation

        try (Response response = keycloak.realm(realm).users().create(rep)) {
            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                LOG.warnv("Failed to create new user {0}: {1}", rep.getUsername(), response.getStatusInfo());
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
            throw ServiceException.serverError(format("Failed to create new user {0}", rep.getUsername()));
        }

        try { // assign the provided roles to the realm
            UsersResource usersResource = keycloak.realm(realm).users();
            String userId = findMatchingUser(rep.getUsername()).getId();

            if (user.team != null) {
                String prefix = getTeamPrefix(user.team);
                usersResource.get(userId).roles().realmLevel()
                        .add(user.roles.stream().map(r -> ensureRole(prefix + r)).toList());
                if (user.roles.contains(Roles.MACHINE)) {
                    // add the base 'machine' role as well to be able to get all machine accounts
                    // keycloak does not return the users of role inherited by composition
                    usersResource.get(userId).roles().realmLevel().add(List.of(ensureRole(Roles.MACHINE)));

                    // reset the password, so that it does not have to be changed
                    setPassword(user.user.username, user.password);
                }
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
            LOG.warnv(t, "Unable to assign roles to new user {0}", rep.getUsername());
            throw ServiceException.serverError(format("Unable to assign roles to new user {0}", rep.getUsername()));
        }
    }

    @Override
    public void removeUser(String username) {
        try (Response response = keycloak.realm(realm).users().delete(findMatchingUser(username).getId())) {
            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                LOG.warnv("Got {0} response for removing user {0}", response.getStatusInfo(), username);
                throw ServiceException.serverError(format("Unable to remove user {0}", username));
            }
        } catch (ServiceException se) {
            throw se; // thrown above, re-throw
        } catch (Throwable t) {
            LOG.warnv(t, "Unable to remove user {0}", username);
            throw ServiceException.serverError(format("Unable to remove user {0}", username));
        }
    }

    private static UserRepresentation convertUserRepresentation(UserService.NewUser user) {
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
        rep.setCredentials(List.of(credentials));
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

    private UserRepresentation findMatchingUser(String username) { // find the clientID of a single user
        List<UserRepresentation> matchingUsers = keycloak.realm(realm).users().search(username, true);
        if (matchingUsers == null || matchingUsers.isEmpty()) {
            LOG.warnv("Cannot find user with username {0}", username);
            throw ServiceException.notFound(format("User {0} does not exist", username));
        } else if (matchingUsers.size() > 1) {
            LOG.warnv("Multiple matches for exact search for username {0}: {1}", username,
                    matchingUsers.stream().map(UserRepresentation::getId).collect(joining(" ")));
            throw ServiceException.serverError(format("More than one user with username {0}", username));
        }
        return matchingUsers.get(0);
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
                LOG.warnv("Cannot find role {0}{1} in Keycloak", prefix, role); // was there a failure when creating the team?
            } catch (Throwable t) {
                LOG.warnv("Error querying keycloak: {0}", t.getMessage());
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
                String userId = findMatchingUser(entry.getKey()).getId();
                rolesMappingResource = keycloak.realm(realm).users().get(userId).roles();
                existingRoles = rolesMappingResource.getAll().getRealmMappings().stream().map(RoleRepresentation::getName)
                        .toList();
            } catch (Throwable t) {
                LOG.warnv(t, "Failed to retrieve current roles of user {0} from Keycloak", entry.getKey());
                throw ServiceException
                        .serverError(format("Failed to retrieve current roles of user {0} from Keycloak", entry.getKey()));
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
                LOG.warnv(t, "Failed to modify roles of user {0}", entry.getKey());
                throw ServiceException.serverError(format("Failed to modify roles of user {0}", entry.getKey()));
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
        } catch (Throwable t) {
            LOG.warnv(t, "Failed to remove all roles of team {0}", team);
            throw ServiceException.serverError(format("Failed to remove all roles of team {0}", team));
        }
    }

    private RoleRepresentation ensureRole(String roleName) {
        try {
            return keycloak.realm(realm).roles().get(roleName).toRepresentation();
        } catch (NotFoundException e) {
            keycloak.realm(realm).roles().create(new RoleRepresentation(roleName, null, false));
            return keycloak.realm(realm).roles().get(roleName).toRepresentation();
        } catch (Throwable t) {
            throw ServiceException.serverError(format("Unable to fetch role {0}", roleName));
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
        for (String role : List.of(Roles.MANAGER, Roles.TESTER, Roles.VIEWER, Roles.UPLOADER, Roles.MACHINE)) {
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
                LOG.warnv("Registration of role {0} failed because it already exists", roleName);
            } else {
                throw ServiceException.serverError(format("Unable to create role {0}", roleName));
            }
        } catch (Throwable t) {
            throw ServiceException.serverError(format("Unable to create role {0}", roleName));
        }
    }

    @Override
    public void deleteTeam(String team) { // delete a team by deleting all the "team roles"
        String prefix = getTeamPrefix(team);
        for (String type : ROLE_TYPES) {
            try {
                keycloak.realm(realm).roles().deleteRole(prefix + type);
            } catch (NotFoundException e) {
                LOG.warnv("Role {0}{1} was not found when deleting it", prefix, type);
                throw ServiceException.notFound(format("Team {0} not found", team));
            } catch (Throwable t) {
                LOG.warnv(t, "Unable to delete team {0}", team);
                throw ServiceException.serverError(format("Unable to delete team {0}", team));
            }
        }
    }

    @Override
    public List<UserService.UserData> administrators() { // get the list of all the users with administrator role
        try {
            return keycloak.realm(realm).roles().get(Roles.ADMIN).getUserMembers(0, Integer.MAX_VALUE).stream()
                    .map(KeycloakUserBackend::toUserInfo).toList();
        } catch (Throwable t) {
            LOG.warnv(t, "Unable to list administrators");
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
                        LOG.infov("Removed administrator role from user {0}", user.getUsername());
                    } catch (Throwable t) {
                        LOG.warnv("Could not remove admin role from user {0} due to {1}", user.getUsername(), t.getMessage());
                    }
                }
            }

            for (String username : newAdmins) { // add admin role for `newAdmins` not in `oldAdmins`
                if (oldAdmins.stream().noneMatch(old -> username.equals(old.getUsername()))) {
                    try {
                        usersResource.get(findMatchingUser(username).getId()).roles().realmLevel().add(List.of(adminRole));
                        LOG.infov("Added administrator role to user {0}", username);
                    } catch (Throwable t) {
                        LOG.warnv("Could not add admin role to user {0} due to {1}", username, t.getMessage());
                    }
                }
            }
        } catch (ServiceException se) {
            throw se; // thrown above, re-throw
        } catch (Throwable t) {
            LOG.warnv(t, "Cannot fetch representation for admin role");
            throw ServiceException.serverError("Cannot find admin role");
        }
    }

    @Override
    public List<UserService.UserData> machineAccounts(String team) {
        try {
            String prefix = getTeamPrefix(team);
            return keycloak.realm(realm).roles().get(prefix + Roles.MACHINE).getUserMembers(0, Integer.MAX_VALUE).stream()
                    .map(KeycloakUserBackend::toUserInfo).toList();
        } catch (NotFoundException ex) {
            LOG.debugv("Unable to list machine accounts for team {0}", team);
            return List.of();
        } catch (Throwable t) {
            LOG.warnv(t, "Unable to list machine accounts for team {0}", team);
            throw ServiceException
                    .serverError("Please verify with the System Administrators that you have the correct permissions");
        }
    }

    @Override
    public void setPassword(String username, String password) {
        try {
            CredentialRepresentation credentials = new CredentialRepresentation();
            credentials.setType(CredentialRepresentation.PASSWORD);
            credentials.setValue(password);

            keycloak.realm(realm).users().get(findMatchingUser(username).getId()).resetPassword(credentials);
        } catch (Throwable t) {
            LOG.warnv(t, "Failed to retrieve current representation of user {0} from Keycloak", username);
            throw ServiceException
                    .serverError(format("Failed to retrieve current representation of user {0} from Keycloak", username));
        }
    }
}
