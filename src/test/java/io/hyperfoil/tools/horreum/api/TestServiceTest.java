package io.hyperfoil.tools.horreum.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.ws.rs.core.HttpHeaders;

import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.entity.json.View;
import io.hyperfoil.tools.horreum.entity.json.ViewComponent;
import io.hyperfoil.tools.horreum.test.NoGrafanaProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.RestAssured;
import io.smallrye.jwt.build.Jwt;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(NoGrafanaProfile.class)
public class TestServiceTest {
   private static final String[] UPLOADER_ROLES = { "foo-team", "foo-uploader", "uploader" };
   private static final String[] TESTER_ROLES = { "foo-team", "foo-tester", "tester", "viewer" };

   @Inject
   EntityManager em;

   @Inject
   SqlService sqlService;

   @org.junit.jupiter.api.Test
   public void testCreateDelete() {
      String testerToken = getAccessToken("alice", TESTER_ROLES);
      String uploaderToken = getAccessToken("alice", UPLOADER_ROLES);

      // create test
      Test test = createExampleTest();
      Test response = RestAssured.given().auth().oauth2(testerToken)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .body(test)
            .post("/api/test")
            .then()
            .statusCode(200)
            .extract().body().as(Test.class);
      try (CloseMe ignored = sqlService.withRoles(em, Arrays.asList(TESTER_ROLES))) {
         assertNotNull(Test.findById(response.id));
      }

      // add run to the test
      long timestamp = System.currentTimeMillis();
      String runIdString = RestAssured.given().auth().oauth2(uploaderToken)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .body("{ \"foo\" : \"bar\" }")
            .post("/api/run/data?start=" + timestamp + "&stop=" + timestamp + "&test=Foo&owner=foo-team&access=PUBLIC")
            .then()
            .statusCode(200)
            .extract().asString();
      int runId = Integer.parseInt(runIdString);

      // delete run
      RestAssured.given().auth().oauth2(testerToken)
            .delete("/api/test/" + response.id)
            .then()
            .statusCode(204);
      em.clear();
      try (CloseMe ignored = sqlService.withRoles(em, Arrays.asList(TESTER_ROLES))) {
         assertNull(Test.findById(response.id));
         // There's no constraint between runs and tests; therefore the run is not deleted
         assertNotNull(Run.findById(runId));
      }
   }

   private Test createExampleTest() {
      Test test = new Test();
      test.name = "Foo";
      test.description = "Bar";
      test.tags = "";
      test.owner = "foo-team";
      View defaultView = new View();
      defaultView.name = "Default";
      defaultView.components = new ArrayList<>();
      defaultView.components.add(new ViewComponent("Some column", "foo", null));
      test.defaultView = defaultView;
      return test;
   }

   private String getAccessToken(String userName, String... groups) {
      return Jwt.preferredUserName(userName)
            .groups(new HashSet<>(Arrays.asList(groups)))
            .issuer("https://server.example.com")
            .audience("https://service.example.com")
            .jws()
            .keyId("1")
            .sign();
   }
}
