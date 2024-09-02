package io.hyperfoil.tools.horreum.svc;

import static io.restassured.RestAssured.given;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.hyperfoil.tools.horreum.api.internal.services.UserService;
import io.hyperfoil.tools.horreum.test.DatabaseRolesTestProfile;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;

@QuarkusTest
@TestProfile(DatabaseRolesTestProfile.class)
public class BasicAuthTest {

    @Inject
    UserServiceImpl userService;

    @TestSecurity(user = "admin", roles = { Roles.ADMIN })
    @Test
    void basicAuthTest() {
        String USERNAME = "botAccount", PASSWORD = "botPassword";

        // HTTP request for the non-existing user should fail
        given().auth().preemptive().basic(USERNAME, PASSWORD).get("api/user/roles").then().statusCode(SC_UNAUTHORIZED);

        // create user account
        UserService.NewUser newUser = new UserService.NewUser();
        newUser.user = new UserService.UserData("", USERNAME, "Bot", "Account", "bot@horreum.io");
        newUser.password = PASSWORD;
        userService.createUser(newUser);
        Log.infov("Created test user {0} with password {1}", USERNAME, PASSWORD);

        // user should be able to authenticate now
        given().auth().preemptive().basic(USERNAME, PASSWORD).get("api/user/roles").then().statusCode(SC_OK);

        // request with bad password
        given().auth().preemptive().basic(USERNAME, PASSWORD.substring(1)).get("api/user/roles").then()
                .statusCode(SC_UNAUTHORIZED);
    }

}
