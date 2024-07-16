package io.hyperfoil.tools.horreum.exp.data.extractor;

import io.hyperfoil.tools.horreum.exp.data.ExtractorDao;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import jakarta.transaction.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class LabelValueExtractorTest {

    @Transactional
    @org.junit.jupiter.api.Test
    public void persist_invalid_targetLabel(){
        ExtractorDao lve = ExtractorDao.fromString("name"+ExtractorDao.NAME_SEPARATOR+ ExtractorDao.PREFIX+".foo.bar");
        try{
            lve.persistAndFlush();
            fail("should not be able to persist");
        }catch (Exception ignored){}
    }

    @org.junit.jupiter.api.Test
    public void fromString_justName(){
        ExtractorDao lve = ExtractorDao.fromString("name");
        assertNotNull(lve.targetLabel,"targetLabel should not be null");
        assertTrue("name".equals(lve.targetLabel.name));
    }
    @org.junit.jupiter.api.Test
    public void fromString_name_iterate(){
        ExtractorDao lve = ExtractorDao.fromString("name"+ExtractorDao.FOR_EACH_SUFFIX);
        assertNotNull(lve.targetLabel,"targetLabel should not be null");
        assertTrue(lve.forEach,"lve should be iterating");
        assertTrue("name".equals(lve.targetLabel.name));
    }
    @org.junit.jupiter.api.Test
    public void fromString_name_iterate_separator(){
        ExtractorDao lve = ExtractorDao.fromString("name"+ExtractorDao.FOR_EACH_SUFFIX+ExtractorDao.NAME_SEPARATOR);
        assertNotNull(lve.targetLabel,"targetLabel should not be null");
        assertTrue(lve.forEach,"lve should be iterating");
        assertTrue("name".equals(lve.targetLabel.name));
    }

    @org.junit.jupiter.api.Test
    public void fromString_name_separator_jsonpath(){
        ExtractorDao lve = ExtractorDao.fromString("name"+ExtractorDao.NAME_SEPARATOR+ ExtractorDao.PREFIX+".foo.bar");
        assertNotNull(lve.targetLabel,"targetLabel should not be null");
        assertTrue("name".equals(lve.targetLabel.name));
        assertTrue((ExtractorDao.PREFIX+".foo.bar").equals(lve.jsonpath));
    }
    @org.junit.jupiter.api.Test
    public void fromString_name_iterate_separator_jsonpath(){
        ExtractorDao lve = ExtractorDao.fromString("name"+ExtractorDao.FOR_EACH_SUFFIX+ExtractorDao.NAME_SEPARATOR+ ExtractorDao.PREFIX+".foo.bar");
        assertNotNull(lve.targetLabel,"targetLabel should not be null");
        assertTrue(lve.forEach,"lve should be iterating");
        assertTrue("name".equals(lve.targetLabel.name));
        assertTrue((ExtractorDao.PREFIX+".foo.bar").equals(lve.jsonpath));
    }
}
