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
public class JsonpathExtractorTest {

    @org.junit.jupiter.api.Test
    public void fromString_jsonpath(){
        ExtractorDao ex = ExtractorDao.fromString(ExtractorDao.PREFIX+".foo.bar");
        assertNotNull(ex);
        assertEquals((ExtractorDao.PREFIX+".foo.bar"),ex.jsonpath,"unexpected jsonpath");
    }
    @org.junit.jupiter.api.Test
    public void fromString_iterate_jsonpath(){
        ExtractorDao ex = ExtractorDao.fromString(ExtractorDao.FOR_EACH_SUFFIX+ExtractorDao.NAME_SEPARATOR+ExtractorDao.PREFIX+".foo.bar");
        assertNotNull(ex);
        assertEquals((ExtractorDao.PREFIX+".foo.bar"),ex.jsonpath,"unexpected jsonpath");
        assertTrue(ex.forEach,"ex should iterate");
    }
}
