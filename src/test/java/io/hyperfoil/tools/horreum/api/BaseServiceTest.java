package io.hyperfoil.tools.horreum.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import javax.ws.rs.core.HttpHeaders;

import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.entity.json.View;
import io.hyperfoil.tools.horreum.entity.json.ViewComponent;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import io.smallrye.jwt.build.Jwt;

public class BaseServiceTest {
   static final String[] TESTER_ROLES = { "foo-team", "foo-tester", "tester", "viewer" };
   static final String[] UPLOADER_ROLES = { "foo-team", "foo-uploader", "uploader" };
   static final String TESTER_TOKEN = BaseServiceTest.getAccessToken("alice", TESTER_ROLES);
   static final String UPLOADER_TOKEN = BaseServiceTest.getAccessToken("alice", UPLOADER_ROLES);

   public static Test createExampleTest() {
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

   public static String getAccessToken(String userName, String... groups) {
      return Jwt.preferredUserName(userName)
            .groups(new HashSet<>(Arrays.asList(groups)))
            .issuer("https://server.example.com")
            .audience("https://service.example.com")
            .jws()
            .keyId("1")
            .sign();
   }

   protected int uploadRun(long start, long stop, Object runJson, String test) {
      String runIdString = RestAssured.given().auth().oauth2(UPLOADER_TOKEN)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .body(runJson)
            .post("/api/run/data?start=" + start + "&stop=" + stop + "&test=" + test + "&owner=foo-team&access=PUBLIC")
            .then()
            .statusCode(200)
            .extract().asString();
      return Integer.parseInt(runIdString);
   }

   protected Test createTest(Test test) {
      return jsonRequest()
            .body(test)
            .post("/api/test")
            .then()
            .statusCode(200)
            .extract().body().as(Test.class);
   }

   protected RequestSpecification jsonRequest() {
      return RestAssured.given().auth().oauth2(TESTER_TOKEN)
            .header(HttpHeaders.CONTENT_TYPE, "application/json");
   }
}
