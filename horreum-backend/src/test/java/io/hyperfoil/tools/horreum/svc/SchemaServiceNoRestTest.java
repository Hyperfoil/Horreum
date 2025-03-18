package io.hyperfoil.tools.horreum.svc;

import static io.hyperfoil.tools.horreum.svc.BaseServiceNoRestTest.DEFAULT_USER;
import static io.hyperfoil.tools.horreum.svc.BaseServiceNoRestTest.FOO_TEAM;
import static io.hyperfoil.tools.horreum.svc.BaseServiceNoRestTest.FOO_TESTER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.hibernate.exception.SQLGrammarException;
import org.junit.jupiter.api.Disabled;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.*;
import io.hyperfoil.tools.horreum.api.services.SchemaService;
import io.hyperfoil.tools.horreum.entity.data.LabelDAO;
import io.hyperfoil.tools.horreum.entity.data.SchemaDAO;
import io.hyperfoil.tools.horreum.entity.data.TransformerDAO;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@TestProfile(HorreumTestProfile.class)
@TestTransaction
@TestSecurity(user = DEFAULT_USER, roles = { Roles.TESTER, Roles.VIEWER, FOO_TEAM, FOO_TESTER })
class SchemaServiceNoRestTest extends BaseServiceNoRestTest {

    @Inject
    SchemaService schemaService;

    @Inject
    ObjectMapper objectMapper;

    @org.junit.jupiter.api.Test
    void testCreateSchema() {
        String schemaUri = "urn:dummy:schema";
        Schema schema = createSampleSchema("Dummy schema", schemaUri, FOO_TEAM);

        // create the schema
        int id = schemaService.add(schema);
        assertTrue(id > 0);

        SchemaDAO savedSchema = SchemaDAO.findById(id);
        assertEquals(schema.name, savedSchema.name);
        assertEquals(schema.uri, savedSchema.uri);
        assertEquals(schema.owner, savedSchema.owner);
    }

    @TestSecurity(user = DEFAULT_USER)
    @org.junit.jupiter.api.Test
    void testCreateSchemaForbidden() {
        String schemaUri = "urn:dummy:schema";
        Schema schema = createSampleSchema("Dummy schema", schemaUri, FOO_TEAM);

        assertThrows(ForbiddenException.class, () -> schemaService.add(schema));
    }

    @TestSecurity()
    @org.junit.jupiter.api.Test
    void testCreateSchemaUnauthorized() {
        String schemaUri = "urn:dummy:schema";
        Schema schema = createSampleSchema("Dummy schema", schemaUri, FOO_TEAM);

        assertThrows(UnauthorizedException.class, () -> schemaService.add(schema));
    }

    @TestSecurity(user = DEFAULT_USER, roles = { Roles.TESTER })
    @org.junit.jupiter.api.Test
    void testCreateSchemaWithNotExistingOwner() {
        String schemaUri = "urn:dummy:schema";
        Schema schema = createSampleSchema("Dummy schema", schemaUri, FOO_TEAM);

        assertThrows(SQLGrammarException.class, () -> schemaService.add(schema));
    }

