package io.hyperfoil.tools;

import io.hyperfoil.tools.horreum.api.RunService;
import io.hyperfoil.tools.horreum.entity.json.Access;
import io.hyperfoil.tools.yaup.json.Json;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import javax.ws.rs.BadRequestException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class HorreumClientTest extends HorreumTestBase {

    @Test
    @Order(1)
    public void ConfigQuickstartTest() {

        String jsonContemt;
        Json payload = null;

        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("data/config-quickstart.jvm.json");) {
            jsonContemt = new BufferedReader(new InputStreamReader(inputStream))
                    .lines().collect(Collectors.joining(" "));
            payload = Json.fromString(jsonContemt);
        } catch (IOException ioException) {
            fail("Failed to read `data/config-quickstart.jvm.json`: " + ioException.getMessage());
        }

        assertNotNull(payload);

        try {
            horreumClient.runService.addRunFromData("$.start", "$.stop", getExistingtest().name, getProperty("horreum.test.owner"), Access.PUBLIC, null, null, "test", payload);
        } catch (BadRequestException badRequestException) {
            fail(badRequestException.getMessage() + (badRequestException.getCause() != null ? " : " + badRequestException.getCause().getMessage() : ""));
        }

    }
}
