package io.hyperfoil.tools.horreum.changedetection;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hyperfoil.tools.horreum.api.alerting.Change;
import io.hyperfoil.tools.horreum.api.alerting.ChangeDetection;
import io.hyperfoil.tools.horreum.api.alerting.DataPoint;
import io.hyperfoil.tools.horreum.api.data.Label;
import io.hyperfoil.tools.horreum.api.data.Schema;
import io.hyperfoil.tools.horreum.api.data.changeDetection.ChangeDetectionModelType;
import io.hyperfoil.tools.horreum.bus.MessageBusChannels;
import io.hyperfoil.tools.horreum.entity.alerting.ChangeDAO;
import io.hyperfoil.tools.horreum.entity.alerting.DataPointDAO;
import io.hyperfoil.tools.horreum.entity.alerting.VariableDAO;
import io.hyperfoil.tools.horreum.entity.data.DatasetDAO;
import io.hyperfoil.tools.horreum.svc.BaseServiceTest;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import jakarta.inject.Inject;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class EdivisiveTests extends BaseServiceTest {

    @Inject
    ChangeDetectionModelResolver resolver;

    @Test
    public void testTemporaryFiles() {
        try {
            HunterEDivisiveModel.TmpFiles tmpFiles = HunterEDivisiveModel.TmpFiles.instance();
            assertTrue(tmpFiles.tmpdir.exists());
        } catch (IOException e) {
            fail(e);
        }
    }

    @Test
    public void testFileStructures() {

        HunterEDivisiveModel model = (HunterEDivisiveModel) resolver.getModel(ChangeDetectionModelType.EDIVISIVE_HUNTER);
        assertNotNull(model);

        try {
            //1. Valid File Structure
            HunterEDivisiveModel.TmpFiles tmpFiles = getTmpFiles("change/eDivisive/valid/tests/resources/horreum.csv");

            //cast ChangeDetectionModel to access protected methods
            boolean valid = model.validateInputCsv(tmpFiles);

            assertTrue(valid);

            //2. Invalid File Structure
            tmpFiles = getTmpFiles("change/eDivisive/invalid/tests/resources/horreum.csv");

            //cast ChangeDetectionModel to access protected methods
            valid = model.validateInputCsv(tmpFiles);

            assertTrue(!valid);


            //todo:: cleanup temp files
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @NotNull
    private static HunterEDivisiveModel.TmpFiles getTmpFiles(String resource) throws IOException {
        HunterEDivisiveModel.TmpFiles tmpFiles = HunterEDivisiveModel.TmpFiles.instance();
        assertTrue(tmpFiles.confFile.exists());

        try (InputStream validCsvStream = EdivisiveTests.class.getClassLoader().getResourceAsStream(resource)) {
            try( OutputStream confOut = new FileOutputStream(tmpFiles.inputFile)){
                confOut.write(validCsvStream.readAllBytes());
            } catch (IOException e){
                fail("Could not extract Hunter configuration from archive");
            }
        } catch (IOException e) {
            fail("Could not create temporary file for Hunter eDivisive algorithm", e);
        }
        return tmpFiles;
    }

    @Test
    public void testDetectedChangePoints(){

        HunterEDivisiveModel model = (HunterEDivisiveModel) resolver.getModel(ChangeDetectionModelType.EDIVISIVE_HUNTER);
        assertNotNull(model);

        List<ChangeDAO> changePoints = new ArrayList<>();

        try {
            HunterEDivisiveModel.TmpFiles tmpFiles = getTmpFiles("change/eDivisive/valid/tests/resources/horreum.csv");

            //cast ChangeDetectionModel to access protected methods
            boolean valid = model.validateInputCsv(tmpFiles);

            assertTrue(valid);

            model.processChangePoints(
                    (datapointID) -> {
                        DataPointDAO datapoint = new DataPointDAO();
                        datapoint.id = datapointID;
                        datapoint.timestamp = Instant.now();
                        datapoint.variable = new VariableDAO();
                        DatasetDAO datasetDAO = new DatasetDAO();
                        datasetDAO.id = datapointID;
                        datapoint.dataset = datasetDAO;
                        return Optional.of(datapoint);
                    },
                    (changePoints::add),
                    tmpFiles
                    );

            assertNotEquals(0, changePoints.size());

            assertEquals(1535410, changePoints.get(0).dataset.id);

            model.cleanupTmpFiles(tmpFiles);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testEdvisiveModelAnalyze(TestInfo info) throws Exception {

        io.hyperfoil.tools.horreum.api.data.Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);

        ChangeDetection cd = new ChangeDetection();
        cd.model = HunterEDivisiveModel.NAME;
        setTestVariables(test, "Value", new Label("value", schema.id), cd);

        BlockingQueue<DataPoint.Event> datapointQueue = eventConsumerQueue(DataPoint.Event.class, MessageBusChannels.DATAPOINT_NEW, e -> e.testId == test.id);
        BlockingQueue<Change.Event> changeQueue = eventConsumerQueue(Change.Event.class, MessageBusChannels.CHANGE_NEW, e -> e.dataset.testId == test.id);

        long ts = System.currentTimeMillis();
        uploadRun(ts, ts, runWithValue(1, schema), test.name);
        assertValue(datapointQueue, 1);
        uploadRun(ts + 1, ts + 1, runWithValue(2, schema), test.name);
        assertValue(datapointQueue, 2);
        int run3 = uploadRun(ts + 2, ts + 2, runWithValue(1, schema), test.name);
        assertValue(datapointQueue, 1);
        int run4 = uploadRun(ts + 3, ts + 3, runWithValue(2, schema), test.name);
        assertValue(datapointQueue, 2);

        assertNull(changeQueue.poll(50, TimeUnit.MILLISECONDS));

        uploadRun(ts + 4, ts + 4, runWithValue(3, schema), test.name);
        assertValue(datapointQueue, 3);

        Change.Event changeEvent1 = changeQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(changeEvent1);
        testSerialization(changeEvent1, Change.Event.class);
        // The change is detected already at run 4 because it's > than the previous mean
        assertEquals(run4, changeEvent1.change.dataset.runId);

        ((ObjectNode) cd.config).put("filter", "min");
        setTestVariables(test, "Value", new Label("value", schema.id), cd);
        // After changing the variable the past datapoints and changes are removed; we need to recalculate them again
        jsonRequest().post("/api/alerting/recalculate?test=" + test.id).then().statusCode(204);

        for (int i = 0; i < 5; ++i) {
            DataPoint.Event dpe = datapointQueue.poll(10, TimeUnit.SECONDS);
            assertNotNull(dpe);
        }

        // now we'll find a change already at run3
        Change.Event changeEvent2 = changeQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(changeEvent2);
        assertEquals(run3, changeEvent2.change.dataset.runId);

        int run6 = uploadRun(ts + 5, ts + 5, runWithValue(1.5, schema), test.name);
        assertValue(datapointQueue, 1.5);

        // mean of previous values is 1.5, now the min is 1.5 => no change
        assertNull(changeQueue.poll(50, TimeUnit.MILLISECONDS));

        uploadRun(ts + 6, ts + 6, runWithValue(2, schema), test.name);
        assertValue(datapointQueue, 2);

        // mean of previous is 2, the last value doesn't matter (1.5 is lower than 2 - 10%)
        Change.Event changeEvent3 = changeQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(changeEvent3);
        assertEquals(run6, changeEvent3.change.dataset.runId);
    }
}
