package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.persistence.Tuple;

import org.junit.jupiter.api.Assertions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.Extractor;
import io.hyperfoil.tools.horreum.api.data.Label;
import io.hyperfoil.tools.horreum.api.data.Schema;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.api.data.Transformer;
import io.hyperfoil.tools.horreum.api.report.TableReport;
import io.hyperfoil.tools.horreum.api.report.TableReportConfig;
import io.hyperfoil.tools.horreum.api.services.SchemaService;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.entity.data.LabelDAO;
import io.hyperfoil.tools.horreum.entity.data.SchemaDAO;
import io.hyperfoil.tools.horreum.entity.data.TransformerDAO;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.hyperfoil.tools.horreum.test.TestUtil;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.common.mapper.TypeRef;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
class SchemaServiceTest extends BaseServiceTest {

    @Inject
    ServiceMediator serviceMediator;

    @org.junit.jupiter.api.Test
    void testGetSchema() {
        String schemaUri = "urn:dummy:schema";
        Schema schema = createSchema("Dummy schema", schemaUri);
        assertNotNull(schema);
        assertTrue(schema.id > 0);

        Schema retrieved = getSchema(schema.id, null);
        assertEquals(schema.id, retrieved.id);
        assertEquals(schema.uri, retrieved.uri);
    }

    @org.junit.jupiter.api.Test
    void testGetSchemaNotFound() {
        jsonRequest().get("/api/schema/9999")
                .then()
                .statusCode(404);
    }

    @org.junit.jupiter.api.Test
    void testGetSchemaByUri() {
        String schemaUri = "urn:dummy:schema";
        Schema schema = createSchema("Dummy schema", schemaUri);

        int schemaId = jsonRequest().get("/api/schema/idByUri/" + schemaUri)
                .then()
                .statusCode(200)
                .extract()
                .as(Integer.class);

        assertEquals(schema.id, schemaId);
    }

    @org.junit.jupiter.api.Test
    void testGetSchemaByUriNotFound() {
        jsonRequest().get("/api/schema/idByUri/urn:unknown:schema")
                .then()
                .statusCode(404);
    }

    @org.junit.jupiter.api.Test
    void testCreateSchemaWithInvalidId() {
        Schema schema = new Schema();
        schema.owner = TESTER_ROLES[0];
        schema.name = "InvalidSchema";
        schema.uri = "urn:invalid-id:schema";
        schema.id = 9999; // does not exist

        jsonRequest().body(schema).post("/api/schema")
                .then()
                .statusCode(400);
    }

    @org.junit.jupiter.api.Test
    void testListSchemas() {
        // create some schemas
        createSchema("Ghi", "urn:schema:1");
        createSchema("Abc", "urn:schema:2");
        createSchema("Def", "urn:schema:3");

        // by default order by name and with descending direction
        SchemaService.SchemaQueryResult res = listSchemas(null, null, null, null, null, null);
        assertEquals(3, res.schemas.size());
        assertEquals(3, res.count);
        assertEquals("Ghi", res.schemas.get(0).name);

        // order by uri with descending direction
        res = listSchemas(null, null, null, null, "uri", null);
        assertEquals(3, res.schemas.size());
        assertEquals(3, res.count);
        assertEquals("Def", res.schemas.get(0).name);

        // order by uri with ascending direction
        res = listSchemas(null, null, null, null, "uri", SortDirection.Ascending);
        assertEquals(3, res.schemas.size());
        assertEquals(3, res.count);
        assertEquals("Ghi", res.schemas.get(0).name);

        // order by name with ascending direction
        res = listSchemas(null, null, null, null, "name", SortDirection.Ascending);
        assertEquals(3, res.schemas.size());
        assertEquals(3, res.count);
        assertEquals("Abc", res.schemas.get(0).name);

        // limit the list to 2 results
        res = listSchemas(null, null, 2, 0, null, null);
        assertEquals(2, res.schemas.size());
        // total number of records
        assertEquals(3, res.count);
    }

