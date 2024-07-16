package io.hyperfoil.tools.horreum.exp.data;

import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class TestTest {


    @Transactional
    @org.junit.jupiter.api.Test
    public void label_references_peer_label(){
        TestDao t = new TestDao("example-test");
        t.loadLabels(
                //added before the label it references but that should not cause a problem
                new LabelDAO("bar",t).loadExtractors(ExtractorDao.fromString("foo:$.bar")),
                new LabelDAO("foo",t).loadExtractors(ExtractorDao.fromString("$.foo"))

        );
        try{
            t.persistAndFlush();
        }catch (Exception e){
            fail(e.getMessage(),e);
        }

        long created = LabelDAO.find("from LabelDAO l where l.name=?1 and l.parent.id=?2","foo",t.id).count();
        //we cannot check that
        assertEquals(1,created,"should only create one label");

    }
    @Transactional
    @org.junit.jupiter.api.Test
    public void invalid_missing_targetLabel() {
        TestDao t = new TestDao("example-test");
                t.loadLabels(
                        new LabelDAO("missing",t)
                                .loadExtractors(ExtractorDao.fromString("doesNotExist:$.foo"))
                );
        try {
            t.persistAndFlush();
            fail("should throw an exception");
        }catch(ConstraintViolationException ignored){}
    }
    @Transactional
    @org.junit.jupiter.api.Test
    public void loadLabels_prevent_circular(){
        TestDao t= new TestDao("example-test");
        LabelDAO l1 = new LabelDAO("foo",t);
        ExtractorDao lve1 = new ExtractorDao();
        lve1.type = ExtractorDao.Type.VALUE;
        LabelDAO l2 = new LabelDAO("bar",t);
        ExtractorDao lve2 = new ExtractorDao();
        lve2.type = ExtractorDao.Type.VALUE;

        lve1.name="lve1";
        lve1.targetLabel=l2;
        l1.extractors= Arrays.asList(lve1);
        lve2.name="lve2";
        lve2.targetLabel=l1;
        l2.extractors=Arrays.asList(lve2);

        try {
            t.loadLabels(l1, l2);
            assertNotNull(t.labels);
            assertEquals(2,t.labels.size());
            t.persistAndFlush();//
            fail("validation check on looped labels should prevent this");
        }catch(ConstraintViolationException ignored){}
    }

    @org.junit.jupiter.api.Test
    public void loadLabels_sorted(){
        TestDao t = new TestDao("foo");
        LabelDAO l1 = new LabelDAO("foo").loadExtractors(ExtractorDao.fromString("$.foo"));
        LabelDAO l2 = new LabelDAO("buz").loadExtractors(ExtractorDao.fromString("bar:$.bar"));
        LabelDAO l3 = new LabelDAO("bar").loadExtractors(ExtractorDao.fromString("foo:$.bar"));

        t.loadLabels(l1,l2,l3);

        assertTrue(l1 == t.labels.get(0),"foo should be first");
        assertTrue(l3 == t.labels.get(1),"bar should be second");
        assertTrue(l2 == t.labels.get(2),"buz should be last");
    }
    @org.junit.jupiter.api.Test
    public void loadLabels_2nd_generation_dependency(){
        LabelDAO a1 = new LabelDAO("a1")
                .loadExtractors(ExtractorDao.fromString("$.a1").setName("a1"));
        LabelDAO b1 = new LabelDAO("b1")
                .loadExtractors(ExtractorDao.fromString("$.b1").setName("b1"));
        LabelDAO iterA = new LabelDAO("iterA")
                .loadExtractors(ExtractorDao.fromString("a1[]").setName("iterA"));
        LabelDAO iterB = new LabelDAO("iterB")
                .loadExtractors(ExtractorDao.fromString("b1[]").setName("iterB"));
        LabelDAO found = new LabelDAO("found")
                .loadExtractors(ExtractorDao.fromString("iterA:$.key").setName("found"));
        LabelDAO nxn = new LabelDAO("nxn")
                .loadExtractors(
                        ExtractorDao.fromString("iterA:$.key").setName("foundA"),
                        ExtractorDao.fromString("iterB:$.key").setName("foundB")
                );
        TestDao t = new TestDao("example-test");
        t.loadLabels(nxn,found,iterB,iterA,b1,a1);
        List<LabelDAO> list = new ArrayList<>(t.labels);

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
