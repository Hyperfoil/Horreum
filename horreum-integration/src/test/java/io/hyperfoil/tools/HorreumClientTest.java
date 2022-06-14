package io.hyperfoil.tools;

import static org.junit.jupiter.api.Assertions.fail;
import static io.hyperfoil.tools.HorreumTestExtension.resourceToString;
import static io.hyperfoil.tools.HorreumTestClientExtension.dummyTest;
import static io.hyperfoil.tools.HorreumTestClientExtension.horreumClient;

import io.hyperfoil.tools.horreum.entity.json.Access;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import javax.ws.rs.BadRequestException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({HorreumTestClientExtension.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class HorreumClientTest {
    @Test
    @Order(1)
    public void ConfigQuickstartTest() throws JsonProcessingException {
        JsonNode payload = new ObjectMapper().readTree(resourceToString("data/config-quickstart.jvm.json"));

        try {
            horreumClient.runService.addRunFromData("$.start", "$.stop", dummyTest.name, dummyTest.owner, Access.PUBLIC, null, null, "test", payload);
        } catch (BadRequestException badRequestException) {
            fail(badRequestException.getMessage() + (badRequestException.getCause() != null ? " : " + badRequestException.getCause().getMessage() : ""));
        }
    }
}
