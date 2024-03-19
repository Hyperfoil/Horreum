package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.test.DatabaseRolesTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(DatabaseRolesTestProfile.class)
public class DatabaseUserServiceTest extends UserServiceAbstractTest {
}
