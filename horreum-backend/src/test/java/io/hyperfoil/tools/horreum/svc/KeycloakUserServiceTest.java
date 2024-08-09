package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.test.KeycloakTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@TestProfile(KeycloakTestProfile.class)
@QuarkusTest
public class KeycloakUserServiceTest extends UserServiceAbstractTest {
}
