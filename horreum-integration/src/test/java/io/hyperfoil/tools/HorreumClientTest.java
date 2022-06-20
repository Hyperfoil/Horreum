package io.hyperfoil.tools;

import static io.hyperfoil.tools.HorreumTestClientExtension.dummyTest;
import static io.hyperfoil.tools.HorreumTestClientExtension.horreumClient;
import static io.hyperfoil.tools.HorreumTestExtension.resourceToString;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Instant;

import javax.ws.rs.BadRequestException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.hyperfoil.tools.horreum.entity.json.Access;
import io.hyperfoil.tools.horreum.entity.json.Run;

@ExtendWith({HorreumTestClientExtension.class})
public class HorreumClientTest {
    @Test
    public void testAddRunFromData() throws JsonProcessingException {
        JsonNode payload = new ObjectMapper().readTree(resourceToString("data/config-quickstart.jvm.json"));

        try {
            horreumClient.runService.addRunFromData("$.start", "$.stop", dummyTest.name, dummyTest.owner, Access.PUBLIC, null, null, "test", payload);
        } catch (BadRequestException badRequestException) {
            fail(badRequestException.getMessage() + (badRequestException.getCause() != null ? " : " + badRequestException.getCause().getMessage() : ""));
        }
    }

    @Test
    public void testAddRun() throws JsonProcessingException {
        Run run = new Run();
        run.start = Instant.now();
        run.stop = Instant.now();
        run.testid = -1; // should be ignored
        run.data = new ObjectMapper().readTree(resourceToString("data/config-quickstart.jvm.json"));
        run.description = "Test description";
        horreumClient.runService.add(dummyTest.name, dummyTest.owner, Access.PUBLIC, null, run);
    }
}
