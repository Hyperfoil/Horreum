package io.hyperfoil.tools;

import static io.hyperfoil.tools.HorreumTestClientExtension.dummyTest;
import static io.hyperfoil.tools.HorreumTestClientExtension.horreumClient;
import static io.hyperfoil.tools.HorreumTestExtension.resourceToString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Instant;
import java.util.Collections;

import javax.ws.rs.BadRequestException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.hyperfoil.tools.horreum.api.DatasetService;
import io.hyperfoil.tools.horreum.entity.json.Access;
import io.hyperfoil.tools.horreum.entity.json.Extractor;
import io.hyperfoil.tools.horreum.entity.json.Label;
import io.hyperfoil.tools.horreum.entity.json.Run;
import io.hyperfoil.tools.horreum.entity.json.Schema;

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
                assertEquals(1, datasets.datasets.size());
                datasetId = datasets.datasets.iterator().next().id;
            }
        }
        assertNotEquals(-1, datasetId);

        Label label = new Label();
        label.name = "foo";
        label.schema = schema;
        label.function = "value => value";
        label.extractors = Collections.singletonList(new Extractor("value", "$.value", false));
        DatasetService.LabelPreview preview = horreumClient.datasetService.previewLabel(datasetId, label);
        assertEquals("foobar", preview.value.textValue());
    }
}
