package io.hyperfoil.tools.horreum.liquidbase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.hyperfoil.tools.horreum.api.data.Extractor;
import io.hyperfoil.tools.horreum.api.data.Label;
import io.hyperfoil.tools.horreum.api.data.Schema;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.api.services.DatasetService;
import io.hyperfoil.tools.horreum.api.services.RunService;
import io.hyperfoil.tools.horreum.api.services.SchemaService;
import io.hyperfoil.tools.horreum.api.services.TestService;
import io.hyperfoil.tools.horreum.exp.data.LabelDao;
import io.hyperfoil.tools.horreum.exp.data.LabelGroupDao;
import io.hyperfoil.tools.horreum.exp.svc.LabelServiceImpl;
import io.hyperfoil.tools.horreum.liquibase.ComposableMigration;
import io.hyperfoil.tools.horreum.svc.BaseServiceTest;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.Ignore;

import java.sql.Connection;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class ComposableMigrationTest extends BaseServiceTest {

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
    LabelServiceImpl labelService;

    @org.junit.jupiter.api.Test
    public void migrate_merge_schema_base_name_no_conflict(){
        String base = "urn:migrate_merge_schema_base_name_no_conflict";
        Schema first = createSchema("migrate_merge_schema_base_name_no_conflict1",base+":0.1");
        Schema second = createSchema("migrate_merge_schema_base_name_no_conflict2",base+":1.0");

        Extractor firstKeyExtractor = new Extractor();
        firstKeyExtractor.name = "first";
        firstKeyExtractor.jsonpath = "$.key";

        Extractor secondKeyExtractor = new Extractor();
        secondKeyExtractor.name = "second";
        secondKeyExtractor.jsonpath = "$.key";

        addLabel(first,"first",null,firstKeyExtractor);
        addLabel(second,"second",null,secondKeyExtractor);

        em.unwrap(Session.class).doWork(ComposableMigration::migrate);

        LabelGroupDao group = LabelGroupDao.find("name",base).firstResult();

        assertNotNull(group,"merged group should use base name");
        assertEquals(2,group.labels.size(),"group should have 2 labels:\n"+group.labels);
    }

    @org.junit.jupiter.api.Test
    public void migrate_merge_schema_base_name_conflict(){
        String base = "urn:migrate_merge_schema_base_name_conflict";
        Schema first = createSchema("migrate_merge_schema_base_name_conflict1",base+":0.1");
        Schema second = createSchema("migrate_merge_schema_base_name_conflict2",base+":1.0");

        Extractor firstKeyExtractor = new Extractor();
        firstKeyExtractor.name = "first";
        firstKeyExtractor.jsonpath = "$.key";

        Extractor secondKeyExtractor = new Extractor();
        secondKeyExtractor.name = "second";
        secondKeyExtractor.jsonpath = "$.key";

        addLabel(first,"first",null,firstKeyExtractor);
        addLabel(second,"first",null,secondKeyExtractor);

        em.unwrap(Session.class).doWork(ComposableMigration::migrate);


        LabelGroupDao group = LabelGroupDao.find("name",base).firstResult();

        assertNotNull(group,"merged group should use base name");
        assertEquals(3,group.labels.size(),"group should have 3 labels:\n"+group.labels);
        assertTrue(group.labels.stream().anyMatch(l->l.name.equals("first")),"group should have a 'first' label");
    }

    @org.junit.jupiter.api.Test
    public void migrate_not_merge_schema_name_conflict() {
        String base = "urn:migrate_not_merge_schema_name_conflict";
        String notBase = "urn:"+base;
        Schema first = createSchema("migrate_not_merge_schema_name_conflict1",base+":0.1");
        Schema second = createSchema("migrate_not_merge_schema_name_conflict2",notBase+":1.0");

        Extractor firstKeyExtractor = new Extractor();
        firstKeyExtractor.name = "first";
        firstKeyExtractor.jsonpath = "$.key";

        Extractor secondKeyExtractor = new Extractor();
        secondKeyExtractor.name = "second";
        secondKeyExtractor.jsonpath = "$.key";

        addLabel(first,"first",null,firstKeyExtractor);
        addLabel(second,"first",null,secondKeyExtractor);

        em.unwrap(Session.class).doWork(ComposableMigration::migrate);

        LabelGroupDao groupFirst = LabelGroupDao.find("name",base).firstResult();
        assertNotNull(groupFirst,"group should exist "+LabelGroupDao.count());
        assertEquals(1,groupFirst.labels.size());

        LabelGroupDao groupSecond = LabelGroupDao.find("name",notBase).firstResult();
        assertNotNull(groupSecond,"group should exist");
        assertEquals(1,groupSecond.labels.size());

    }

    @org.junit.jupiter.api.Test
    public void migrate_merge_run_reference_name_conflict_at_root() throws JsonProcessingException {
        Schema foo = createSchema("migrate_merge_run_reference_name_conflict_at_root-foo","urn:foo:migrate_merge_run_reference_name_conflict_at_root");
        Schema bar = createSchema("migrate_merge_run_reference_name_conflict_at_root-bar","urn:bar:migrate_merge_run_reference_name_conflict_at_root");

        Extractor fooKeyExtractor = new Extractor();
        fooKeyExtractor.name = "foo";
        fooKeyExtractor.jsonpath = "$.foo";

        Extractor barKeyExtractor = new Extractor();
        barKeyExtractor.name = "bar";
        barKeyExtractor.jsonpath = "$.bar";

        addLabel(foo,"found",null,fooKeyExtractor);
        addLabel(bar,"found",null,barKeyExtractor);

        Test t = createTest(createExampleTest("migrate_merge_run_reference_name_conflict_at_root"));

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        uploadRun(mapper.readTree(
            "{ \"foo\": \"foo\"}"), t.name, foo.uri);

        uploadRun(mapper.readTree(
            "{ \"bar\": \"bar\"}"), t.name, bar.uri);

        em.unwrap(Session.class).doWork(ComposableMigration::migrate);

        LabelGroupDao group = LabelGroupDao.find("name","migrate_merge_run_reference_name_conflict_at_root").firstResult();
        assertNotNull(group,"should find the test's group");

        assertEquals(3,group.labels.size(),"group should have 3 labels\n"+group.labels);
        LabelDao foundLabel = group.labels.stream().filter(l->l.name.equals("found")).findAny().orElse(null);
        assertNotNull(foundLabel,"group should have a label named found");
        assertEquals(2,foundLabel.extractors.size(),"found should have two extractors");

    }
    @org.junit.jupiter.api.Test
    public void migrate_merge_run_reference_different_jsonpath_no_conflict() throws JsonProcessingException {
        Schema foo = createSchema("migrate_merge_run_reference_different_jsonpath_no_conflict-foo","urn:foo:migrate_merge_run_reference_different_jsonpath_no_conflict");
        Schema bar = createSchema("migrate_merge_run_reference_different_jsonpath_no_conflict-bar","urn:bar:migrate_merge_run_reference_different_jsonpath_no_conflict");

        Extractor fooKeyExtractor = new Extractor();
        fooKeyExtractor.name = "foo";
        fooKeyExtractor.jsonpath = "$.foo";

        Extractor barKeyExtractor = new Extractor();
        barKeyExtractor.name = "bar";
        barKeyExtractor.jsonpath = "$.bar";

        addLabel(foo,"found",null,fooKeyExtractor);
        addLabel(bar,"found",null,barKeyExtractor);

        Test t = createTest(createExampleTest("migrate_merge_run_reference_different_jsonpath_no_conflict"));

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        uploadRun(mapper.readTree(
                "{ \"foo\": \"foo\"}"), t.name, foo.uri);

        uploadRun(mapper.readTree(
                "{ \"first\": { \"$schema\":\""+bar.uri+"\", \"bar\": \"bar\" } }"), t.name, null);

        em.unwrap(Session.class).doWork(ComposableMigration::migrate);

        LabelGroupDao group = LabelGroupDao.find("name","migrate_merge_run_reference_different_jsonpath_no_conflict").firstResult();
        assertNotNull(group,"should find the test's group");

        //the labels should not be merged because they have different fqdn but there should be the $."first"."$schema" label
        assertEquals(3,group.labels.size(),"group should have 2 labels\n"+group.labels.stream().map(LabelDao::toString).collect(Collectors.joining("\n")));
        assertTrue(group.labels.stream().anyMatch(l->l.name.equals("$.\"first\".\"$schema\"")));
        assertEquals(2,group.labels.stream().filter(l->l.name.equals("found")).count());

        //TODO not sure how we want to verify the values, which 'found' should be used?
    }


}
