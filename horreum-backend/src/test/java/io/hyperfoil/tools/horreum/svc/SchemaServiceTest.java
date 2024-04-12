package io.hyperfoil.tools.horreum.svc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.data.*;
import io.hyperfoil.tools.horreum.api.report.TableReport;
import io.hyperfoil.tools.horreum.api.report.TableReportConfig;
import io.hyperfoil.tools.horreum.api.services.SchemaService;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.entity.data.*;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.hyperfoil.tools.horreum.test.TestUtil;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.common.mapper.TypeRef;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.TestInfo;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class SchemaServiceTest extends BaseServiceTest {

   @Inject
   ServiceMediator serviceMediator;

   @org.junit.jupiter.api.Test
   public void testValidateRun() throws IOException, InterruptedException {
      JsonNode allowAny = load("/allow-any.json");
      Schema allowAnySchema = createSchema("any", allowAny.path("$id").asText(), allowAny);
      JsonNode allowNone = load("/allow-none.json");
      Schema allowNoneSchema = createSchema("none", allowNone.path("$id").asText(), allowNone);

      Test test = createTest(createExampleTest("schemaTest"));
      BlockingQueue<Schema.ValidationEvent> runValidations = serviceMediator.getEventQueue(AsyncEventChannels.RUN_VALIDATED, test.id) ;
      BlockingQueue<Schema.ValidationEvent> datasetValidations = serviceMediator.getEventQueue(AsyncEventChannels.DATASET_VALIDATED, test.id) ;

      ArrayNode data = JsonNodeFactory.instance.arrayNode();
      data.addObject().put("$schema", allowAnySchema.uri).put("foo", "bar");
      data.addObject().put("$schema", allowNoneSchema.uri).put("foo", "bar");
      data.addObject().put("$schema", "urn:unknown:schema").put("foo", "bar");
      int runId = uploadRun(data.toString(), test.name);

      Schema.ValidationEvent runValidation = runValidations.poll(10, TimeUnit.SECONDS);
      assertNotNull(runValidation);
      assertEquals(runId, runValidation.id);
      // one error for extra "foo" and one for "$schema"
      assertEquals(2, runValidation.errors.size());
      runValidation.errors.forEach(e -> {
         assertEquals(allowNoneSchema.id, e.getSchemaId());
         assertNotNull(e.error);
      });

      Schema.ValidationEvent dsValidation = datasetValidations.poll(10, TimeUnit.SECONDS);
      assertNotNull(dsValidation);
      assertEquals(2, dsValidation.errors.size());
      dsValidation.errors.forEach(e -> {
         assertEquals(allowNoneSchema.id, e.getSchemaId());
         assertNotNull(e.error);
      });
      assertEquals(0, runValidations.drainTo(new ArrayList<>()));
      assertEquals(0, datasetValidations.drainTo(new ArrayList<>()));

      allowAnySchema.schema = allowNone.deepCopy();
      ((ObjectNode) allowAnySchema.schema).set("$id", allowAny.path("$id").deepCopy());
      addOrUpdateSchema(allowAnySchema);

      Schema.ValidationEvent runValidation2 = runValidations.poll(10, TimeUnit.SECONDS);
      assertNotNull(runValidation2);
      assertEquals(runId, runValidation2.id);
      // This time we get errors for both schemas
      assertEquals(4, runValidation2.errors.size());

      Schema.ValidationEvent datasetValidation2 = datasetValidations.poll(10, TimeUnit.SECONDS);
      assertNotNull(datasetValidation2);
      assertEquals(4, datasetValidation2.errors.size());

      assertEquals(4, em.createNativeQuery("SELECT COUNT(*)::::int FROM run_validationerrors").getSingleResult());
      assertEquals(4, em.createNativeQuery("SELECT COUNT(*)::::int FROM dataset_validationerrors").getSingleResult());
   }

   @org.junit.jupiter.api.Test
   public void testEditSchema() {
      Schema schema = createSchema("My Schema", "urn:my:schema");
      assertEquals("My Schema", schema.name);
      Assertions.assertTrue(schema.id != null && schema.id > 0);
      int labelId = addLabel(schema, "foo", null, new Extractor("foo", "$.foo", false));
      List<Label> labels = jsonRequest().get("/api/schema/" + schema.id + "/labels")
              .then().statusCode(200).extract().body().jsonPath().getList(".", Label.class);
      assertEquals(1, labels.size());
      assertEquals("foo", labels.get(0).name);
      //schema = jsonRequest().get("/api/schema/" + schema.id).then().statusCode(200).extract().body().as(Schema.class);
      int transformerId = createTransformer("my-transformer", schema, "value => value",
              new Extractor("all", "$.", false)).id;

      List<Transformer> transformers = jsonRequest().get("/api/schema/" + schema.id + "/transformers")
              .then().statusCode(200).extract().body().jsonPath().getList(".", Transformer.class);
      assertEquals(1, transformers.size());
      assertEquals("my-transformer", transformers.get(0).name);

      schema.name = "Different name";
      schema.description = "Bla bla";
      schema = addOrUpdateSchema(schema);
      checkEntities(labelId, transformerId);
      //check labels and transformers using the rest interface as well
      labels = jsonRequest().get("/api/schema/" + schema.id + "/labels")
              .then().statusCode(200).extract().body().jsonPath().getList(".", Label.class);
      assertEquals(1, labels.size());
      assertEquals("foo", labels.get(0).name);
      transformers = jsonRequest().get("/api/schema/" + schema.id + "/transformers")
              .then().statusCode(200).extract().body().jsonPath().getList(".", Transformer.class);
      assertEquals(1, transformers.size());
      assertEquals("my-transformer", transformers.get(0).name);

      schema.uri = "http://example.com/otherschema";
      schema.description = null;
      addOrUpdateSchema(schema);
      checkEntities(labelId, transformerId);

      // back to original
      schema.name = "My schema";
      schema.uri = "urn:my:schema";
      schema = addOrUpdateSchema(schema);
      assertEquals("urn:my:schema", schema.uri);
      checkEntities(labelId, transformerId);

      //lets add another tranformer
      int newTransformerId = createTransformer("my-transformer2", schema, "value => value + 10",
              new Extractor("all", "$.", false)).id;
      transformers = jsonRequest().get("/api/schema/" + schema.id + "/transformers")
              .then().statusCode(200).extract().body().jsonPath().getList(".", Transformer.class);
      assertEquals(2, transformers.size());
      assertEquals(newTransformerId, transformers.get(1).id);
      //lets also get the transformer from the allTransformers endpoint
      List<SchemaService.TransformerInfo> infos = jsonRequest().get("/api/schema/allTransformers")
              .then().statusCode(200).extract().body().jsonPath().getList(".", SchemaService.TransformerInfo.class);
      assertEquals(2, infos.size());
      assertEquals(newTransformerId, infos.get(1).transformerId);

      labels = jsonRequest().get("/api/schema/" + schema.id + "/labels")
              .then().statusCode(200).extract().body().jsonPath().getList(".", Label.class);
      assertEquals(1, labels.size());
      assertEquals("foo", labels.get(0).name);
      //lets also get the labels from the allLabels endpoint
      List<SchemaService.LabelInfo> labelInfos = jsonRequest().get("/api/schema/allLabels")
              .then().statusCode(200).extract().body().as(new TypeRef<>() {});
      assertEquals(1, labelInfos.size());
      assertEquals("foo", labelInfos.get(0).name);
      assertEquals(schema.id, labelInfos.get(0).schemas.get(0).id);

      //then we delete one transformer
      jsonRequest().delete("/api/schema/" + schema.id + "/transformers/" + transformerId)
              .then().statusCode(204);
      transformers = jsonRequest().get("/api/schema/" + schema.id + "/transformers")
              .then().statusCode(200).extract().body().jsonPath().getList(".", Transformer.class);
      assertEquals(1, transformers.size());
      assertEquals(newTransformerId, transformers.get(0).id);
      //make sure it did not affect the lables
      labels = jsonRequest().get("/api/schema/" + schema.id + "/labels")
              .then().statusCode(200).extract().body().jsonPath().getList(".", Label.class);
      assertEquals(1, labels.size());
      assertEquals("foo", labels.get(0).name);
   }

   private void checkEntities(int labelId, int transformerId) {
      assertEquals(1, SchemaDAO.count());
      assertEquals(1, LabelDAO.count());
      assertEquals(1, TransformerDAO.count());
      assertNotNull(LabelDAO.findById(labelId));
      assertNotNull(TransformerDAO.findById(transformerId));
   }

   private static JsonNode load(String resource) throws IOException {
      try (InputStream stream = SchemaServiceTest.class.getResourceAsStream(resource)) {
         return Util.OBJECT_MAPPER.reader().readValue(stream, JsonNode.class);
      }
   }

   @org.junit.jupiter.api.Test
   public void testImportWithTransformers() throws JsonProcessingException {
      Path p = new File(getClass().getClassLoader().getResource(".").getPath()).toPath();
      p = p.getParent().getParent().getParent().resolve("infra-legacy/example-data/");
      String s1 = readFile(p.resolve("quarkus_sb_schema.json").toFile());

      jsonRequest().body(s1).post("/api/schema/import").then().statusCode(204);

      SchemaDAO s = SchemaDAO.find("uri", "urn:quarkus-sb-compare:0.1").firstResult();
      assertNotNull(s);
      assertEquals(30, LabelDAO.find("schema.id", s.id).count());
      assertEquals(1, TransformerDAO.find("schema.id", s.id).count());
      TransformerDAO t = TransformerDAO.find("schema.id", s.id).firstResult();
      assertEquals(12, t.extractors.size());

      String s2 = readFile(p.resolve("quarkus_sb_schema_empty.json").toFile());
      // this should fail since the uri is identical to an already imported test
      jsonRequest().body(s2).post("/api/schema/import").then().statusCode(400);
   }


   @org.junit.jupiter.api.Test
   public void testExportImportWithWipe() {
      testExportImport(true);
   }

   @org.junit.jupiter.api.Test
   public void testExportImportWithoutWipe() {
      testExportImport(false);
   }

   private void testExportImport(boolean wipe) {
      Schema schema = createSchema("Test schema", "urn:xxx:1.0");
      addLabel(schema, "foo", null, new Extractor("foo", "$.foo", false));
      addLabel(schema, "bar", "({bar, goo}) => ({ bar, goo })",
            new Extractor("bar", "$.bar", true), new Extractor("goo", "$.goo", false));

      createTransformer("Blabla", schema, "({x, y}) => ({ z: 1 })",
            new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));

      String exportJson = jsonRequest().get("/api/schema/" + schema.id + "/export").then().statusCode(200).extract().body().asString();
      HashMap<String, List<JsonNode>> db = dumpDatabaseContents();

      if (wipe) {
         deleteSchema(schema);
         TestUtil.eventually(() -> {
            Util.withTx(tm, () -> {
               em.clear();
               assertEquals(0, SchemaDAO.count());
               assertEquals(0, LabelDAO.count());
               assertEquals(0, TransformerDAO.count());
               return null;
            });
         });
      }

      jsonRequest().body(exportJson).post("/api/schema/import").then().statusCode(204);
      SchemaDAO s = SchemaDAO.find("uri", "urn:xxx:1.0").firstResult();
      assertNotNull(s);
      assertEquals(2, LabelDAO.find("schema.id", s.id).count());
      assertEquals(1, TransformerDAO.find("schema.id", s.id).count());
   }

   @org.junit.jupiter.api.Test
   public void testFindUsages(TestInfo info) throws InterruptedException  {
      Test test = createTest(createExampleTest("nofilter"));
      createComparisonSchema();
      uploadExampleRuns(test);

      TableReportConfig config = newExampleTableReportConfig(test);

      TableReport report = jsonRequest().body(config).post("/api/report/table/config")
              .then().statusCode(200).extract().body().as(TableReport.class);

      assertNotEquals(0, report.data.size());

      List<SchemaService.LabelLocation> usages = jsonRequest().get("/api/schema/findUsages?label=".concat("category"))
                .then().statusCode(200).extract().body().as(List.class);

      assertNotNull(usages);

   }


}
