package io.hyperfoil.tools.horreum.exp.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;

import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class TestTest {

    @Transactional
    @org.junit.jupiter.api.Test
    public void label_references_peer_label() {
        TestDao t = new TestDao("example-test");
        t.setLabels(
                //added before the label it references but that should not cause a problem
                new LabelDao("bar", t).loadExtractors(ExtractorDao.fromString("foo:$.bar")),
                new LabelDao("foo", t).loadExtractors(ExtractorDao.fromString("$.foo"))

        );
        try {
            t.persistAndFlush();
        } catch (Exception e) {
            fail(e.getMessage(), e);
        }

        long created = LabelDao.find("from LabelDao l where l.name=?1 and l.group.id=?2", "foo", t.id).count();
        //we cannot check that
        assertEquals(1, created, "should only create one label");

    }

    @Transactional
    @org.junit.jupiter.api.Test
    public void invalid_missing_targetLabel() {
        TestDao t = new TestDao("example-test");
        t.setLabels(
                new LabelDao("missing", t)
                        .loadExtractors(ExtractorDao.fromString("doesNotExist:$.foo")));
        try {
            t.persistAndFlush();
            fail("should throw an exception");
        } catch (ConstraintViolationException ignored) {
        }
    }

    @Transactional
    @org.junit.jupiter.api.Test
    public void loadLabels_prevent_circular() {
        TestDao t = new TestDao("example-test");
        LabelDao l1 = new LabelDao("foo", t);
        ExtractorDao lve1 = new ExtractorDao();
        lve1.type = ExtractorDao.Type.VALUE;
        LabelDao l2 = new LabelDao("bar", t);
        ExtractorDao lve2 = new ExtractorDao();
        lve2.type = ExtractorDao.Type.VALUE;

        lve1.name = "lve1";
        lve1.targetLabel = l2;
        l1.extractors = Arrays.asList(lve1);
        lve2.name = "lve2";
        lve2.targetLabel = l1;
        l2.extractors = Arrays.asList(lve2);

        try {
            t.setLabels(l1, l2);
            assertNotNull(t.labels);
            assertEquals(2, t.labels.size());
            t.persistAndFlush();//
            fail("validation check on looped labels should prevent this");
        } catch (ConstraintViolationException ignored) {
        }
    }

    @org.junit.jupiter.api.Test
    public void loadLabels_sorted() {
        TestDao t = new TestDao("foo");
        LabelDao l1 = new LabelDao("foo").loadExtractors(ExtractorDao.fromString("$.foo"));
        LabelDao l2 = new LabelDao("buz").loadExtractors(ExtractorDao.fromString("bar:$.bar"));
        LabelDao l3 = new LabelDao("bar").loadExtractors(ExtractorDao.fromString("foo:$.bar"));

        t.setLabels(l1, l2, l3);

        assertTrue(l1 == t.labels.get(0), "foo should be first");
        assertTrue(l3 == t.labels.get(1), "bar should be second");
        assertTrue(l2 == t.labels.get(2), "buz should be last");
    }

    @org.junit.jupiter.api.Test
    public void loadLabels_2nd_generation_dependency() {
        LabelDao a1 = new LabelDao("a1")
                .loadExtractors(ExtractorDao.fromString("$.a1").setName("a1"));
        LabelDao b1 = new LabelDao("b1")
                .loadExtractors(ExtractorDao.fromString("$.b1").setName("b1"));
        LabelDao iterA = new LabelDao("iterA")
                .loadExtractors(ExtractorDao.fromString("a1[]").setName("iterA"));
        LabelDao iterB = new LabelDao("iterB")
                .loadExtractors(ExtractorDao.fromString("b1[]").setName("iterB"));
        LabelDao found = new LabelDao("found")
                .loadExtractors(ExtractorDao.fromString("iterA:$.key").setName("found"));
        LabelDao nxn = new LabelDao("nxn")
                .loadExtractors(
                        ExtractorDao.fromString("iterA:$.key").setName("foundA"),
                        ExtractorDao.fromString("iterB:$.key").setName("foundB"));
        TestDao t = new TestDao("example-test");
        t.setLabels(nxn, found, iterB, iterA, b1, a1);
        List<LabelDao> list = new ArrayList<>(t.labels);

        int a1Index = list.indexOf(a1);
        int b1Index = list.indexOf(b1);
        int iterAIndex = list.indexOf(iterA);
        int iterBIndex = list.indexOf(iterB);
        int foundIndex = list.indexOf(found);
        int nxnIndex = list.indexOf(nxn);

        assertTrue(a1Index < iterAIndex);
        assertTrue(b1Index < iterBIndex);
        assertTrue(foundIndex > iterAIndex);
        assertTrue(nxnIndex > iterAIndex);
        assertTrue(nxnIndex > iterBIndex);
    }
}
