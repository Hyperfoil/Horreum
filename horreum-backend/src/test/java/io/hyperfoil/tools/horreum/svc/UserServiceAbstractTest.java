package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.api.internal.services.UserService;
import io.hyperfoil.tools.horreum.entity.user.UserInfo;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.security.TestIdentityAssociation;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for {@link UserServiceImpl} that is executed for every back-end
 */
public abstract class UserServiceAbstractTest {

    private static final Logger LOG = Logger.getLogger(UserServiceAbstractTest.class);

    //  the name of the default keycloak user with "admin" role (see io.quarkus.test.keycloak.server.KeycloakTestResourceLifecycleManager#createUsers)
    private static final String KEYCLOAK_ADMIN = "admin";

    @Inject UserServiceImpl userService;

    /**
     * Runs a section of a test under a different user
     */
    private void overrideTestSecurity(String name, Set<String> roles, Runnable runnable) {
        SecurityIdentity identity = QuarkusSecurityIdentity.builder().setPrincipal(new QuarkusPrincipal(name)).addRoles(roles).build();
        TestIdentityAssociation identityAssociation = CDI.current().select(TestIdentityAssociation.class).get();
        SecurityIdentity previous = identityAssociation.getTestIdentity();
        try {
            identityAssociation.setTestIdentity(identity);
            runnable.run();
        } finally {
            identityAssociation.setTestIdentity(previous);
        }
    }

    @TestSecurity(user = KEYCLOAK_ADMIN, roles = { Roles.ADMIN })
    @Test void administratorsTest() {
        String adminUserName = KEYCLOAK_ADMIN, testUserName = "administrator-test-user".toLowerCase();

        UserService.NewUser adminUser = new UserService.NewUser();
        adminUser.user = new UserService.UserData("", adminUserName, "Admin", "User", "super@horreum.io");
        adminUser.password = "super-secret";
        adminUser.team = "performance-team";
        adminUser.roles = Collections.emptyList();

        // create the admin user and give it the admin role
        try {
            userService.createUser(adminUser);
            userService.updateAdministrators(List.of(adminUserName));
            LOG.infov("Created user {0}", adminUserName);
        } catch (ServiceException se) {
            // in the keycloak implementation this admin user already exists, and therefore the exception is expected
            assertEquals(se.getMessage(), "User exists with same username");
        }
        List<String> adminList = userService.administrators().stream().map(u -> u.username).toList();
        assertTrue(adminList.size() == 1 && adminList.contains(adminUserName));

        // create the test user
        UserService.NewUser testUser = new UserService.NewUser();
        testUser.user = new UserService.UserData("", testUserName, "Test", "User", "test@horreum.io");
        testUser.password = "secret";
        testUser.team = "horreum-team";
        testUser.roles = Collections.emptyList();
        userService.createUser(testUser);

        // verify the test user does not have admin role
        adminList = userService.administrators().stream().map(u -> u.username).toList();
        assertFalse(adminList.contains(testUserName));

        // verify that the admin user can't remove itself
        assertThrows(ServiceException.class, () -> userService.updateAdministrators(List.of(testUserName)));

        // give admin role to test user and verify it shows up in administrators list
        userService.updateAdministrators(List.of(adminUserName, testUserName));
        adminList = userService.administrators().stream().map(u -> u.username).toList();
        assertTrue(adminList.contains(adminUserName) && adminList.contains(testUserName));

        // remove admin role of test user
        userService.updateAdministrators(List.of(adminUserName));
        adminList = userService.administrators().stream().map(u -> u.username).toList();
        assertTrue(adminList.contains(adminUserName));
        assertFalse(adminList.contains(testUserName));

        // give admin role to test user again
        userService.updateAdministrators(List.of(adminUserName, testUserName));
        adminList = userService.administrators().stream().map(u -> u.username).toList();
        assertTrue(adminList.contains(adminUserName) && adminList.contains(testUserName));

        // verify that an unknown user can't be added (and don't cause an exception to be thrown)
        userService.updateAdministrators(List.of(adminUserName, testUserName, "some-random-dude"));
        adminList = userService.administrators().stream().map(u -> u.username).toList();
        assertFalse(adminList.contains("some-random-dude"));
    }

