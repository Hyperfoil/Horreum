package io.hyperfoil.tools.horreum.svc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import javax.ws.rs.core.HttpHeaders;

import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.SchemaService;
import io.hyperfoil.tools.horreum.entity.alerting.RegressionDetection;
import io.hyperfoil.tools.horreum.entity.json.Schema;
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

   public static Test createExampleTest(String testName) {
      Test test = new Test();
      test.name = testName;
      test.description = "Bar";
      test.tags = "";
      test.owner = TESTER_ROLES[0];
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

   protected int uploadRun(Object runJson, String test) {
      long timestamp = System.currentTimeMillis();
      return uploadRun(timestamp, timestamp, runJson, test);
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

   protected void deleteTest(Test test) {
      RestAssured.given().auth().oauth2(TESTER_TOKEN)
            .delete("/api/test/" + test.id)
            .then()
            .statusCode(204);
   }

   protected RequestSpecification jsonRequest() {
      return RestAssured.given().auth().oauth2(TESTER_TOKEN)
            .header(HttpHeaders.CONTENT_TYPE, "application/json");
   }

   protected String getTestName(TestInfo info) {
      return info.getTestClass().map(Class::getName).orElse("<unknown>") + "." + info.getDisplayName();
   }

   protected Schema createExampleSchema(TestInfo info) {
      Schema schema = new Schema();
      schema.owner = TESTER_ROLES[0];
      schema.name = info.getTestClass().map(Class::getName).orElse("<unknown>") + "." + info.getDisplayName();
      schema.uri = "urn:" + info.getTestClass().map(Class::getName).orElse("<unknown>") + ":" + info.getDisplayName() + ":1.0";
      jsonRequest().body(schema).post("/api/schema").then().statusCode(200);

      SchemaService.ExtractorUpdate extractor = new SchemaService.ExtractorUpdate();
      extractor.id = -1;
      extractor.accessor = "value";
      extractor.jsonpath = ".value";
      extractor.schema = schema.uri;
      jsonRequest().body(extractor).post("/api/schema/extractor").then().statusCode(200);
      return schema;
   }

   protected void setTestVariables(Test test, String name, String accessors, RegressionDetection... rds) {
      ArrayNode variables = JsonNodeFactory.instance.arrayNode();
      ObjectNode variable = JsonNodeFactory.instance.objectNode();
      variable.put("testid", test.id);
      variable.put("name", name);
      variable.put("accessors", accessors);
      if (rds.length > 0) {
         ArrayNode rdsArray = JsonNodeFactory.instance.arrayNode();
         for (RegressionDetection rd : rds) {
            rdsArray.add(JsonNodeFactory.instance.objectNode().put("model", rd.model).set("config", rd.config));
         }
         variable.set("regressionDetection", rdsArray);
      }
      variables.add(variable);
      jsonRequest().body(variables.toString()).post("/api/alerting/variables?test=" + test.id).then().statusCode(204);
   }
}
