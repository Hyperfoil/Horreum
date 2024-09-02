package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.alerting.DatasetLog;
import io.hyperfoil.tools.horreum.api.alerting.TransformationLog;
import io.hyperfoil.tools.horreum.api.data.ActionLog;
import io.hyperfoil.tools.horreum.api.data.Label;
import io.hyperfoil.tools.horreum.api.data.Schema;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.arc.impl.ParameterizedTypeImpl;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class LogServiceTest extends BaseServiceTest {

    @org.junit.jupiter.api.Test
    public void testLogs(TestInfo info) throws JsonProcessingException, InterruptedException {
        Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);
        setTestVariables(test, "Value", new Label("value", schema.id));

        // This run won't contain the 'value'
        ObjectNode runJson = JsonNodeFactory.instance.objectNode();
        runJson.put("$schema", schema.uri);

        BlockingQueue<MissingValuesEvent> missingQueue = serviceMediator
                .getEventQueue(AsyncEventChannels.DATASET_MISSING_VALUES, test.id);
        missingQueue.drainTo(new ArrayList<>());
        int runId = uploadRun(runJson, test.name);

        MissingValuesEvent event = missingQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(event);
        assertEquals(runId, event.dataset.runId);

        Thread.sleep(500);

        int datasetLogCount = jsonRequest()
                .auth()
                .oauth2(getTesterToken())
                .get("/api/log/dataset/variables/" + test.id + "/count")
                .then()
                .statusCode(200)
                .extract()
                .as(Integer.class);
        assertTrue(datasetLogCount > 0);

        int datasetLogCount2 = jsonRequest()
                .get("/api/log/dataset/variables/" + test.id + "/count?datasetId=" + event.dataset.id)
                .then()
                .statusCode(200)
                .extract()
                .as(Integer.class);
        assertEquals(datasetLogCount2, datasetLogCount);

        int actionCountLog = jsonRequest()
                .get("/api/log/action/" + test.id + "/count")
                .then()
                .statusCode(200)
                .extract()
                .as(Integer.class);
        assertEquals(0, actionCountLog);

        int transformationLogCount = jsonRequest()
                .get("/api/log/transformation/" + test.id + "/count")
                .then()
                .statusCode(200)
                .extract()
                .as(Integer.class);
        assertEquals(0, transformationLogCount);

        List<ActionLog> actionLogs = jsonRequest()
                .get("/api/log/action/" + test.id)
                .then().statusCode(200).extract().body().as(new ParameterizedTypeImpl(List.class, ActionLog.class));
        assertEquals(actionCountLog, actionLogs.size());

        List<DatasetLog> datasetLogs = jsonRequest()
                .get("/api/log/dataset/variables/" + test.id)
                .then().statusCode(200).extract().body().as(new ParameterizedTypeImpl(List.class, DatasetLog.class));
        assertEquals(datasetLogCount, datasetLogs.size());

        List<TransformationLog> transformationLogs = jsonRequest()
                .get("/api/log/transformation/" + test.id)
                .then().statusCode(200).extract().body().as(new ParameterizedTypeImpl(List.class, TransformationLog.class));
        assertEquals(transformationLogCount, transformationLogs.size());

        actionCountLog = jsonRequest()
                .get("/api/log/action/" + test.id + "/count")
                .then()
                .statusCode(200)
                .extract()
                .as(Integer.class);

        actionLogs = jsonRequest()
                .get("/api/log/action/" + test.id)
                .then().statusCode(200).extract().body().as(new ParameterizedTypeImpl(List.class, ActionLog.class));
        assertEquals(actionCountLog, actionLogs.size());

        BlockingQueue<Integer> runTrashedEvents = serviceMediator.getEventQueue(AsyncEventChannels.RUN_TRASHED, test.id);
        deleteTest(test);
        assertNotNull(runTrashedEvents.poll(10, TimeUnit.SECONDS));

        datasetLogCount = jsonRequest()
                .auth()
                .oauth2(getTesterToken())
                .get("/api/log/dataset/variables/" + test.id + "/count")
                .then()
                .statusCode(200)
                .extract()
                .as(Integer.class);

        assertEquals(0, datasetLogCount);
    }
}