    @org.junit.jupiter.api.Test
    void testCreateSchemaWithInvalidUri() {
        String schemaUri = "dummy:schema";
        Schema schema = createSampleSchema("Dummy schema", schemaUri, FOO_TEAM);

        ServiceException thrown = assertThrows(ServiceException.class, () -> schemaService.add(schema));
        assertEquals("Please use URI starting with one of these schemes: [urn, uri, http, https, ftp, file, jar]",
                thrown.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testUpdateSchema() {
        String schemaUri = "urn:dummy:schema";
        Schema schema = createSampleSchema("Dummy schema", schemaUri, FOO_TEAM);

        // create the schema
        int id = schemaService.add(schema);
        assertTrue(id > 0);

        // update the schema
        schema.id = id;
        schema.name = "urn:dummy:schema:v1";

        int afterUpdateId = schemaService.update(schema);
        assertEquals(id, afterUpdateId);

        SchemaDAO savedSchema = SchemaDAO.findById(id);
        assertEquals(schema.name, savedSchema.name);
        assertEquals(schema.uri, savedSchema.uri);
        assertEquals(schema.owner, savedSchema.owner);
    }

    @org.junit.jupiter.api.Test
    void testUpdateNotFoundSchema() {
        String schemaUri = "urn:dummy:schema";
        Schema schema = createSampleSchema("Dummy schema", schemaUri, FOO_TEAM);
        // set invalid id > 0
        schema.id = 9999;

        // try to update a not existing schema
        ServiceException thrown = assertThrows(ServiceException.class, () -> schemaService.update(schema));
        assertEquals("Missing schema id or schema with id 9999 does not exist", thrown.getMessage());
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testCreateSchemaWithExistingName() {
        String schemaUri = "urn:dummy:schema";
        Schema schema = createSampleSchema("Dummy schema", schemaUri, FOO_TEAM);

        // create the schema
        int id = schemaService.add(schema);
        assertTrue(id > 0);

        // try to create another schema with same name
        ServiceException thrown = assertThrows(ServiceException.class, () -> schemaService.add(schema));
        assertEquals("Name already used", thrown.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testCreateSchemaWithExistingUri() {
        String schemaUri = "urn:dummy:schema";
        Schema schema = createSampleSchema("Dummy schema", schemaUri, FOO_TEAM);

        // create the schema
        int id = schemaService.add(schema);
        assertTrue(id > 0);

        // try to create another schema with same uri
        schema.name = "urn:different-name";
        ServiceException thrown = assertThrows(ServiceException.class, () -> schemaService.add(schema));
        assertEquals("URI already used", thrown.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testDeleteSchema() {
        String schemaUri = "urn:dummy:schema";
        Schema schema = createSampleSchema("Dummy schema", schemaUri, FOO_TEAM);

        // create the schema
        int id = schemaService.add(schema);
        assertTrue(id > 0);
        assertEquals(1, SchemaDAO.count());

        // delete schema
        schemaService.delete(id);

        assertEquals(0, SchemaDAO.count());
    }

    @org.junit.jupiter.api.Test
    void testDeleteSchemaNotFound() {
        ServiceException thrown = assertThrows(ServiceException.class, () -> schemaService.delete(999));
        assertEquals("Schema not found", thrown.getMessage());
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testIdByUri() {
        String schemaUri = "urn:dummy:schema";
        // create the schema
        Schema s = createSchema("Dummy schema", schemaUri);

        int retrievedId = schemaService.idByUri(schemaUri);
        assertEquals(s.id, retrievedId);
    }

    @org.junit.jupiter.api.Test
    void testIdByUriNotFound() {
        String notExistingUri = "urn:not-existing:schema";
        ServiceException thrown = assertThrows(ServiceException.class, () -> schemaService.idByUri(notExistingUri));
        assertEquals("Schema with given uri not found: " + notExistingUri, thrown.getMessage());
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testListSchemas() {
        // create some schemas
        createSchema("Ghi", "urn:schema:1");
        createSchema("Abc", "urn:schema:2");
        createSchema("Def", "urn:schema:3");

        // by default order by name and with descending direction
        SchemaService.SchemaQueryResult res = schemaService.list(null, null, null, null, null, null);
        assertEquals(3, res.schemas.size());
        assertEquals(3, res.count);
        assertEquals("Ghi", res.schemas.get(0).name);

        // order by uri with descending direction
        res = schemaService.list(null, null, null, "uri", null, null);
        assertEquals(3, res.schemas.size());
        assertEquals(3, res.count);
        assertEquals("Def", res.schemas.get(0).name);

        // order by uri with ascending direction
        res = schemaService.list(null, null, null, "uri", SortDirection.Ascending, null);
        assertEquals(3, res.schemas.size());
        assertEquals(3, res.count);
        assertEquals("Ghi", res.schemas.get(0).name);

        // order by name with ascending direction
        res = schemaService.list(null, null, null, "name", SortDirection.Ascending, null);
        assertEquals(3, res.schemas.size());
        assertEquals(3, res.count);
        assertEquals("Abc", res.schemas.get(0).name);

        // limit the list to 2 results
        res = schemaService.list(Roles.MY_ROLES, 2, 0, null, null, null);
        assertEquals(2, res.schemas.size());
        // total number of records
        assertEquals(3, res.count);
    }

    @org.junit.jupiter.api.Test
    void testListSchemasByName() {
        // create some schemas
        createSchema("Ghi", "urn:schema:1");
        createSchema("Abc", "urn:schema:2");
        createSchema("Def", "urn:schema:3");
        createSchema("Ghi2", "urn:schema:4");
        createSchema("Def2", "urn:schema:5");
        createSchema("Def3", "urn:schema:6");

        // exact match case-insensitive
        SchemaService.SchemaQueryResult res = schemaService.list(null, null, null, null, null, "ghi2");
        assertEquals(1, res.schemas.size());
        assertEquals(6, res.count);
        assertEquals("Ghi2", res.schemas.get(0).name);

        // partial match case-insensitive
        res = schemaService.list(null, null, null, "name", SortDirection.Ascending, "ghi");
        assertEquals(2, res.schemas.size());
        assertEquals(6, res.count);
        assertEquals("Ghi", res.schemas.get(0).name);
        assertEquals("Ghi2", res.schemas.get(1).name);

        // partial match case-insensitive with paging 1
        res = schemaService.list(null, 1, 0, "name", SortDirection.Ascending, "ghi");
        assertEquals(1, res.schemas.size());
        assertEquals(6, res.count);
        assertEquals("Ghi", res.schemas.get(0).name);

        // partial match case-insensitive with paging 2
        res = schemaService.list(null, 1, 1, "name", SortDirection.Ascending, "ghi");
        assertEquals(1, res.schemas.size());
        assertEquals(6, res.count);
        assertEquals("Ghi2", res.schemas.get(0).name);

        // partial match case-insensitive with paging 2
        res = schemaService.list(Roles.MY_ROLES, 2, 1, "name", SortDirection.Ascending, "ef");
        assertEquals(1, res.schemas.size());
        assertEquals(6, res.count);
        assertEquals("Def3", res.schemas.get(0).name);
    }

    @org.junit.jupiter.api.Test
    void testSchemaDescriptors() {
        // create some schemas
        List<Integer> ids = List.of(
                createSchema("Ghi", "urn:schema:1").id,
                createSchema("Abc", "urn:schema:2").id,
                createSchema("Def", "urn:schema:3").id);

        List<SchemaService.SchemaDescriptor> descriptors = schemaService.descriptors(ids);
        assertEquals(ids.size(), descriptors.size());
    }

    @org.junit.jupiter.api.Test
    void testUpdateSchemaAccess() {
        Schema s = createSchema("dummy", "urn:dummy:schema");
        SchemaDAO savedSchema = SchemaDAO.findById(s.id);
        assertEquals(FOO_TEAM, savedSchema.owner);
        assertEquals(Access.PUBLIC, savedSchema.access);

        schemaService.updateAccess(s.id, FOO_TEAM, Access.PROTECTED);

        savedSchema = SchemaDAO.findById(s.id);
        assertEquals(Access.PROTECTED, savedSchema.access);
    }

    @org.junit.jupiter.api.Test
    void testUpdateSchemaAccessWithWrongOwner() {
        Schema s = createSchema("dummy", "urn:dummy:schema");
        SchemaDAO savedSchema = SchemaDAO.findById(s.id);
        assertEquals(FOO_TEAM, savedSchema.owner);
        assertEquals(Access.PUBLIC, savedSchema.access);

        ServiceException thrown = assertThrows(ServiceException.class,
                () -> schemaService.updateAccess(s.id, FOO_TESTER, Access.PROTECTED));
        assertEquals("Access change failed (missing permissions?)", thrown.getMessage());
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testUpdateSchemaAccessWithInvalidSchema() {
        ServiceException thrown = assertThrows(ServiceException.class,
                () -> schemaService.updateAccess(999, FOO_TESTER, Access.PROTECTED));
        assertEquals("Schema not found", thrown.getMessage());
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testCreateSchemaTransformer() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        Transformer t = createSampleTransformer("Blabla", s, null, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));

        int id = schemaService.addTransformer(s.id, t);

        TransformerDAO transformer = TransformerDAO.findById(id);
        assertNotNull(transformer);
        assertEquals(2, transformer.extractors.size());
    }

    @org.junit.jupiter.api.Test
    void testCreateMultipleSchemaTransformers() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        Transformer t = createSampleTransformer("Blabla", s, null, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));

        int id1 = schemaService.addTransformer(s.id, t);
        // create another transformer with different name and additional extractor
        t.name = "Albalb";
        t.extractors.add(new Extractor("z", "$.z", false));
        int id2 = schemaService.addTransformer(s.id, t);

        TransformerDAO transformer1 = TransformerDAO.findById(id1);
        assertNotNull(transformer1);
        assertEquals(2, transformer1.extractors.size());

        TransformerDAO transformer2 = TransformerDAO.findById(id2);
        assertNotNull(transformer2);
        assertEquals(3, transformer2.extractors.size());

        // fetch newly created transformers
        List<Transformer> transformers = schemaService.listTransformers(s.id);
        assertEquals(2, transformers.size());
        // they are ordered by name
        assertEquals(id2, transformers.get(0).id);
    }

    @org.junit.jupiter.api.Test
    void testUpdateSchemaTransformer() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        Transformer t = createSampleTransformer("Blabla", s, null, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));

        int id = schemaService.addTransformer(s.id, t);

        TransformerDAO transformer = TransformerDAO.findById(id);
        assertNotNull(transformer);
        assertEquals(2, transformer.extractors.size());

        t.id = id;
        t.extractors.add(new Extractor("z", "$.z", false));

        id = schemaService.updateTransformer(s.id, t);
        transformer = TransformerDAO.findById(id);
        assertNotNull(transformer);
        assertEquals(3, transformer.extractors.size());
    }

    @org.junit.jupiter.api.Test
    void testCreateSchemaTransformerWithWrongOwner() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        Transformer t = createSampleTransformer("Blabla", s, "another-team", "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));

        ServiceException thrown = assertThrows(ServiceException.class, () -> schemaService.addTransformer(s.id, t));
        assertEquals("This user is not a member of team another-team", thrown.getMessage());
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testCreateSchemaTransformerWithBlankName() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        // empty name
        Transformer t = createSampleTransformer("", s, null, "({x, y}) => ({ z: 1 })");

        ServiceException thrown = assertThrows(ServiceException.class, () -> schemaService.addTransformer(s.id, t));
        assertEquals("Transformer must have a name!", thrown.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());

        // null name
        t.name = null;
        thrown = assertThrows(ServiceException.class, () -> schemaService.addTransformer(s.id, t));
        assertEquals("Transformer must have a name!", thrown.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testUpdateSchemaTransformerOfDifferentSchema() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        Transformer t = createSampleTransformer("Blabla", s, null, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));

        int id = schemaService.addTransformer(s.id, t);

        TransformerDAO transformer = TransformerDAO.findById(id);
        assertNotNull(transformer);
        assertEquals(2, transformer.extractors.size());

        t.id = id;
        t.extractors.add(new Extractor("z", "$.z", false));

        ServiceException thrown = assertThrows(ServiceException.class, () -> schemaService.updateTransformer(999, t));
        assertTrue(
                thrown.getMessage().contains(String.format("Transformer id=%d, name=%s belongs to a different schema: %d(%s)",
                        t.id, t.name, s.id, s.uri)));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testDeleteSchemaTransformer() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        Transformer t = createSampleTransformer("Blabla", s, null, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));

        int id = schemaService.addTransformer(s.id, t);
        assertEquals(1, TransformerDAO.count());

        schemaService.deleteTransformer(s.id, id);
        assertEquals(0, TransformerDAO.count());
    }

    @org.junit.jupiter.api.Test
    void testDeleteSchemaTransformerWithFailure() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        Transformer t = createSampleTransformer("Blabla", s, null, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));

        int id = schemaService.addTransformer(s.id, t);
        assertEquals(1, TransformerDAO.count());

        // wrong transformer id, not found
        ServiceException thrown = assertThrows(ServiceException.class, () -> schemaService.deleteTransformer(s.id, 999));
        assertEquals("Transformer 999 not found", thrown.getMessage());
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), thrown.getResponse().getStatus());

        // wrong schema id
        thrown = assertThrows(ServiceException.class, () -> schemaService.deleteTransformer(999, id));
        assertEquals(String.format("Transformer %s does not belong to schema 999", id), thrown.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
    }

    @Disabled("To be implemented")
    @org.junit.jupiter.api.Test
    void testDeleteSchemaTransformerStillReferencedInTests() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        Transformer t = createSampleTransformer("Blabla", s, null, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));

