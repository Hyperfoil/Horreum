package io.hyperfoil.tools.horreum.svc;


import static io.restassured.RestAssured.given;

import javax.ws.rs.core.HttpHeaders;

import io.hyperfoil.tools.horreum.entity.json.Access;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.entity.json.TestToken;
import io.hyperfoil.tools.horreum.server.TokenInterceptor;
import io.hyperfoil.tools.horreum.test.NoGrafanaProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(NoGrafanaProfile.class)
public class TokenAuthTest extends BaseServiceTest {
   @org.junit.jupiter.api.Test
   public void testTokenInHeader() {
      Test test = createExampleTest("private");
      test.access = Access.PRIVATE;
      test = createTest(test);

      // assert test exists
      jsonRequest().get("/api/test/" + test.id).then().statusCode(200);
      // assert not accessible without auth
      given().get("/api/test/" + test.id).then().statusCode(404);

      addToken(test, TestToken.READ, "foobar");

      given().get("/api/test/" + test.id).then().statusCode(404);
      given().get("/api/test/" + test.id + "?token=foobar").then().statusCode(200);
      given().get("/api/test/" + test.id + "?token=blabla").then().statusCode(404);

      given().header(TokenInterceptor.TOKEN_HEADER, "foobar").get("/api/test/" + test.id).then().statusCode(200);
      given().header(HttpHeaders.AUTHORIZATION, "Bearer foobar").get("/api/test/" + test.id).then().statusCode(200); // TODO: this seems wrong as the OIDC server never verifies the token

      given().get("/api/test/" + test.id + "?token=" + getAccessToken("alice", TESTER_ROLES)).then().statusCode(400);
   }
}