    @TestSecurity(user = KEYCLOAK_ADMIN, roles = { Roles.ADMIN })
    @Test void teamTest() {
        String testTeam = "an-unique-test-team";
        List<String> originalTeams = userService.getTeams();

        // create new test team
        userService.addTeam(testTeam);
        List<String> actualTeams = userService.getTeams();
        assertEquals(originalTeams.size() + 1, actualTeams.size());
        assertTrue(actualTeams.contains(testTeam));

        // both calls should return the same teams
        assertEquals(userService.getTeams(), userService.getAllTeams());

        // create a user on the team
        UserService.NewUser managerUser = new UserService.NewUser();
        managerUser.user = new UserService.UserData("", "team-manager-test-user", "Team", "User", "team@horreum.io");
        managerUser.password = "secret";
        managerUser.team = testTeam;
        managerUser.roles = List.of("manager");
        userService.createUser(managerUser);

        // delete test team
        userService.deleteTeam(testTeam);
        actualTeams = userService.getTeams();
        assertEquals(originalTeams.size(), actualTeams.size());
        assertFalse(actualTeams.contains(testTeam));

        // delete team already deleted
        assertThrows(ServiceException.class, () -> userService.deleteTeam(testTeam));
    }

    @TestSecurity(user = KEYCLOAK_ADMIN, roles = { Roles.ADMIN })
    @Test void teamManagerTest() {
        String testTeam = "managed-test-team", otherTeam = "some-team-that-does-not-exist-team";
        userService.addTeam(testTeam);

        // getting members of a non-existing team should not throw
        assertTrue(userService.teamMembers(otherTeam).isEmpty());

        overrideTestSecurity("manager", Set.of(testTeam.substring(0, testTeam.length() - 4) + Roles.MANAGER), () -> {
            String managedUser = "managed";

            // team just created has no members
            assertTrue(userService.teamMembers(testTeam).isEmpty());

            // add a user to the team with "tester" role
            UserService.NewUser user = new UserService.NewUser();
            user.user = new UserService.UserData("", managedUser, "Managed", "User", "managed@horreum.io");
            user.password = "secret";
            user.team = "managed-test-team";
            user.roles = List.of(Roles.TESTER);
            userService.createUser(user);

            // verify created user
            List<String> userRoles = userService.teamMembers(testTeam).get(managedUser);
            assertTrue(userRoles.contains(Roles.TESTER));

            // change the roles of the managed user
            userService.updateTeamMembers(testTeam, Map.of(managedUser, List.of(Roles.VIEWER, Roles.UPLOADER)));
            userRoles = userService.teamMembers(testTeam).get(managedUser);
            assertTrue(userRoles.contains(Roles.VIEWER) && userRoles.contains(Roles.UPLOADER));
            assertFalse(userRoles.contains(Roles.TESTER) || userRoles.contains(Roles.MANAGER));

            // remove all roles of a user
            userService.updateTeamMembers(testTeam, Map.of(managedUser, List.of()));
            assertTrue(userService.teamMembers(testTeam).isEmpty());

            // user removing the manger role from itself
            userService.updateTeamMembers(testTeam, Map.of(managedUser, List.of(Roles.TESTER, Roles.MANAGER)));
            overrideTestSecurity(managedUser, Set.of(testTeam.substring(0, testTeam.length() - 4) + Roles.MANAGER), () -> {
                userService.updateTeamMembers(testTeam, Map.of(managedUser, List.of(Roles.TESTER)));
                List<String> managedUserRoles = userService.teamMembers(testTeam).get(managedUser);
                assertFalse(managedUserRoles.contains(Roles.MANAGER));
            });
            userRoles = userService.teamMembers(testTeam).get(managedUser);
            assertTrue(userRoles.contains(Roles.TESTER));

            // remove all allUsers from the team
            userService.updateTeamMembers(testTeam, Map.of());
            assertTrue(userService.teamMembers(testTeam).isEmpty());

            // check that a manager cannot manager other team
            assertThrows(ServiceException.class, () -> userService.updateTeamMembers(otherTeam, Map.of()));
        });

        overrideTestSecurity("manager", Set.of(otherTeam.substring(0, otherTeam.length() - 4) + Roles.MANAGER), () -> {
            // check that a manager cannot manager non-existing team
            assertThrows(ServiceException.class, () -> userService.updateTeamMembers(otherTeam, Map.of()));
        });

        userService.deleteTeam(testTeam);
    }

