package io.hyperfoil.tools.horreum.exp.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import io.hyperfoil.tools.horreum.api.exp.data.Label;
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
public class LabelTest {

    @Inject
    Validator validator;

    @org.junit.jupiter.api.Test
    public void invalid_null_extractor() {
        LabelDao l = new LabelDao();
        List<ExtractorDao> extractorList = new ArrayList<>();
        extractorList.add(null);
        l.extractors = extractorList;
        Set<ConstraintViolation<LabelDao>> violations = validator.validate(l);
        assertFalse(violations.isEmpty(), violations.toString());
    }

    @org.junit.jupiter.api.Test
    public void compareTo_sortOrder() {
        TestDao t = new TestDao("example-test");
        LabelDao a1 = new LabelDao("a1", t)
                .loadExtractors(ExtractorDao.fromString("$.a1").setName("a1"));
        LabelDao b1 = new LabelDao("b1", t)
                .loadExtractors(ExtractorDao.fromString("$.b1").setName("b1"));
        LabelDao firstAKey = new LabelDao("firstAKey", t)
                .loadExtractors(ExtractorDao.fromString("a1:$[0].key").setName("firstAKey"));
        LabelDao justA = new LabelDao("justA", t)
                .loadExtractors(ExtractorDao.fromString("a1").setName("justA"));
        LabelDao iterA = new LabelDao("iterA", t)
                .setTargetSchema(new LabelGroupDao("uri:keyed"))
                .loadExtractors(ExtractorDao.fromString("a1[]").setName("iterA"));
        LabelDao iterAKey = new LabelDao("iterAKey", t)
                //.setTargetSchema("uri:keyed") // causes an error when it targets a schema
                .loadExtractors(ExtractorDao.fromString("a1[]:$.key").setName("iterAKey"));
        LabelDao iterB = new LabelDao("iterB", t)
                .setTargetSchema(new LabelGroupDao("uri:keyed"))
                .loadExtractors(ExtractorDao.fromString("b1[]").setName("iterB"));
        LabelDao foundA = new LabelDao("foundA", t)
                .loadExtractors(ExtractorDao.fromString("iterA:$.key").setName("foundA"));
        LabelDao foundB = new LabelDao("foundB", t)
                .loadExtractors(ExtractorDao.fromString("iterB:$.key").setName("foundB"));
        LabelDao nxn = new LabelDao("nxn", t)
                .loadExtractors(
                        ExtractorDao.fromString("iterA:$.key").setName("foundA"),
                        ExtractorDao.fromString("iterB:$.key").setName("foundB"));
        LabelDao jenkinsBuild = new LabelDao("build", t)
                .loadExtractors(ExtractorDao.fromString(
                        ExtractorDao.METADATA_PREFIX + "metadata" + ExtractorDao.METADATA_SUFFIX +
                                ExtractorDao.NAME_SEPARATOR + ExtractorDao.PREFIX + ".jenkins.build")
                        .setName("build"));
        nxn.multiType = Label.MultiIterationType.NxN;

        t.setLabels(justA, foundA, firstAKey, foundB, a1, b1, iterA, iterAKey, iterB, nxn, jenkinsBuild); // order should not matter

        int nxnIndex = t.labels.indexOf(nxn);
        int foundAIndex = t.labels.indexOf(foundA);
        int foundBIndex = t.labels.indexOf(foundB);
        int iterAKeyIndex = t.labels.indexOf(iterAKey);
        int a1Index = t.labels.indexOf(a1);
        int b1Index = t.labels.indexOf(b1);
        int iterAIndex = t.labels.indexOf(iterA);
        int iterBindex = t.labels.indexOf(iterB);

        assertTrue(nxnIndex > iterAIndex);
        assertTrue(nxnIndex > iterBindex);
        assertTrue(foundAIndex > iterAIndex);
        assertTrue(foundBIndex > iterBindex);
        assertTrue(a1Index < iterAIndex);
        assertTrue(b1Index < iterBindex);
        assertTrue(iterAKeyIndex > a1Index);

    }

