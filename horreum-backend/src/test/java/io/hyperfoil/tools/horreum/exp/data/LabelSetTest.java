package io.hyperfoil.tools.horreum.exp.data;

import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import jakarta.persistence.EntityExistsException;
import jakarta.transaction.Transactional;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class LabelSetTest {

    @org.junit.jupiter.api.Test
    @Transactional
    public void create_simple_label_set(){
        LabelDAO l = new LabelDAO("test_label", "uri:global:label:simple_label:0", null);

        l.loadExtractors(
                ExtractorDao.fromString("$.lve").setName("lve")
        );

        l.parent = new TestDao("dummy test"); //atm we need a dummy test to persist the label - we need to revisit this


        LabelSetDao.LabelSetEntry entry = new LabelSetDao.LabelSetEntry();
        entry.uri = "uri:global:labelSet:create_simple_label_set:label:test_label:0";
        entry.version = 0;
        entry.label = l;

        LabelSetDao labelSet = new LabelSetDao();
        labelSet.labels = Set.of(entry);
        labelSet.uri = "uri:global:labelSet:create_simple_label_set:0";
        labelSet.name = "testLabelSet";

        try {
            labelSet.persist();
        } catch (EntityExistsException e) {
            fail("should not already exist");
        }

    }

    @org.junit.jupiter.api.Test
    @Transactional
    public void populate_test_label_set(){

        LabelDAO l = new LabelDAO("test_label", "uri:global:label:test_label:0", null);

        l.name = "test_label";

        l.loadExtractors(
                ExtractorDao.fromString("$.user").setName("user"),
                ExtractorDao.fromString("$.uuid").setName("uuid")
        );

        l.parent = new TestDao("dummy test"); //atm we need a dummy test to persist the label - we need to revisit this

        LabelSetDao.LabelSetEntry entry = new LabelSetDao.LabelSetEntry();
        entry.uri = "uri:global:labelSet:populate_test_label_set:label:test_label:0";
        entry.version = 0;
        entry.label = l;

        LabelSetDao labelSet = new LabelSetDao();
        labelSet.labels = Set.of(entry);
        String labelSetUri = "uri:global:labelSet:populate_test_label_set:0";
        labelSet.uri = labelSetUri;
        labelSet.name = "testLabelSet";

        try {
            labelSet.persistAndFlush();
        } catch (EntityExistsException e) {
            fail("should not already exist");
        }

        TestDao test = new TestDao();
        test.name = "labelSetTest";
        test.copyLabelSet(labelSetUri);

        try {
            test.persistAndFlush();
        } catch (Exception e) {
            fail("could not persist test", e);
        }

        TestDao newTest = TestDao.findById(test.id);

        assertNotEquals(0, newTest.labels.size());
    }


}
