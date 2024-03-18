package io.hyperfoil.tools.horreum.svc;


import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class UtilTest {


    @Test
    public void toInstant_valid() throws IOException {
        //already an instant
        assertNotNull(Util.toInstant(Instant.now()),"failed to handle instant input");
        //number
        assertNotNull(Util.toInstant(System.currentTimeMillis()),"failed to parse current millis");
        //strings
        assertNotNull(Util.toInstant(""+System.currentTimeMillis()),"failed to parse millis as string");
        assertNotNull(Util.toInstant("2020-01-01"),"failed to parse yyyy-mm-dd");
        assertNotNull(Util.toInstant("2020-01-01T01:01:01"),"failed to parse YYYY-MM-DDTHH:mm:ss");
        assertNotNull(Util.toInstant("2020-01-01T01:01:01Z"),"failed to parse YYYY-MM-DDThh:mm:ssZ");
        assertNotNull(Util.toInstant("2020-01-01T01:01:01+00:00"),"failed to parse YYYY-MM-DDThh:mm:ss[+-]zz:zz");
        assertNotNull(Util.toInstant("2024-03-11T22:16:24.302655-04:00"),"failed to parse ISO with microseconds and zzzz");
        //json
        assertNotNull(Util.toInstant(new TextNode("2020-01-01")),"failed to parse json text YYYY-MM-DD");
        assertNotNull(Util.toInstant(new LongNode(System.currentTimeMillis())),"failed to parse current millis as json node");

    }

}