    @TestSecurity(user = KEYCLOAK_ADMIN, roles = { Roles.ADMIN })
    @Test void machineAccountTest() {
        String testTeam = "machine-test-team";
        userService.addTeam(testTeam);

        overrideTestSecurity("manager", Set.of(Roles.MANAGER, testTeam.substring(0, testTeam.length() - 4) + Roles.MANAGER), () -> {
            String machineUser = "machine-account";

            // add a user to the team with "machine" role
            UserService.NewUser user = new UserService.NewUser();
            user.user = new UserService.UserData("", machineUser, "Machine", "Account", "machine@horreum.io");
            user.password = "whatever";
            user.team = testTeam;
            user.roles = List.of(Roles.UPLOADER, Roles.MACHINE);
            userService.createUser(user);

            // user should not show up in search or team membership
            assertFalse(userService.teamMembers(testTeam).containsKey(machineUser));
            assertTrue(userService.searchUsers(machineUser).isEmpty());

            // user should be able to authenticate with the password provided on create
            given().auth().preemptive().basic(machineUser, "wrong-password").get("api/user/roles").then().statusCode(SC_UNAUTHORIZED);
            given().auth().preemptive().basic(machineUser, "whatever").get("api/user/roles").then().statusCode(SC_OK);

            // reset password
            String newPassword = userService.resetPassword(testTeam, machineUser);
            assertFalse(newPassword.isEmpty(), "Expected some generated password");

            // user should be able to authenticate now
            given().auth().preemptive().basic(machineUser, "whatever").get("api/user/roles").then().statusCode(SC_UNAUTHORIZED);
            given().auth().preemptive().basic(machineUser, newPassword).get("api/user/roles").then().statusCode(SC_OK);

            // manager remove account
            userService.removeUser(machineUser);
            assertThrows(ServiceException.class, () -> userService.removeUser(machineUser));
        });

        userService.deleteTeam(testTeam);
    }

    @TestSecurity(user = KEYCLOAK_ADMIN, roles = { Roles.ADMIN })
    @Test void defaultTeamTest() {
        String testUserName = "default-team-user";

        // create a test user
        UserService.NewUser testUser = new UserService.NewUser();
        testUser.user = new UserService.UserData("", testUserName, "Default Team", "User", "default@horreum.io");
        testUser.password = "secret";
        testUser.team = "default-test-team";
        testUser.roles = Collections.emptyList();
        userService.createUser(testUser);

        overrideTestSecurity(testUserName, Set.of(), () -> {
            String fistTeam = "first-default-team", secondTeam = "second-default-team";

            // workaround for keycloak
            addUserInfo(testUserName);

            // default team defined
            assertEquals("default-test-team", userService.defaultTeam());

            // changed to some team
            userService.setDefaultTeam(fistTeam);
            assertEquals(fistTeam, userService.defaultTeam());

            // set value to a different team
            userService.setDefaultTeam("\"" + secondTeam + "\"");
            assertEquals(secondTeam, userService.defaultTeam());

            // attempt to reset default team
            assertThrows(ServiceException.class, () -> userService.setDefaultTeam(""));
            assertThrows(ServiceException.class, () -> userService.setDefaultTeam(null));
        });

        // test non-existent user
        overrideTestSecurity("some-non-exiting-dude", Set.of(), () -> {
            try {
                userService.setDefaultTeam("some-non-existing-team");
                fail("Expected ServiceException");
            } catch (ServiceException se) {
                assertEquals(Response.Status.NOT_FOUND.getStatusCode(), se.getResponse().getStatus());
            }
        });
    }

    @Transactional
    @WithRoles(addUsername = true)
    void addUserInfo(String username) {
        try {
            UserInfo userInfo = new UserInfo(username);
            userInfo.persistAndFlush();
        } catch (Throwable ignored) {
            // database backend already has username
        }
    }