        int id = schemaService.addTransformer(s.id, t);
        assertEquals(1, TransformerDAO.count());

        // TODO: create a Test that references this transformer and try to remove it
        // check badRequest is thrown with "This transformer is still referenced in some tests: ..." msg
    }

    @org.junit.jupiter.api.Test
    void testCreateSchemaLabel() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        Label l = createSampleLabel("Blabla", s, null, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));

        int id = schemaService.addLabel(s.id, l);
        assertEquals(1, LabelDAO.count());
        LabelDAO label = LabelDAO.findById(id);
        assertNotNull(label);
        assertEquals("Blabla", label.name);
        assertEquals(2, label.extractors.size());
    }

    @org.junit.jupiter.api.Test
    void testCreateSchemaLabelWithBlankName() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        Label l = createSampleLabel("", s, null, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));

        // empty name
        ServiceException thrown = assertThrows(ServiceException.class, () -> schemaService.addLabel(s.id, l));
        assertEquals("Label must have a non-blank name", thrown.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());

        // null name
        l.name = null;
        thrown = assertThrows(ServiceException.class, () -> schemaService.addLabel(s.id, l));
        assertEquals("Label must have a non-blank name", thrown.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testCreateSchemaLabelWithNotExistingId() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        Label l = createSampleLabel("my-awesome-label", s, null, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));
        l.id = 999;

        // adding a label ensure the id is cleared if not existing
        int id = schemaService.addLabel(s.id, l);
        assertEquals(1, LabelDAO.count());
        LabelDAO label = LabelDAO.findById(id);
        assertNotNull(label);
        assertEquals("my-awesome-label", label.name);
        assertEquals(2, label.extractors.size());
        assertNotEquals(999, label.id);
    }

    @org.junit.jupiter.api.Test
    void testCreateSchemaLabelWithFailures() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        Label l = createSampleLabel("Blabla", s, null, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));

        // null label dto
        ServiceException thrown = assertThrows(ServiceException.class, () -> schemaService.addLabel(s.id, null));
        assertEquals("No label?", thrown.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());

        // not existing schema
        thrown = assertThrows(ServiceException.class, () -> schemaService.addLabel(999, l));
        assertEquals("Schema 999 not found", thrown.getMessage());
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testUpdateSchemaLabel() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        Label l = createSampleLabel("Blabla", s, null, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));

        int id = schemaService.addLabel(s.id, l);
        assertEquals(1, LabelDAO.count());

        // update the label
        l.id = id;
        l.name = "AnotherName";
        l.extractors.add(new Extractor("z", "$.z", false));
        int afterUpdateId = schemaService.updateLabel(s.id, l);
        assertEquals(id, afterUpdateId);

        assertEquals(1, LabelDAO.count());
        LabelDAO label = LabelDAO.findById(id);
        assertNotNull(label);
        assertEquals(3, label.extractors.size());
        assertEquals("AnotherName", label.name);
    }

    @org.junit.jupiter.api.Test
    void testUpdateSchemaLabelWrongSchemaId() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        Label l = createSampleLabel("Blabla", s, null, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));

        int id = schemaService.addLabel(s.id, l);
        assertEquals(1, LabelDAO.count());

        // update the label passing the wrong schema id
        l.id = id;
        ServiceException thrown = assertThrows(ServiceException.class, () -> schemaService.updateLabel(999, l));
        assertEquals(String.format("Label id=%d, name=%s belongs to a different schema: %d(%s)",
                l.id, l.name, s.id, s.uri), thrown.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testAddExistingSchemaLabel() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        Label l = createSampleLabel("Blabla", s, null, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));

        int id = schemaService.addLabel(s.id, l);
        assertEquals(1, LabelDAO.count());

        // update the label passing the wrong schema id
        l.id = id;
        ServiceException thrown = assertThrows(ServiceException.class, () -> schemaService.addLabel(999, l));
        assertEquals(String.format("Label with id %d already exists", l.id), thrown.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testUpdateSchemaLabelNameWhenAlreadyExisting() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        Label l = createSampleLabel("Blabla", s, null, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));
        Label another = createSampleLabel("AnotherLabel", s, null, "({x, y}) => ({ z: 1 })");

        int id = schemaService.addLabel(s.id, l);
        schemaService.addLabel(s.id, another);
        assertEquals(2, LabelDAO.count());

        // update the label
        l.id = id;
        l.name = "AnotherLabel";
        ServiceException thrown = assertThrows(ServiceException.class, () -> schemaService.updateLabel(s.id, l));
        assertEquals(String.format("There is an existing label with the same name (%s) in this " +
                "schema; please choose different name.", l.name), thrown.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testDeleteSchemaLabel() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        Label l = createSampleLabel("Blabla", s, null, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));

        int id = schemaService.addLabel(s.id, l);
        assertEquals(1, LabelDAO.count());

        schemaService.deleteLabel(s.id, id);
        assertEquals(0, LabelDAO.count());
    }

    @org.junit.jupiter.api.Test
    void testDeleteSchemaLabelWithFailures() {
        Schema s = createSchema("dummy", "urn:dummy:schema");

        Label l = createSampleLabel("Blabla", s, null, "({x, y}) => ({ z: 1 })",
                new Extractor("x", "$.x", true), new Extractor("y", "$.y", false));

        int id = schemaService.addLabel(s.id, l);
        assertEquals(1, LabelDAO.count());

        ServiceException thrown = assertThrows(ServiceException.class, () -> schemaService.deleteLabel(s.id, 999));
        assertEquals(String.format("Label %d not found", 999), thrown.getMessage());
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), thrown.getResponse().getStatus());

        thrown = assertThrows(ServiceException.class, () -> schemaService.deleteLabel(999, id));
        assertEquals(String.format("Label %d does not belong to schema %d", id, 999), thrown.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testImportSchemaWithValidStructure() throws JsonProcessingException {
        String schemaImport = """
                {
                  "labels" : [ {
                    "name" : "kb_report_results_podLatencyQuantilesMeasurement_quantiles_Ready_P99",
                    "filtering" : true,
                    "metrics" : true,
                    "schemaId" : "221",
                    "access" : "PUBLIC",
                    "owner" : "TEAM_NAME",
                    "extractors" : [ {
                      "name" : "P99",
                      "jsonpath" : "$.results.podLatencyQuantilesMeasurement.quantiles.Ready.P99",
                      "isarray" : false
                    } ]
                  } ],
                  "transformers": [],
                  "id": 221,
                  "uri": "urn:kube-burner-report:0.1",
                  "name": "kube-burner-report",
                  "description": "Kube Burner test for the report variant of results",
                  "schema": {
                    "$id": "urn:kube-burner-report:0.2",
                    "type": "object",
                    "$schema": "http://json-schema.org/draft-07/schema#"
                  },
                  "access": "PUBLIC",
                  "owner": "TEAM_NAME"
                }              """;

        ObjectNode schemaJson = (ObjectNode) objectMapper.readTree(schemaImport.replaceAll("TEAM_NAME", FOO_TEAM));
        SchemaExport schemaExport = objectMapper.readValue(schemaJson.toString(), SchemaExport.class);
        schemaService.importSchema(schemaExport);

    }

    // utility to create a schema in the db, tested with testCreateSchema
    private Schema createSchema(String name, String uri) {
        Schema schema = createSampleSchema(name, uri, FOO_TEAM);
        schema.id = schemaService.add(schema);
        return schema;
    }
}