    @org.junit.jupiter.api.Test
    public void compareTo_dependsOn_two() {
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

    @org.junit.jupiter.api.Test
    public void compareTo_dependsOn() {
        LabelDao l1 = new LabelDao("foo").loadExtractors(ExtractorDao.fromString("$.foo"));
        LabelDao l2 = new LabelDao("bar").loadExtractors(ExtractorDao.fromString("foo:$.bar"));

        int comp = 0;
        comp = l1.compareTo(l2);
        assertTrue(comp < 0, "expect l1 to be before l2 but compareTo was " + comp);
        comp = l2.compareTo(l1);
        assertTrue(comp > 0, "expect l2 to be after l1 but compareTo was " + comp);
    }

    @org.junit.jupiter.api.Test
    public void compareTo_labelValueExtractor() {
        LabelDao l1 = new LabelDao("foo").loadExtractors(ExtractorDao.fromString("$.foo"));
        LabelDao l2 = new LabelDao("bar").loadExtractors(ExtractorDao.fromString("buz:$.bar"));

        int comp = 0;
        comp = l1.compareTo(l2);
        assertTrue(comp < 0, "expect l1 to be before l2 but compareTo was " + comp);
        comp = l2.compareTo(l1);
        assertTrue(comp > 0, "expect l2 to be after l1 but compareTo was " + comp);
    }

    @org.junit.jupiter.api.Test
    public void compareTo_both_labelValueExtractor() {
        LabelDao l1 = new LabelDao("bar").loadExtractors(ExtractorDao.fromString("foo:$.bar"));
        LabelDao l2 = new LabelDao("buz").loadExtractors(ExtractorDao.fromString("bar:$.bar"));

        int comp = 0;
        comp = l1.compareTo(l2);
        assertTrue(comp < 0, "expect l1 to be before l2 but compareTo was " + comp);
        comp = l2.compareTo(l1);
        assertTrue(comp > 0, "expect l2 to be after l1 but compareTo was " + comp);
    }

    @org.junit.jupiter.api.Test
    public void dependsOn_both_labelValueExtractor() {
        LabelDao l2 = new LabelDao("buz").loadExtractors(ExtractorDao.fromString("bar:$.bar"));
        LabelDao l3 = new LabelDao("bar").loadExtractors(ExtractorDao.fromString("foo:$.bar"));

        assertTrue(l2.dependsOn(l3));
        assertFalse(l3.dependsOn(l2));
    }

    @org.junit.jupiter.api.Test
    public void compareTo_sortedList() {
        LabelDao l1 = new LabelDao("foo").loadExtractors(ExtractorDao.fromString("$.foo"));
        LabelDao l2 = new LabelDao("buz").loadExtractors(ExtractorDao.fromString("bar:$.bar"));
        LabelDao l3 = new LabelDao("bar").loadExtractors(ExtractorDao.fromString("foo:$.bar"));

        List<LabelDao> list = new ArrayList<>(Arrays.asList(l1, l2, l3));
        list.sort(LabelDao::compareTo);

        assertTrue(l1 == list.get(0), "foo should be first");
        assertTrue(l3 == list.get(1), "bar should be second");
        assertTrue(l2 == list.get(2), "buz should be last");
    }

    @org.junit.jupiter.api.Test
    public void compareTo_same_name_and_targetLabel_different_jsonpath() {
        LabelDao l1 = new LabelDao("bar").loadExtractors(ExtractorDao.fromString("foo:$.foo"));
        LabelDao l2 = new LabelDao("bar").loadExtractors(ExtractorDao.fromString("foo:$.bar"));

        List<LabelDao> list = new ArrayList<>(Arrays.asList(l1, l2));
        list.sort(LabelDao::compareTo);
        //assume stable sort
        assertTrue(l1 == list.get(0), "foo:$.foo should be first");
        assertTrue(l2 == list.get(1), "foo:$.bar should be second");
    }

    @org.junit.jupiter.api.Test
    public void isCircular_pass() {
        //l1
        LabelDao l1 = new LabelDao("l1").loadExtractors(ExtractorDao.fromString("$.foo"));
        assertFalse(l1.isCircular(), "label without label value extractor");

        //l2 -> l1 (l2 depends on l1)
        ExtractorDao lve2_1 = new ExtractorDao();
        lve2_1.type = ExtractorDao.Type.VALUE;
        lve2_1.targetLabel = l1;
        lve2_1.name = "lve";
        LabelDao l2 = new LabelDao("l2");
        l2.extractors = Arrays.asList(lve2_1);
        assertFalse(l1.isCircular(), "label without label value extractor");
        assertFalse(l2.isCircular(), "ancestor is not");

        //l3 -> [l2,l1], l2 -> l1 (l3 depends on l2 and l1, l2 depends on l1
        ExtractorDao lve3_1 = new ExtractorDao();
        lve3_1.type = ExtractorDao.Type.VALUE;
        lve3_1.targetLabel = l1;
        lve3_1.name = "lve3_1";

        ExtractorDao lve3_2 = new ExtractorDao();
        lve3_2.type = ExtractorDao.Type.VALUE;
        lve3_2.targetLabel = l2;
        lve3_2.name = "lve3_2";

        LabelDao l3 = new LabelDao("l3");
        l3.extractors = Arrays.asList(lve3_1, lve3_2);

        assertFalse(l1.isCircular(), "shared ancestor is not circular");
        assertFalse(l2.isCircular(), "shared ancestor is not circular");
        assertFalse(l3.isCircular(), "shared ancestor is not circular");
    }

    @org.junit.jupiter.api.Test
    public void isCircular_pass_duplicate_dependency() {
        LabelDao l1 = new LabelDao("l1").loadExtractors(ExtractorDao.fromString("$.foo"));
        ;

        //l2 -> [l1, l1]
        LabelDao l2 = new LabelDao("l2");
        ExtractorDao lve2_1 = new ExtractorDao();
        lve2_1.type = ExtractorDao.Type.VALUE;
        lve2_1.targetLabel = l1;
        lve2_1.name = "lve2_1";
        ExtractorDao lve2_2 = new ExtractorDao();
        lve2_2.type = ExtractorDao.Type.VALUE;
        lve2_2.targetLabel = l1;
        lve2_2.name = "lve2_1";

        l2.extractors = Arrays.asList(lve2_1, lve2_2);

        assertFalse(l1.isCircular(), "only label value extractors can be circular");
        assertFalse(l2.isCircular(), "having duplicate dependencies is not circular");
    }

    @org.junit.jupiter.api.Test
    public void isCircular_self_reference() {
        LabelDao l = new LabelDao("foo");
        ExtractorDao lve = new ExtractorDao();
        lve.type = ExtractorDao.Type.VALUE;
        lve.name = "lve";
        lve.targetLabel = l;
        l.extractors = Arrays.asList(lve);

        assertTrue(l.isCircular(), "self referencing is circular");
    }

    @org.junit.jupiter.api.Test
    public void isCircular_unexpected_exception() {
        TestDao t = new TestDao("example-test");
        LabelDao l1 = new LabelDao("foo", t).loadExtractors(ExtractorDao.fromString("$.foo"));
        LabelDao l2 = new LabelDao("bar", t).loadExtractors(ExtractorDao.fromString("foo:$.bar"));
        t.setLabels(l1, l2);

        assertFalse(l1.isCircular());
        assertFalse(l2.isCircular());
    }

    @org.junit.jupiter.api.Test
    public void isCircular_circular_pair() {
        LabelDao l1 = new LabelDao("foo");
        ExtractorDao lve1 = new ExtractorDao();
        lve1.type = ExtractorDao.Type.VALUE;
        LabelDao l2 = new LabelDao("bar");
        ExtractorDao lve2 = new ExtractorDao();
        lve2.type = ExtractorDao.Type.VALUE;

        lve1.name = "lve1";
        lve1.targetLabel = l2;
        l1.extractors = Arrays.asList(lve1);
        lve2.name = "lve2";
        lve2.targetLabel = l1;
        l2.extractors = Arrays.asList(lve2);

        assertTrue(l1.isCircular(), "pair reference is circular");
        assertTrue(l2.isCircular(), "pair reference is circular");
    }

    @org.junit.jupiter.api.Test
    public void isCircular_circular_trio() {
        LabelDao l1 = new LabelDao("foo");
        ExtractorDao lve1 = new ExtractorDao();
        lve1.type = ExtractorDao.Type.VALUE;
        LabelDao l2 = new LabelDao("bar");
        ExtractorDao lve2 = new ExtractorDao();
        lve2.type = ExtractorDao.Type.VALUE;
        LabelDao l3 = new LabelDao("biz");
        ExtractorDao lve3 = new ExtractorDao();
        lve3.type = ExtractorDao.Type.VALUE;

        lve1.name = "lve1";
        lve1.targetLabel = l2;
        l1.extractors = Arrays.asList(lve1);
        lve2.name = "lve2";
        lve2.targetLabel = l3;
        l2.extractors = Arrays.asList(lve2);
        lve3.name = "lve3";
        lve3.targetLabel = l1;
        l3.extractors = Arrays.asList(lve3);

        assertTrue(l1.isCircular(), "trio reference is circular");
        assertTrue(l2.isCircular(), "trio reference is circular");
        assertTrue(l3.isCircular(), "trio reference is circular");
    }

    @org.junit.jupiter.api.Test
    public void fromString_instanceof() {
        ExtractorDao ex;
        ex = ExtractorDao
                .fromString(ExtractorDao.FOR_EACH_SUFFIX + ExtractorDao.NAME_SEPARATOR + ExtractorDao.PREFIX + ".foo.bar");
        assertInstanceOf(ExtractorDao.class, ex, ExtractorDao.FOR_EACH_SUFFIX + ExtractorDao.NAME_SEPARATOR
                + ExtractorDao.PREFIX + ".foo.bar should be jsonpath extractor");
        assertEquals(ExtractorDao.Type.PATH, ex.type);
        ex = ExtractorDao.fromString(ExtractorDao.METADATA_PREFIX + "metadata" + ExtractorDao.METADATA_SUFFIX
                + ExtractorDao.NAME_SEPARATOR + ExtractorDao.PREFIX + ".foo.bar");
        assertEquals(ExtractorDao.Type.METADATA, ex.type);
        assertInstanceOf(ExtractorDao.class, ex, ExtractorDao.METADATA_PREFIX + "metadata" + ExtractorDao.METADATA_SUFFIX
                + ExtractorDao.NAME_SEPARATOR + ExtractorDao.PREFIX + ".foo.bar should be run metadata extractor");
        assertEquals(ExtractorDao.Type.METADATA, ex.type);
        ex = ExtractorDao.fromString("foo");
        assertInstanceOf(ExtractorDao.class, ex, "foo should be a label value extractor");
        assertEquals(ExtractorDao.Type.VALUE, ex.type);
    }

    @org.junit.jupiter.api.Test
    public void name_surrounded_by_curly_bracket() {
        LabelDao l = new LabelDao("{name}");
        Set<ConstraintViolation<LabelDao>> constraints = validator.validate(l);
        assertFalse(constraints.isEmpty(), "expect constraints: " + constraints);
    }

    @org.junit.jupiter.api.Test
    public void name_starts_with_curly_bracket() {
        LabelDao l = new LabelDao("{name");
        Set<ConstraintViolation<LabelDao>> constraints = validator.validate(l);
        assertFalse(constraints.isEmpty(), "expect constraints: " + constraints);
    }

    @org.junit.jupiter.api.Test
    public void name_ends_with_curly_bracket() {
        LabelDao l = new LabelDao("name}");
        Set<ConstraintViolation<LabelDao>> constraints = validator.validate(l);
        assertFalse(constraints.isEmpty(), "expect constraints: " + constraints);
    }

    @org.junit.jupiter.api.Test
    public void name_starts_with_dollar() {
        LabelDao l = new LabelDao("$name");
        Set<ConstraintViolation<LabelDao>> constraints = validator.validate(l);
        assertFalse(constraints.isEmpty(), "expect constraints: " + constraints);
    }

    @org.junit.jupiter.api.Test
    public void name_ends_with_iteration_indicator() {
        LabelDao l = new LabelDao("name" + ExtractorDao.FOR_EACH_SUFFIX);
        Set<ConstraintViolation<LabelDao>> constraints = validator.validate(l);
        assertFalse(constraints.isEmpty(), "expect constraints: " + constraints);
    }
}
