package io.hyperfoil.tools.horreum.exp.data.extractor;

import io.hyperfoil.tools.horreum.exp.data.ExtractorDao;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class RunMetadataExtractorTest {

    @org.junit.jupiter.api.Test
    public void fromString_column(){
        String str = ExtractorDao.METADATA_PREFIX+"id"+ExtractorDao.METADATA_SUFFIX;
        ExtractorDao ex = ExtractorDao.fromString(str);
        assertNotNull(ex,str+" is a valid extractor");
        assertEquals("id",ex.column_name,"incorrect column name");
    }
    @org.junit.jupiter.api.Test
    public void fromString_column_iterate(){
        String str = ExtractorDao.METADATA_PREFIX+"metadata"+ExtractorDao.METADATA_SUFFIX+ExtractorDao.FOR_EACH_SUFFIX;
        ExtractorDao ex = ExtractorDao.fromString(str);
        assertNotNull(ex,str+" is a valid extractor");
        assertEquals("metadata",ex.column_name,"incorrect column name");
        assertTrue(ex.forEach,"should be an iterating extractor");
    }

    @org.junit.jupiter.api.Test
    public void fromString_column_jsonpath(){
        String str = ExtractorDao.METADATA_PREFIX+"metadata"+ExtractorDao.METADATA_SUFFIX+ExtractorDao.NAME_SEPARATOR+ ExtractorDao.PREFIX+".foo.bar";
        ExtractorDao ex = ExtractorDao.fromString(str);
        assertNotNull(ex,str+" is a valid extractor");
        assertEquals("metadata",ex.column_name,"incorrect column name");
        assertEquals(ExtractorDao.PREFIX+".foo.bar",ex.jsonpath,"incorrect jsonpath");
    }
    @org.junit.jupiter.api.Test
    public void fromString_column_iterate_jsonpath(){
        String str = ExtractorDao.METADATA_PREFIX+"metadata"+ExtractorDao.METADATA_SUFFIX+ExtractorDao.FOR_EACH_SUFFIX+ExtractorDao.NAME_SEPARATOR+ ExtractorDao.PREFIX+".foo.bar";
        ExtractorDao ex = ExtractorDao.fromString(str);
        assertNotNull(ex,str+" is a valid extractor");
        assertEquals("metadata",ex.column_name,"incorrect column name");
        assertTrue(ex.forEach,"should be an iterating extractor");
        assertEquals(ExtractorDao.PREFIX+".foo.bar",ex.jsonpath,"incorrect jsonpath");
    }


}