    @TestSecurity(user = KEYCLOAK_ADMIN, roles = { Roles.ADMIN })
    @Test void userInfoTest() {
        String[] usernames = new String[] { "barreiro-test-user", "barracuda-test-user", "barreto-test-user", "barrabas-test-user" };
        String lastname = "Info"; // mixed case
        int i = 0; // for uniqueness

        // create all test users
        for (String username : usernames) {
            UserService.NewUser testUser = new UserService.NewUser();
            testUser.user = new UserService.UserData("", username, username.substring(0, username.indexOf("-")), lastname, "info-user-" + i++ + "@horreum.io");
            testUser.password = "secret";
            testUser.team = "info-team";
            testUser.roles = Collections.emptyList();
            userService.createUser(testUser);
        }

        // test all users match - beginning / middle / end
        assertUserSearch("barr", usernames);
        assertUserSearch(lastname.toLowerCase(), usernames);

        // test some users match
        assertUserSearch("barre", usernames[0], usernames[2]);
        assertUserSearch("barra", usernames[1], usernames[3]);

        // test single user match
        assertUserSearch("barreiro", usernames[0]);
        assertUserSearch("barreto", usernames[2]);

        // test no user match
        assertUserSearch("non-existent-user");

        // test user info
        List<UserService.UserData> userDataList = userService.info(List.of(usernames));
        assertEquals(usernames.length, userDataList.size());
        assertEquals(lastname, userDataList.get(0).lastName);

        // no user should return empty list
        assertTrue(userService.info(List.of("non-existent-user")).isEmpty());

        // test roles of admin user
        assertEquals(List.of("admin"), userService.getRoles());
    }

    private void assertUserSearch(String search, String... expected) {
        List<UserService.UserData> allUsers = userService.searchUsers(search);
        assertEquals(expected.length, allUsers.size());
        for (String username : expected) {
            assertTrue(allUsers.stream().anyMatch(u -> username.equals(u.username)));
        }
    }

    @TestSecurity(user = KEYCLOAK_ADMIN, roles = { Roles.ADMIN })
    @Test void createUserTest() {
        String firstUser = "create-user-test-user", secondUser = "another-create-test-user", thirdUser = "create-user-admin-user", testTeam = "create-user-test-team";

        // create team
        userService.addTeam(testTeam);

        // attempt to create null user
        assertThrows(ServiceException.class, () -> userService.createUser(null));

        // create a test user
        UserService.NewUser testUser = new UserService.NewUser();
        testUser.user = new UserService.UserData("", "horreum." + firstUser, "Create", "User", "create@horreum.io");
        testUser.password = "secret";
        testUser.roles = Collections.emptyList();

        // test reserved user name
        assertThrows(ServiceException.class, () -> userService.createUser(testUser));
        testUser.user.username = firstUser;

        // test long team name
        testUser.team = "a-very-very-long-team-name-that-goes-over-the-maximum-capacity-team";
        assertThrows(ServiceException.class, () -> userService.createUser(testUser));

        // test team name not ending with "-team"
        testUser.team = "invalid";
        assertThrows(ServiceException.class, () -> userService.createUser(testUser));

        // test no team
        testUser.team = "";
        assertThrows(ServiceException.class, () -> userService.createUser(testUser));

        // test success with empty team
        testUser.team = testTeam;
        userService.createUser(testUser);
        assertFalse(userService.info(List.of(firstUser)).isEmpty());

        // test duplicate
        assertThrows(ServiceException.class, () -> userService.createUser(testUser));

        // test duplicate email
        UserService.NewUser anotherUser = new UserService.NewUser();
        anotherUser.user = new UserService.UserData("", secondUser, "Another", "User", "create@horreum.io");
        anotherUser.password = "secret";
        anotherUser.team = testTeam;
        anotherUser.roles = List.of("viewer", "tester", "uploader", "manager", "nonexistent");
        assertThrows(ServiceException.class, () -> userService.createUser(anotherUser));

        // create second user
        anotherUser.user.email = "create-another@horreum.io";
        userService.createUser(anotherUser);
        assertFalse(userService.info(List.of(secondUser)).isEmpty());

        // test all roles
        assertTrue(userService.teamMembers(testTeam).get(secondUser).containsAll(List.of("viewer", "tester", "uploader", "manager")));

        // test create user without password
        UserService.NewUser impostor = new UserService.NewUser();
        impostor.user = new UserService.UserData("", thirdUser, "Admin", "User", "create-admin@horreum.io");
        impostor.roles = List.of(Roles.ADMIN);
        assertThrows(ServiceException.class, () -> userService.createUser(anotherUser));

        // test empty password
        impostor.password = "";
        assertThrows(ServiceException.class, () -> userService.createUser(anotherUser));

        // create third user (without team)
        impostor.password = "secret";
        userService.createUser(impostor);
        assertFalse(userService.info(List.of(thirdUser)).isEmpty());

        // test attempt to set admin role
        assertFalse(userService.administrators().stream().anyMatch(data -> thirdUser.equals(data.username)));

        // test remove user
        userService.removeUser(secondUser);
        assertThrows(ServiceException.class, () -> userService.removeUser("some-non-existent-user"));

        // test recreate
        userService.createUser(anotherUser);

        // delete team
        userService.deleteTeam(testTeam);
    }

