package io.hyperfoil.tools;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import io.hyperfoil.tools.horreum.entity.json.Access;
import io.hyperfoil.tools.yaup.json.Json;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import javax.ws.rs.BadRequestException;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class HorreumClientTest extends HorreumTestBase {
    @Test
    @Order(1)
    public void ConfigQuickstartTest() {
        createOrLookupTest();

        Json payload = Json.fromString(resourceToString("data/config-quickstart.jvm.json"));
        assertNotNull(payload);

        try {
            horreumClient.runService.addRunFromData("$.start", "$.stop", dummyTest.name, dummyTest.owner, Access.PUBLIC, null, null, "test", payload);
        } catch (BadRequestException badRequestException) {
            fail(badRequestException.getMessage() + (badRequestException.getCause() != null ? " : " + badRequestException.getCause().getMessage() : ""));
        }
    }
}
