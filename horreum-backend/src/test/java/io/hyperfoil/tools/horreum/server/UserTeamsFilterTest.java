package io.hyperfoil.tools.horreum.server;

import static javax.persistence.LockModeType.PESSIMISTIC_WRITE;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Arrays;

import javax.persistence.LockModeType;
import javax.transaction.Transactional;

import org.junit.Before;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import io.hyperfoil.tools.horreum.entity.UserInfo;
import io.hyperfoil.tools.horreum.svc.BaseServiceTest;
import io.hyperfoil.tools.horreum.test.NoGrafanaProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.RestAssured;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(NoGrafanaProfile.class)
public class UserTeamsFilterTest extends BaseServiceTest {

   @Before
   public void before() {
      String username = "alice";
      UserInfo userInfo = getUserInfo(username, PESSIMISTIC_WRITE);
      if (userInfo != null) {
         userInfo.delete();
      }
   }
   @org.junit.jupiter.api.Test
   public void testFilterCreatedLoggedInUser() {
      String username = "alice";
      bareRequest().cookie("horreum.teams="+TESTER_ROLES[0]).get("/").then()
         /*.statusCode(200)*/
         .assertThat().cookie("horreum.teams", username + "!" + TESTER_ROLES[0] );
      UserInfo createdUserInfo = getUserInfo(username, PESSIMISTIC_WRITE);
      assertNotNull(createdUserInfo);
   }

   @org.junit.jupiter.api.Test
   public void testAnonymous() {
      String username = "????";
      RestAssured.given().get("/").then()
         /*.statusCode(200)*/;
      UserInfo createdUserInfo = getUserInfo(username, PESSIMISTIC_WRITE);
      assertNull(createdUserInfo);
   }

   @Transactional
   UserInfo getUserInfo(String username, LockModeType type) {
      try (CloseMe ignored = roleManager.withRoles(em, Arrays.asList(username))) {
         return UserInfo.findById(username, LockModeType.PESSIMISTIC_WRITE);
      }
   }
}