    @org.junit.jupiter.api.Test
    void testListSchemasWithDifferentRoles() {
        // create some schemas
        createSchema("Ghi", "urn:schema:ghi");
        createSchema("Abc", "urn:schema:abc");
        createSchema("Def", "urn:schema:def");
        createSchema("jkl", "urn:schema:jkl");

        // by default order by name and with ascending direction
        SchemaService.SchemaQueryResult res = listSchemas(null, null, null, null, null, null);
        assertEquals(4, res.schemas.size());
        assertEquals(4, res.count);

        res = listSchemas(getAdminToken(), Roles.MY_ROLES, null, null, null, null);
        assertEquals(0, res.schemas.size());
        assertEquals(4, res.count);

        res = listSchemas(getAdminToken(), Roles.ALL_ROLES, null, null, null, null);
        assertEquals(4, res.schemas.size());
        assertEquals(4, res.count);
    }

    @org.junit.jupiter.api.Test
    void testUpdateSchemaAccess() {
        Schema s = createSchema("dummy", "urn:dummy:schema");
        assertEquals(TESTER_ROLES[0], s.owner);
        assertEquals(Access.PUBLIC, s.access);

        jsonRequest()
                .auth().oauth2(getTesterToken())
                // access=1 => PROTECTED
                .post("/api/schema/" + s.id + "/updateAccess?owner=" + TESTER_ROLES[0] + "&access=1")
                .then()
                .statusCode(204);

        s = getSchema(s.id, null);
        assertNotNull(s);
        assertEquals(TESTER_ROLES[0], s.owner);
        assertEquals(Access.PROTECTED, s.access);
    }

    @org.junit.jupiter.api.Test
    void testValidateRun() throws IOException, InterruptedException {
        JsonNode allowAny = load("/allow-any.json");
        Schema allowAnySchema = createSchema("any", allowAny.path("$id").asText(), allowAny);
        JsonNode allowNone = load("/allow-none.json");
        Schema allowNoneSchema = createSchema("none", allowNone.path("$id").asText(), allowNone);

        Test test = createTest(createExampleTest("schemaTest"));
        BlockingQueue<Schema.ValidationEvent> runValidations = serviceMediator.getEventQueue(AsyncEventChannels.RUN_VALIDATED,
                test.id);
        BlockingQueue<Schema.ValidationEvent> datasetValidations = serviceMediator
                .getEventQueue(AsyncEventChannels.DATASET_VALIDATED, test.id);

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

        assertEquals(4, em.createNativeQuery("SELECT COUNT(*)::int FROM run_validationerrors").getSingleResult());
        assertEquals(4, em.createNativeQuery("SELECT COUNT(*)::int FROM dataset_validationerrors").getSingleResult());
    }

