package io.hyperfoil.tools.horreum.svc;

import org.junit.jupiter.api.Disabled;

import io.hyperfoil.tools.horreum.test.KeycloakTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@TestProfile(KeycloakTestProfile.class)
@QuarkusTest
@Disabled("Flaky test https://github.com/Hyperfoil/Horreum/issues/2086")
public class KeycloakUserServiceTest extends UserServiceAbstractTest {
}