    @Test void authorizationTest() {
        // unauthenticated user
        assertThrows(UnauthorizedException.class, userService::getRoles);
        assertThrows(UnauthorizedException.class, () -> userService.searchUsers(null));
        assertThrows(UnauthorizedException.class, () -> userService.info(null));
        assertThrows(UnauthorizedException.class, () -> userService.createUser(null));
        assertThrows(UnauthorizedException.class, () -> userService.removeUser(null));
        assertThrows(UnauthorizedException.class, userService::getTeams);
        assertThrows(UnauthorizedException.class, userService::defaultTeam);
        assertThrows(UnauthorizedException.class, () -> userService.setDefaultTeam(null));
        assertThrows(UnauthorizedException.class, () -> userService.teamMembers(null));
        assertThrows(UnauthorizedException.class, () -> userService.updateTeamMembers(null, null));
        assertThrows(UnauthorizedException.class, userService::getAllTeams);
        assertThrows(UnauthorizedException.class, () -> userService.addTeam(null));
        assertThrows(UnauthorizedException.class, () -> userService.deleteTeam(null));
        assertThrows(UnauthorizedException.class, userService::administrators);
        assertThrows(UnauthorizedException.class, () -> userService.updateAdministrators(List.of()));
        assertThrows(UnauthorizedException.class, () -> userService.resetPassword(null, null));

        // user authenticated but without the necessary privileges
        overrideTestSecurity("unprivileged-user", Set.of(), () -> {
            assertThrows(ForbiddenException.class, userService::getAllTeams);
            assertThrows(ForbiddenException.class, () -> userService.addTeam(null));
            assertThrows(ForbiddenException.class, () -> userService.deleteTeam(null));
            assertThrows(ForbiddenException.class, userService::administrators);
            assertThrows(ForbiddenException.class, () -> userService.updateAdministrators(List.of()));
            assertThrows(ForbiddenException.class, () -> userService.searchUsers(null));
            assertThrows(ForbiddenException.class, () -> userService.info(new ArrayList<>()));
        });
    }

    @TestSecurity(user = KEYCLOAK_ADMIN, roles = { Roles.ADMIN })
    @Test void userSearchOnlyForAdminManagerTest() {
        int beforeCount = userService.searchUsers("").size();
        String testTeam = "foobar-test-team";
        userService.addTeam(testTeam);
        String testUserName = "foo-team-user";

        UserService.NewUser testUser = new UserService.NewUser();
        testUser.user = new UserService.UserData("", testUserName, "Foo", "Bar", "foobar@horreum.io");
        testUser.password = "secret";
        testUser.team = testTeam;
        testUser.roles = Collections.emptyList();
        userService.createUser(testUser);
        assertEquals(beforeCount + 1, userService.searchUsers("").size());
        assertEquals(1, userService.info(List.of(testUserName)).size());
        UserService.NewUser testManagerUser = new UserService.NewUser();
        String testManagerUserName = "aacme";
        testManagerUser.user = new UserService.UserData("", testManagerUserName, "Andrew", "acme", "aacme@horreum.io");
        testManagerUser.password = "secret";
        testManagerUser.team = testTeam;
        testManagerUser.roles = List.of(Roles.MANAGER);
        userService.createUser(testManagerUser);
        assertEquals(1, userService.searchUsers(testManagerUserName).size());
        assertEquals(1, userService.info(List.of(testManagerUserName)).size());
        int fooBarTestTeamCount = userService.searchUsers("").size();
        overrideTestSecurity(testManagerUserName, Set.of(Roles.MANAGER), () -> {
            assertEquals(fooBarTestTeamCount, userService.searchUsers("").size());
            assertEquals(1, userService.info(List.of(testManagerUserName)).size());
        });
    }
}
