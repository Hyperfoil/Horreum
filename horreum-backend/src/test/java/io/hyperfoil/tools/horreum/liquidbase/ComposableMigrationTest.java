package io.hyperfoil.tools.horreum.liquidbase;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.core.HttpHeaders;

import org.hibernate.Session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.hyperfoil.tools.horreum.api.data.Extractor;
import io.hyperfoil.tools.horreum.api.data.Schema;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.api.data.Transformer;
import io.hyperfoil.tools.horreum.api.exp.LabelService;
import io.hyperfoil.tools.horreum.api.services.DatasetService;
import io.hyperfoil.tools.horreum.api.services.RunService;
import io.hyperfoil.tools.horreum.api.services.SchemaService;
import io.hyperfoil.tools.horreum.api.services.TestService;
import io.hyperfoil.tools.horreum.exp.data.ExtractorDao;
import io.hyperfoil.tools.horreum.exp.data.LabelDao;
import io.hyperfoil.tools.horreum.exp.data.LabelGroupDao;
import io.hyperfoil.tools.horreum.exp.svc.LabelServiceImpl;
import io.hyperfoil.tools.horreum.exp.svc.RunServiceImpl;
import io.hyperfoil.tools.horreum.liquibase.ComposableMigration;
import io.hyperfoil.tools.horreum.svc.BaseServiceTest;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class ComposableMigrationTest extends BaseServiceTest {

    String getMethodName() {
        return Thread.currentThread().getStackTrace()[0].getMethodName();
    }

    @Inject
    EntityManager em;

    @Inject
    TestService testService;
    @Inject
    RunService runService;
    @Inject
    SchemaService schemaService;
    @Inject
    DatasetService datasetService;

    @Inject
    LabelServiceImpl expLabelService;
    @Inject
    RunServiceImpl expRunService;

    @org.junit.jupiter.api.Test
    public void migrate_shared_transform_creates_global() {

        String base = "urn:migrate_shared_transform_creates_global";
        Schema first = createSchema("migrate_shared_transform_creates_global", base + ":0.1");

        Extractor fooExtractor = new Extractor();
        fooExtractor.name = "foo";
        fooExtractor.jsonpath = "$.foo";

        Transformer transformer = createTransformer("foo", first, "", fooExtractor);

        Test t1 = createTest(createExampleTest("migrate_shared_transform_creates_global-test1"));
        Test t2 = createExampleTest("migrate_shared_transform_creates_global-test2");
        t2.owner = BAR_TESTER_ROLES[0];

        ValidatableResponse vr = RestAssured.given().auth().oauth2(getAccessToken("alice", BAR_TESTER_ROLES))
                .header(HttpHeaders.CONTENT_TYPE, "application/json").body(t2)
                .post("/api/test")
                .then();

        t2 = vr.statusCode(200)
                .extract().body().as(Test.class);

        addTransformer(t1, transformer);
        //addTransformer(t2,transformer);
        vr = RestAssured.given().auth().oauth2(getAccessToken("alice", BAR_TESTER_ROLES))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(Collections.singletonList(transformer.id)).post("/api/test/" + t2.id + "/transformers").then();

        vr.assertThat().statusCode(204);

        em.unwrap(Session.class).doWork(ComposableMigration::migrate);

        LabelGroupDao group = LabelGroupDao.find("name", "migrate_shared_transform_creates_global-test1").firstResult();
        assertNotNull(group, "migrate_shared_transform_creates_global-test1 label group should exist");

        assertEquals(1, group.labels.size(), "expect one label in the group");
        LabelDao grouplabel = group.labels.get(0);
        assertNotNull(grouplabel.sourceGroup, "label should have a source group");
        assertNotNull(grouplabel.originalLabel, "label should have an original label");

        LabelGroupDao sourceGroup = grouplabel.sourceGroup;
        assertNotNull(sourceGroup, "source label group should not be null");
        assertNotEquals(sourceGroup.owner, group.owner, "the source group should not have have the same owner");
    }

    @org.junit.jupiter.api.Test
    public void migrate_merge_schema_base_name_no_conflict() {
        String base = "urn:migrate_merge_schema_base_name_no_conflict";
        Schema first = createSchema("migrate_merge_schema_base_name_no_conflict1", base + ":0.1");
        Schema second = createSchema("migrate_merge_schema_base_name_no_conflict2", base + ":1.0");

        Extractor firstKeyExtractor = new Extractor();
        firstKeyExtractor.name = "first";
        firstKeyExtractor.jsonpath = "$.key";

        Extractor secondKeyExtractor = new Extractor();
        secondKeyExtractor.name = "second";
        secondKeyExtractor.jsonpath = "$.key";

        addLabel(first, "first", null, firstKeyExtractor);
        addLabel(second, "second", null, secondKeyExtractor);

        em.unwrap(Session.class).doWork(ComposableMigration::migrate);

        LabelGroupDao group = LabelGroupDao.find("name", base).firstResult();

        assertNotNull(group, "merged group should use base name");
        assertEquals(2, group.labels.size(), "group should have 2 labels:\n" + group.labels);

    }

    /**
     * The two schemas should get merged into one group with two labels that aren't mutated
     */
    @org.junit.jupiter.api.Test
    public void migrate_merge_schema_base_name_conflict() {
        String base = "urn:migrate_merge_schema_base_name_conflict";
        Schema first = createSchema("migrate_merge_schema_base_name_conflict1", base + ":0.1");
        Schema second = createSchema("migrate_merge_schema_base_name_conflict2", base + ":1.0");

        Extractor firstKeyExtractor = new Extractor();
        firstKeyExtractor.name = "first";
        firstKeyExtractor.jsonpath = "$.key";

        Extractor secondKeyExtractor = new Extractor();
        secondKeyExtractor.name = "second";
        secondKeyExtractor.jsonpath = "$.key";

        addLabel(first, "first", null, firstKeyExtractor);
        addLabel(second, "first", null, secondKeyExtractor);

        em.unwrap(Session.class).doWork(ComposableMigration::migrate);

        LabelGroupDao group = LabelGroupDao.find("name", base).firstResult();

        assertNotNull(group, "merged group should use base name");
        assertEquals(3, group.labels.size(), "group should have 3 labels:\n" + group.labels);
        assertTrue(group.labels.stream().anyMatch(l -> l.name.equals("first")), "group should have a 'first' label");
    }

    @org.junit.jupiter.api.Test
    public void migrate_transform_target_same_schema() throws JsonProcessingException {
        String name = getMethodName();
        String base = "urn:" + name;
        Schema first = createSchema(name + "_schema", base);

        Extractor fooExtractor = new Extractor();
        fooExtractor.name = "foo";
        fooExtractor.jsonpath = "$.foo";

        Extractor barExtractor = new Extractor();
        barExtractor.name = "bar";
        barExtractor.jsonpath = "$.bar";

        addLabel(first, "bar", null, barExtractor);
        Transformer transformerFoo = createTransformer("foo", first, first, "(v)=>{ return [{\"bar\":v,\"using\":\"foo\"}]}",
                fooExtractor);

        Test t1 = createTest(createExampleTest(name + "-test"));
        addTransformer(t1, transformerFoo);

        List<Integer> ids = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        ids.addAll(uploadRun(mapper.readTree("{ \"foo\": \"foo\"}"), t1.name, null));

        em.unwrap(Session.class).doWork(ComposableMigration::migrate);

        LabelGroupDao group = LabelGroupDao.find("name", t1.name).firstResult();
        assertNotNull(group, " test group should not be null");
        assertEquals(2, group.labels.size(), "group should have transform and target schema label");
        assertTrue(group.labels.stream().anyMatch(l -> l.name.equals("bar")), "group should have a foo label");

        expLabelService.calculateLabelValues(group.labels, Long.valueOf(ids.get(0)));

        List<LabelService.ValueMap> valueMaps = expLabelService.labelValues(
                group.id,
                "",
                "",
                "",
                "",
                "",
                Integer.MAX_VALUE,
                0,
                Collections.EMPTY_LIST,
                Collections.EMPTY_LIST,
                false);

        assertEquals(1, valueMaps.size());
        LabelService.ValueMap map = valueMaps.get(0);
        assertNotNull(map, "valueMap should not be null");
        assertTrue(map.data.hasNonNull("foo"), "valueMap should include the output of the transform label");
        assertTrue(map.data.hasNonNull("bar"), "valueMap should include the output of the schema's label");

    }

    @org.junit.jupiter.api.Test
    public void migrate_transform_target_different_schema() throws JsonProcessingException {
        String name = getMethodName();
        String base = "urn:" + name;
        Schema first = createSchema(name + "_schema", base);
        Schema second = createSchema(name + "_target", base + "_target");

        Extractor fooExtractor = new Extractor();
        fooExtractor.name = "foo";
        fooExtractor.jsonpath = "$.foo";

        Extractor barExtractor = new Extractor();
        barExtractor.name = "bar";
        barExtractor.jsonpath = "$.bar";

        addLabel(second, "bar", null, barExtractor);
        Transformer transformerFoo = createTransformer("foo", first, second, "(v)=>{ return [{\"bar\":v,\"using\":\"foo\"}]}",
                fooExtractor);

        Test t1 = createTest(createExampleTest(name + "-test"));
        addTransformer(t1, transformerFoo);

        List<Integer> ids = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        ids.addAll(uploadRun(mapper.readTree("{ \"foo\": \"foo\"}"), t1.name, null));

        em.unwrap(Session.class).doWork(ComposableMigration::migrate);

        LabelGroupDao group = LabelGroupDao.find("name", t1.name).firstResult();
        assertNotNull(group, " test group should not be null");
        assertEquals(2, group.labels.size(), "group should have transform and target schema label");
        assertTrue(group.labels.stream().anyMatch(l -> l.name.equals("bar")), "group should have a foo label");

        expLabelService.calculateLabelValues(group.labels, Long.valueOf(ids.get(0)));

        List<LabelService.ValueMap> valueMaps = expLabelService.labelValues(
                group.id,
                "",
                "",
                "",
                "",
                "",
                Integer.MAX_VALUE,
                0,
                Collections.EMPTY_LIST,
                Collections.EMPTY_LIST,
                false);

        assertEquals(1, valueMaps.size());
        LabelService.ValueMap map = valueMaps.get(0);
        assertNotNull(map, "valueMap should not be null");
        assertTrue(map.data.hasNonNull("foo"), "valueMap should include the output of the transform label");
        assertTrue(map.data.hasNonNull("bar"), "valueMap should include the output of the schema's label");

    }

    /**
     * Two schemas that merge also had transforms that needed to merge
     */
    @org.junit.jupiter.api.Test
    public void migrate_merge_schema_merge_transforms_same_target() {
        String base = "urn:migrate_merge_schema_merge_transforms_same_target";
        Schema first = createSchema("migrate_merge_schema_merge_transforms_same_target_transformsOld", base + ":0.1");
        Schema second = createSchema("migrate_merge_schema_merge_transforms_same_target_transformsNew", base + ":1.0");
        Schema target = createSchema("migrate_merge_schema_merge_transforms_same_target_target", base + "_target");

        Extractor fooExtractor = new Extractor();
        fooExtractor.name = "foo";
        fooExtractor.jsonpath = "$.foo";

        Extractor barExtractor = new Extractor();
        barExtractor.name = "bar";
        barExtractor.jsonpath = "$.bar";

        Extractor foundExtractor = new Extractor();
        foundExtractor.name = "found";
        foundExtractor.jsonpath = "$.found";

        addLabel(target, "found", null, foundExtractor);

        Transformer transformerFoo = createTransformer("foo", first, target, "(v)=>{ return [{\"found\":v,\"using\":\"foo\"}]}",
                fooExtractor);
        Transformer transformerBar = createTransformer("foo", second, target,
                "(v)=>{ return [{\"found\":v,\"using\":\"bar\"}]}",
                barExtractor);

        Test t1 = createTest(createExampleTest("migrate_merge_schema_merge_transforms-test"));

        addTransformer(t1, transformerFoo, transformerBar);

        em.unwrap(Session.class).doWork(ComposableMigration::migrate);

        LabelGroupDao group = LabelGroupDao.find("name", t1.name).firstResult();
        assertNotNull(group);

        assertEquals(4, group.labels.size());
        LabelDao fooLabel = group.labels.stream().filter(l -> l.name.equals("foo")).findFirst().orElse(null);
        assertNotNull(fooLabel, "group should have a label named foo");
        assertEquals(2, fooLabel.extractors.size(), "foo should have 2 extractors");
        assertEquals(2, fooLabel.extractors.stream().filter(e -> e.targetLabel != null).count());

        assertNotNull(fooLabel.targetGroup, "foo label should target a group");

        LabelDao foundLabel = group.labels.stream().filter(l -> l.name.equals("found")).findFirst().orElse(null);
        assertNotNull(foundLabel, "test should have a label 'found'");
        assertEquals(1, foundLabel.extractors.size(), "found label should only have original extractor");
        ExtractorDao foundLabelExtractor = foundLabel.extractors.get(0);
        assertNotNull(foundLabelExtractor.targetLabel, "found label's extractor should target a label");
        assertEquals(fooLabel, foundLabelExtractor.targetLabel, "found label's extractor should target foo label");
        assertEquals(fooLabel.targetGroup, foundLabel.sourceGroup, "foo label target should be the source for found label");
    }

    /**
     * The schemas should not be merged into one group
     */
    @org.junit.jupiter.api.Test
    public void migrate_not_merge_schema_name_conflict() {
        String base = "urn:migrate_not_merge_schema_name_conflict";
        String notBase = "urn:" + base;
        Schema first = createSchema("migrate_not_merge_schema_name_conflict1", base + ":0.1");
        Schema second = createSchema("migrate_not_merge_schema_name_conflict2", notBase + ":1.0");

        Extractor firstKeyExtractor = new Extractor();
        firstKeyExtractor.name = "first";
        firstKeyExtractor.jsonpath = "$.key";

        Extractor secondKeyExtractor = new Extractor();
        secondKeyExtractor.name = "second";
        secondKeyExtractor.jsonpath = "$.key";

        addLabel(first, "first", null, firstKeyExtractor);
        addLabel(second, "first", null, secondKeyExtractor);

        em.unwrap(Session.class).doWork(ComposableMigration::migrate);

        LabelGroupDao groupFirst = LabelGroupDao.find("name", base).firstResult();
        assertNotNull(groupFirst, "group should exist " + LabelGroupDao.count());
        assertEquals(1, groupFirst.labels.size());

        LabelGroupDao groupSecond = LabelGroupDao.find("name", notBase).firstResult();
        assertNotNull(groupSecond, "group should exist");
        assertEquals(1, groupSecond.labels.size());

    }

    @org.junit.jupiter.api.Test
    public void migrate_merge_run_reference_name_conflict_at_root() throws JsonProcessingException {
        Schema foo = createSchema("migrate_merge_run_reference_name_conflict_at_root-foo",
                "urn:foo:migrate_merge_run_reference_name_conflict_at_root");
        Schema bar = createSchema("migrate_merge_run_reference_name_conflict_at_root-bar",
                "urn:bar:migrate_merge_run_reference_name_conflict_at_root");

        Extractor fooKeyExtractor = new Extractor();
        fooKeyExtractor.name = "foo";
        fooKeyExtractor.jsonpath = "$.foo";

        Extractor barKeyExtractor = new Extractor();
        barKeyExtractor.name = "bar";
        barKeyExtractor.jsonpath = "$.bar";

        addLabel(foo, "found", null, fooKeyExtractor);
        addLabel(bar, "found", null, barKeyExtractor);

        Test t = createTest(createExampleTest("migrate_merge_run_reference_name_conflict_at_root"));

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        List<Integer> ids = new ArrayList<>();

        ids.addAll(uploadRun(mapper.readTree("{ \"foo\": \"foo\"}"), t.name, foo.uri));
        ids.addAll(uploadRun(mapper.readTree("{ \"bar\": \"bar\"}"), t.name, bar.uri));

        em.unwrap(Session.class).doWork(ComposableMigration::migrate);

        LabelGroupDao group = LabelGroupDao.find("name", "migrate_merge_run_reference_name_conflict_at_root").firstResult();

        assertNotNull(group, "should find the test's group");
        assertEquals(3, group.labels.size(), "group should have 3 labels\n" + group.labels);

        LabelDao foundLabel = group.labels.stream().filter(l -> l.name.equals("found")).findAny().orElse(null);

        assertNotNull(foundLabel, "group should have a label named found");
        assertEquals(2, foundLabel.extractors.size(), "found should have two extractors");

        foundLabel.extractors.forEach(e -> {
            assertEquals(e.type, ExtractorDao.Type.VALUE, e.name + " should be a VALUE type");
            assertNotNull(e.targetLabel, e.name + " should have a target label");
            assertTrue(e.jsonpath == null || e.jsonpath.isBlank(), e.name + " should not have a jsonpath");
        });
        expLabelService.calculateLabelValues(group.labels, Long.valueOf(ids.get(0)));
        expLabelService.calculateLabelValues(group.labels, Long.valueOf(ids.get(1)));

        List<LabelService.ValueMap> valueMaps = expLabelService.labelValues(
                group.id,
                "",
                "",
                "",
                "",
                "",
                Integer.MAX_VALUE,
                0,
                Collections.EMPTY_LIST,
                Collections.EMPTY_LIST,
                false);
        assertEquals(2, valueMaps.size());
        assertEquals(1, valueMaps.stream().filter(v -> v.data().hasNonNull("found2")).count());
        assertEquals(1, valueMaps.stream().filter(v -> v.data().hasNonNull("found1")).count());
        assertEquals(1, valueMaps.stream().filter(v -> v.data().get("found").asText().equals("foo")).count());
        assertEquals(1, valueMaps.stream().filter(v -> v.data().get("found").asText().equals("bar")).count());
        assertEquals(2, valueMaps.stream().filter(v -> v.data().hasNonNull("found")).count());

    }

    @org.junit.jupiter.api.Test
    public void migrate_merge_run_reference_different_jsonpath_no_conflict() throws JsonProcessingException {
        Schema foo = createSchema("migrate_merge_run_reference_different_jsonpath_no_conflict-foo",
                "urn:foo:migrate_merge_run_reference_different_jsonpath_no_conflict");
        Schema bar = createSchema("migrate_merge_run_reference_different_jsonpath_no_conflict-bar",
                "urn:bar:migrate_merge_run_reference_different_jsonpath_no_conflict");

        Extractor fooKeyExtractor = new Extractor();
        fooKeyExtractor.name = "foo";
        fooKeyExtractor.jsonpath = "$.foo";

        Extractor barKeyExtractor = new Extractor();
        barKeyExtractor.name = "bar";
        barKeyExtractor.jsonpath = "$.bar";

        addLabel(foo, "found", null, fooKeyExtractor);
        addLabel(bar, "found", null, barKeyExtractor);

        Test t = createTest(createExampleTest("migrate_merge_run_reference_different_jsonpath_no_conflict"));

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        List<Integer> ids = new ArrayList<>();

        ids.addAll(uploadRun(mapper.readTree(
                "{ \"foo\": \"foo\"}"), t.name, foo.uri));

        ids.addAll(uploadRun(mapper.readTree(
                "{ \"first\": { \"$schema\":\"" + bar.uri + "\", \"bar\": \"bar\" } }"), t.name, null));

        em.unwrap(Session.class).doWork(ComposableMigration::migrate);

        LabelGroupDao group = LabelGroupDao.find("name", "migrate_merge_run_reference_different_jsonpath_no_conflict")
                .firstResult();
        assertNotNull(group, "should find the test's group");

        //the labels should not be merged because they have different fqdn but there should be the $."first"."$schema" label
        assertEquals(3, group.labels.size(), "group should have 2 labels\n"
                + group.labels.stream().map(LabelDao::toString).collect(Collectors.joining("\n")));
        assertTrue(group.labels.stream().anyMatch(l -> l.name.equals("$.\"first\"")));
        assertEquals(2, group.labels.stream().filter(l -> l.name.equals("found")).count());

        expLabelService.calculateLabelValues(group.labels, Long.valueOf(ids.get(0)));
        expLabelService.calculateLabelValues(group.labels, Long.valueOf(ids.get(1)));

        List<LabelService.ValueMap> valueMaps = expLabelService.labelValues(
                group.id,
                "",
                "",
                "",
                "",
                "",
                Integer.MAX_VALUE,
                0,
                Collections.EMPTY_LIST,
                Collections.EMPTY_LIST,
                false);

        assertEquals(2, valueMaps.size(), "expect two value map entries");
        assertEquals(2, valueMaps.stream().filter(m -> m.data().hasNonNull("found")).count());
        assertEquals(1, valueMaps.stream().filter(m -> m.data().get("found").textValue().equals("foo")).count());
        assertEquals(1, valueMaps.stream().filter(m -> m.data().get("found").textValue().equals("bar")).count());
    }

}
