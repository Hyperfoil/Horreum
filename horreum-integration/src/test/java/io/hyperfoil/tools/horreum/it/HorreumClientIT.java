package io.hyperfoil.tools.horreum.it;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.hyperfoil.tools.horreum.api.DatasetService;
import io.hyperfoil.tools.horreum.entity.json.*;
import io.hyperfoil.tools.horreum.it.profile.InContainerProfile;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.callback.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.hyperfoil.tools.HorreumClient;

import javax.ws.rs.BadRequestException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.fail;

@QuarkusIntegrationTest
@TestProfile(InContainerProfile.class)
public class HorreumClientIT implements  QuarkusTestBeforeTestExecutionCallback, QuarkusTestBeforeClassCallback, QuarkusTestBeforeEachCallback, QuarkusTestAfterEachCallback, QuarkusTestAfterAllCallback {

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
    public void testAddRunWithMetadataData() throws JsonProcessingException {
        JsonNode payload = new ObjectMapper().readTree(resourceToString("data/config-quickstart.jvm.json"));
        JsonNode metadata = JsonNodeFactory.instance.objectNode().put("$schema", "urn:foobar").put("foo", "bar");

        try {
            horreumClient.runService.addRunFromData("$.start", "$.stop", dummyTest.name, dummyTest.owner, Access.PUBLIC, null, null, "test", payload, metadata);
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

    // Javascript execution gets often broken with new Quarkus releases, this should catch it
    @Test
    public void testJavascriptExecution() throws InterruptedException {
        Schema schema = new Schema();
        schema.uri = "urn:dummy:schema";
        schema.name = "Dummy";
        schema.owner = dummyTest.owner;
        schema.access = Access.PUBLIC;
        schema.id = horreumClient.schemaService.add(schema);

        long now = System.currentTimeMillis();
        String ts = String.valueOf(now);
        JsonNode data = JsonNodeFactory.instance.objectNode()
              .put("$schema", schema.uri)
              .put("value", "foobar");
        horreumClient.runService.addRunFromData(ts, ts, dummyTest.name, dummyTest.owner, Access.PUBLIC, null, null, null, data);

        int datasetId = -1;
        while (System.currentTimeMillis() < now + 10000) {
            DatasetService.DatasetList datasets = horreumClient.datasetService.listByTest(dummyTest.id, null, null, null, null, null, null);
            if (datasets.datasets.isEmpty()) {
                //noinspection BusyWait
                Thread.sleep(50);
            } else {
                Assertions.assertEquals(1, datasets.datasets.size());
                datasetId = datasets.datasets.iterator().next().id;
            }
        }
        Assertions.assertNotEquals(-1, datasetId);

        Label label = new Label();
        label.name = "foo";
        label.schema = schema;
        label.function = "value => value";
        label.extractors = Collections.singletonList(new Extractor("value", "$.value", false));
        DatasetService.LabelPreview preview = horreumClient.datasetService.previewLabel(datasetId, label);
        Assertions.assertEquals("foobar", preview.value.textValue());
    }


    protected static String resourceToString(String resourcePath) {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            return new BufferedReader(new InputStreamReader(inputStream))
                    .lines().collect(Collectors.joining(" "));
        } catch (IOException e) {
            fail("Failed to read `" + resourcePath + "`", e);
            return null;
        }
    }

    public static HorreumClient horreumClient;

    public static io.hyperfoil.tools.horreum.entity.json.Test dummyTest;


    @Override
    public void beforeEach(QuarkusTestMethodContext context) {
        if ( horreumClient == null) {
            instantiateClient();
        }
        if( dummyTest != null) {
            horreumClient.testService.delete(dummyTest.id);
            dummyTest = null;
        }
        Assertions.assertNull(dummyTest);
        io.hyperfoil.tools.horreum.entity.json.Test test = new io.hyperfoil.tools.horreum.entity.json.Test();
        test.name = "test" ;
        test.owner = "dev-team";
        test.description = "This is a dummy test";
        dummyTest = horreumClient.testService.add(test);
        Assertions.assertNotNull(dummyTest);
    }

    @Override
    public void afterEach(QuarkusTestMethodContext context) {
        if ( horreumClient == null) {
            instantiateClient();
        }
        if ( dummyTest != null ) {
            horreumClient.testService.delete(dummyTest.id);
        }
        dummyTest = null;
    }

    @Override
    public void afterAll(QuarkusTestContext context) {
        horreumClient.close();
    }

    private void instantiateClient(){
        if ( horreumClient == null ) {
            String horreumBaseUrl = "http://localhost:".concat(System.getProperty("quarkus.http.test-port"));
            horreumClient = new HorreumClient.Builder()
                    .horreumUrl(horreumBaseUrl + "/")
                    .keycloakUrl(System.getProperty("keycloak.host"))
                    .horreumUser("user")
                    .horreumPassword("secret")
                    .build();

            Assertions.assertNotNull(horreumClient);

        }
    }

    @Override
    public void beforeClass(Class<?> testClass) {
        instantiateClient();
        //TODO: Fix grafana service test
/*
        if (!"OK".equals(horreumClient.alertingService.grafanaStatus())) {
            fail("Grafana is not healthy");
        }
*/

    }

    @Override
    public void beforeTestExecution(QuarkusTestMethodContext context) {
        instantiateClient();
    }
}
