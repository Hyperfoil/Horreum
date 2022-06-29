package io.hyperfoil.tools.horreum.server;

import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.*;

import io.hyperfoil.tools.HorreumTestExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.fail;
import static io.hyperfoil.tools.HorreumTestExtension.TESTER_TOKEN;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith({HorreumTestExtension.class})
public class GrafanaUserFilterTest extends HorreumTestExtension {

   @Test
   @Order(1)
   void testFilterAnonymousAccess()
   {
      given().get("/").then().statusCode(200);
   }

   @Test
   @Order(2)
   void testFilterAuthorizedAccess() {
      try {
//         ValidatableResponse res = bareRequest().get("/").then();
         ValidatableResponse res = RestAssured.given().auth().oauth2(TESTER_TOKEN).get("/").then();
         res.statusCode(200);
         res.header("grafana_user", "user@example.com");
      } catch (Exception e){
         e.printStackTrace();
         fail(e.getMessage());
      }
   }
}