    @org.junit.jupiter.api.Test
    void testEditSchema() {
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
                .then().statusCode(200).extract().body().as(new TypeRef<>() {
                });
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
    void testImportWithTransformers() throws JsonProcessingException {
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
    void testExportImportWithWipe() {
        testExportImport(true);
    }

    @org.junit.jupiter.api.Test
    void testExportImportWithoutWipe() {
        testExportImport(false);
    }

    private void testExportImport(boolean wipe) {
        Schema schema = createSchema("Test schema", "urn:xxx:1.0");
        addLabel(schema, "foo", null, new Extractor("foo", "$.foo", false));
        addLabel(schema, "bar", "({bar, goo}) => ({ bar, goo })",
                new Extractor("bar", "$.bar", true), new Extractor("goo", "$.goo", false));

        createTransformer("Blabla", schema, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));

        String exportJson = jsonRequest().get("/api/schema/" + schema.id + "/export").then().statusCode(200).extract().body()
                .asString();
        HashMap<String, List<JsonNode>> db = dumpDatabaseContents();

        if (wipe) {
            deleteSchema(schema);
            TestUtil.eventually(() -> {
                Util.withTx(tm, () -> {
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
    void testUpdateSchemaTransformer() {
        Schema schema = createSchema("Dummy schema", "urn:xxx:1.0");

        Transformer t = createTransformer("Blabla", schema, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));
        assertNotNull(t);
        assertEquals(2, t.extractors.size());

        // add another extractor
        t.extractors.add(new Extractor("z", "$.z", false));
        Integer id = jsonRequest().body(t).post("/api/schema/" + schema.id + "/transformers")
                .then().statusCode(200).extract().as(Integer.class);

        TransformerDAO transformer = TransformerDAO.findById(id);
        assertNotNull(transformer);
        assertEquals(3, transformer.extractors.size());
    }

    @org.junit.jupiter.api.Test
    void testUpdateSchemaTransformerWithoutPrivileges() {
        Schema schema = createSchema("Dummy schema", "urn:xxx:1.0");

        Transformer t = createTransformer("Blabla", schema, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));
        assertNotNull(t);
        assertEquals(2, t.extractors.size());

        // add another extractor
        t.extractors.add(new Extractor("z", "$.z", false));
        jsonRequest()
                .auth().oauth2(getAdminToken())
                .body(t).post("/api/schema/" + schema.id + "/transformers")
                .then().statusCode(403);
    }

    @org.junit.jupiter.api.Test
    void testUpdateSchemaTransformerOnWrongSchema() {
        Schema schema1 = createSchema("Dummy schema 1", "urn:xxx:1.0");
        Schema schema2 = createSchema("Dummy schema 2", "urn:xxx:2.0");

        Transformer t = createTransformer("Blabla", schema1, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));
        assertNotNull(t);
        assertEquals(2, t.extractors.size());

        // add another extractor
        t.extractors.add(new Extractor("z", "$.z", false));
        jsonRequest()
                // wrong schema id in the path
                .body(t).post("/api/schema/" + schema2.id + "/transformers")
                .then().statusCode(400);
    }

    @org.junit.jupiter.api.Test
    void testFindUsages() throws InterruptedException {
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

    @org.junit.jupiter.api.Test
    void testUpdateSchemaLabel() {
        Schema schema = createSchema("Dummy schema", "urn:xxx:1.0");

        int labelId = addLabel(schema, "foo", null, new Extractor("foo", "$.foo", false));
        assertTrue(labelId > 0);

        int labelUpdatedId = updateLabel(schema, labelId, "foo-updated", null, new Extractor("foo1", "$.foo1", false),
                new Extractor("foo2", "$.foo2", false));
        assertEquals(labelId, labelUpdatedId);

        LabelDAO label = LabelDAO.findById(labelId);
        assertNotNull(label);
        assertEquals("foo-updated", label.name);
        assertEquals(2, label.extractors.size());
    }

    @org.junit.jupiter.api.Test
    void testDeleteLabel() {
        Schema schema = createSchema("Dummy schema", "urn:xxx:1.0");

        int labelId = addLabel(schema, "foo", null, new Extractor("foo", "$.foo", false));
        assertTrue(labelId > 0);

        deleteLabel(schema, labelId);

        LabelDAO label = LabelDAO.findById(labelId);
        assertNull(label);
    }

    @org.junit.jupiter.api.Test
    void testDeleteLabelOnDifferentSchema() {
        Schema schema1 = createSchema("Dummy schema 1", "urn:xxx:1.0");
        Schema schema2 = createSchema("Dummy schema 2", "urn:xxx:2.0");

        int labelId = addLabel(schema1, "foo", null, new Extractor("foo", "$.foo", false));
        assertTrue(labelId > 0);

        jsonRequest().delete("/api/schema/" + schema2.id + "/labels/" + labelId).then().statusCode(400);
    }

    @org.junit.jupiter.api.Test
    void testDeleteNotExistingLabel() {
        Schema schema = createSchema("Dummy schema", "urn:xxx:1.0");
        jsonRequest().delete("/api/schema/" + schema.id + "/labels/-1").then().statusCode(404);
    }

    @org.junit.jupiter.api.Test
    void testCreateSchemaAfterRunWithArrayData() {
        String schemaUri = "urn:unknown:schema";
        Test test = createTest(createExampleTest("dummy-test"));

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.addObject().put("$schema", schemaUri).put("foo", "bar");
        data.addObject().put("$schema", schemaUri).put("foo", "bar");
        int runId = uploadRun(data.toString(), test.name);
        assertTrue(runId > 0);

        // no validation errors
        assertEquals(0, em.createNativeQuery("SELECT COUNT(*)::int FROM run_validationerrors").getSingleResult());
        assertEquals(0, em.createNativeQuery("SELECT COUNT(*)::int FROM dataset_validationerrors").getSingleResult());

        List<?> runSchemasBefore = em.createNativeQuery("SELECT * FROM run_schemas WHERE runid = ?1").setParameter(1, runId)
                .getResultList();
        assertEquals(0, runSchemasBefore.size());

        // create the schema afterward
        Schema schema = createSchema("Unknown schema", schemaUri);
        assertNotNull(schema);
        assertTrue(schema.id > 0);

        TestUtil.eventually(() -> {
            Util.withTx(tm, () -> {
                List<?> runSchemas = em.createNativeQuery("SELECT * FROM run_schemas WHERE runid = ?1").setParameter(1, runId)
                        .getResultList();
                // two records as the run is an array of two objects, both referencing the same schema
                assertEquals(2, runSchemas.size());
                return null;
            });
        });
    }

    @org.junit.jupiter.api.Test
    void testCreateSchemaAfterRunWithMultipleSchemas() {
        String firstSchemaUri = "urn:unknown1:schema";
        String secondSchemaUri = "urn:unknown2:schema";
        Test test = createTest(createExampleTest("dummy-test"));

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.addObject().put("$schema", firstSchemaUri).put("foo", "bar");
        data.addObject().put("$schema", secondSchemaUri).put("foo", "zip");
        int runId = uploadRun(data.toString(), test.name);
        assertTrue(runId > 0);

        // no validation errors
        assertEquals(0, em.createNativeQuery("SELECT COUNT(*)::int FROM run_validationerrors").getSingleResult());
        assertEquals(0, em.createNativeQuery("SELECT COUNT(*)::int FROM dataset_validationerrors").getSingleResult());

        List<?> runSchemasBefore = em.createNativeQuery("SELECT * FROM run_schemas WHERE runid = ?1").setParameter(1, runId)
                .getResultList();
        assertEquals(0, runSchemasBefore.size());

        // create the schema 1 afterward
        Schema schema1 = createSchema("Unknown schema 1", firstSchemaUri);
        assertNotNull(schema1);
        assertTrue(schema1.id > 0);

        TestUtil.eventually(() -> {
            Util.withTx(tm, () -> {
                List<Tuple> runSchemas = em.createNativeQuery("SELECT * FROM run_schemas WHERE runid = ?1", Tuple.class)
                        .setParameter(1, runId).getResultList();
                // 1 record as the run is an array of two objects referencing different schemas and only the first one is created
                assertEquals(1, runSchemas.size());
                assertEquals(schema1.id, (int) runSchemas.get(0).get(3));
                return null;
            });
        });
    }

    @org.junit.jupiter.api.Test
    void testCreateSchemaAfterRunWithObjectData() {
        String schemaUri = "urn:unknown:schema";
        Test test = createTest(createExampleTest("dummy-test"));

        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("$schema", schemaUri).put("foo", "bar");
        int runId = uploadRun(data.toString(), test.name);
        assertTrue(runId > 0);

        // no validation errors
        assertEquals(0, em.createNativeQuery("SELECT COUNT(*)::int FROM run_validationerrors").getSingleResult());
        assertEquals(0, em.createNativeQuery("SELECT COUNT(*)::int FROM dataset_validationerrors").getSingleResult());

        List<?> runSchemasBefore = em.createNativeQuery("SELECT * FROM run_schemas WHERE runid = ?1").setParameter(1, runId)
                .getResultList();
        assertEquals(0, runSchemasBefore.size());

        // create the schema afterward
        Schema schema = createSchema("Unknown schema", schemaUri);
        assertNotNull(schema);
        assertTrue(schema.id > 0);

        TestUtil.eventually(() -> {
            Util.withTx(tm, () -> {
                List<?> runSchemas = em.createNativeQuery("SELECT * FROM run_schemas WHERE runid = ?1").setParameter(1, runId)
                        .getResultList();
                // run has single object data, thus referencing one schema
                assertEquals(1, runSchemas.size());
                return null;
            });
        });
    }

    @org.junit.jupiter.api.Test
    void testChangeUriForReferencedSchema() {
        String schemaUri = "urn:dummy:schema";
        Schema schema = createSchema("Dummy schema", schemaUri);
        assertNotNull(schema);
        assertTrue(schema.id > 0);

        Test test = createTest(createExampleTest("dummy-test"));

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.addObject().put("$schema", schemaUri).put("foo", "bar");
        data.addObject().put("$schema", schemaUri).put("foo", "bar");
        int runId = uploadRun(data.toString(), test.name);
        assertTrue(runId > 0);

        // no validation errors
        assertEquals(0, em.createNativeQuery("SELECT COUNT(*)::int FROM run_validationerrors").getSingleResult());
        assertEquals(0, em.createNativeQuery("SELECT COUNT(*)::int FROM dataset_validationerrors").getSingleResult());

        List<?> runSchemasBefore = em.createNativeQuery("SELECT * FROM run_schemas WHERE runid = ?1").setParameter(1, runId)
                .getResultList();
        assertEquals(2, runSchemasBefore.size());

        // update the schema uri afterward
        schema.uri = "urn:new-dummy:schema";
        Schema updatedSchema = addOrUpdateSchema(schema);
        assertNotNull(updatedSchema);
        assertEquals(schema.id, updatedSchema.id);

        TestUtil.eventually(() -> {
            Util.withTx(tm, () -> {
                List<?> runSchemasAfter = em.createNativeQuery("SELECT * FROM run_schemas WHERE runid = ?1")
                        .setParameter(1, runId)
                        .getResultList();
                assertEquals(0, runSchemasAfter.size());
                return null;
            });
        });
    }

    @org.junit.jupiter.api.Test
    void testDeleteSchemaAfterRun() {
        String schemaUri = "urn:dummy:schema";
        Schema schema = createSchema("Dummy schema", schemaUri);
        assertNotNull(schema);
        assertTrue(schema.id > 0);

        Test test = createTest(createExampleTest("dummy-test"));

        ArrayNode data = JsonNodeFactory.instance.arrayNode();
        data.addObject().put("$schema", schemaUri).put("foo", "bar");
        data.addObject().put("$schema", schemaUri).put("foo", "bar");
        int runId = uploadRun(data.toString(), test.name);
        assertTrue(runId > 0);

        // no validation errors
        assertEquals(0, em.createNativeQuery("SELECT COUNT(*)::int FROM run_validationerrors").getSingleResult());
        assertEquals(0, em.createNativeQuery("SELECT COUNT(*)::int FROM dataset_validationerrors").getSingleResult());

        List<?> runSchemasBefore = em.createNativeQuery("SELECT * FROM run_schemas WHERE runid = ?1").setParameter(1, runId)
                .getResultList();
        assertEquals(2, runSchemasBefore.size());
        List<?> datasetSchemasBefore = em.createNativeQuery("SELECT * FROM dataset_schemas").getResultList();
        assertEquals(2, datasetSchemasBefore.size());

        // delete the schema afterward
        deleteSchema(schema);

        assertEquals(0, SchemaDAO.count());
        List<?> runSchemasAfter = em.createNativeQuery("SELECT * FROM run_schemas WHERE runid = ?1").setParameter(1, runId)
                .getResultList();
        assertEquals(0, runSchemasAfter.size());
        List<?> datasetSchemasAfter = em.createNativeQuery("SELECT * FROM dataset_schemas").getResultList();
        assertEquals(0, datasetSchemasAfter.size());
    }

    // utility to get list of schemas
    private SchemaService.SchemaQueryResult listSchemas(String token, String roles, Integer limit, Integer page, String sort,
            SortDirection direction) {
        StringBuilder query = new StringBuilder("/api/schema/");
        if (roles != null || limit != null || page != null || sort != null || direction != null) {
            query.append("?");

            if (roles != null) {
                query.append("roles=").append(roles).append("&");
            }

            if (limit != null) {
                query.append("limit=").append(limit).append("&");
            }

            if (page != null) {
                query.append("page=").append(page).append("&");
            }

            if (sort != null) {
                query.append("sort=").append(sort).append("&");
            }

            if (direction != null) {
                query.append("direction=").append(direction);
            }
        }
        return jsonRequest()
                .auth()
                .oauth2(token == null ? getTesterToken() : token)
                .get(query.toString())
                .then()
                .statusCode(200)
                .extract()
                .as(SchemaService.SchemaQueryResult.class);
    }
}
