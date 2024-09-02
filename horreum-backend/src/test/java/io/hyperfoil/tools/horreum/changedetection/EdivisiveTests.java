package io.hyperfoil.tools.horreum.changedetection;

import static org.junit.jupiter.api.Assertions.*;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.inject.Inject;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.hyperfoil.tools.horreum.api.alerting.Change;
import io.hyperfoil.tools.horreum.api.alerting.ChangeDetection;
import io.hyperfoil.tools.horreum.api.alerting.DataPoint;
import io.hyperfoil.tools.horreum.api.data.Label;
import io.hyperfoil.tools.horreum.api.data.Schema;
import io.hyperfoil.tools.horreum.api.data.changeDetection.ChangeDetectionModelType;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.entity.alerting.ChangeDAO;
import io.hyperfoil.tools.horreum.entity.alerting.DataPointDAO;
import io.hyperfoil.tools.horreum.entity.alerting.VariableDAO;
import io.hyperfoil.tools.horreum.entity.data.DatasetDAO;
import io.hyperfoil.tools.horreum.svc.BaseServiceTest;
import io.hyperfoil.tools.horreum.svc.ServiceMediator;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
@Tag("CiTests")
public class EdivisiveTests extends BaseServiceTest {

    @Inject
    ChangeDetectionModelResolver resolver;

    @Inject
    ServiceMediator serviceMediator;

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

        HunterEDivisiveModel model = (HunterEDivisiveModel) resolver.getModel(ChangeDetectionModelType.EDIVISIVE);
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

            assertFalse(valid);

            tmpFiles.cleanup();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @NotNull
    private static HunterEDivisiveModel.TmpFiles getTmpFiles(String resource) throws IOException {
        HunterEDivisiveModel.TmpFiles tmpFiles = HunterEDivisiveModel.TmpFiles.instance();
        assertTrue(tmpFiles.confFile.exists());

        try (InputStream validCsvStream = EdivisiveTests.class.getClassLoader().getResourceAsStream(resource)) {
            try (OutputStream confOut = new FileOutputStream(tmpFiles.inputFile)) {
                confOut.write(validCsvStream.readAllBytes());
            } catch (IOException e) {
                fail("Could not extract Hunter configuration from archive");
            }
        } catch (IOException e) {
            fail("Could not create temporary file for Hunter eDivisive algorithm", e);
        }
        return tmpFiles;
    }

    @Test
    public void testDetectedChangePoints() {

        HunterEDivisiveModel model = (HunterEDivisiveModel) resolver.getModel(ChangeDetectionModelType.EDIVISIVE);
        assertNotNull(model);

        List<ChangeDAO> changePoints = new ArrayList<>();

        try {
            HunterEDivisiveModel.TmpFiles tmpFiles = getTmpFiles("change/eDivisive/valid/tests/resources/horreum.csv");

            Instant sinceInstant = Instant.ofEpochSecond(1702002504);
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
                    tmpFiles,
                    sinceInstant);

            assertNotEquals(0, changePoints.size());

            assertEquals(1535410, changePoints.get(0).dataset.id);

            tmpFiles.cleanup();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testEdvisiveModelAnalyze(TestInfo info) throws Exception {

        io.hyperfoil.tools.horreum.api.data.Test test = createTest(createExampleTest(getTestName(info)));
        Schema schema = createExampleSchema(info);

        ChangeDetection cd = new ChangeDetection();
        cd.model = ChangeDetectionModelType.names.EDIVISIVE;
        setTestVariables(test, "Value", new Label("value", schema.id), cd);

        BlockingQueue<DataPoint.Event> datapointQueue = serviceMediator.getEventQueue(AsyncEventChannels.DATAPOINT_NEW,
                test.id);
        BlockingQueue<Change.Event> changeQueue = serviceMediator.getEventQueue(AsyncEventChannels.CHANGE_NEW, test.id);

        long ts = System.currentTimeMillis();
        uploadRun(ts, ts, runWithValue(1, schema), test.name);
        assertValue(datapointQueue, 1);
        uploadRun(ts + 1, ts + 1, runWithValue(2, schema), test.name);
        assertValue(datapointQueue, 2);
        uploadRun(ts + 2, ts + 2, runWithValue(1, schema), test.name);
        assertValue(datapointQueue, 1);
        uploadRun(ts + 3, ts + 3, runWithValue(2, schema), test.name);
        assertValue(datapointQueue, 2);
        uploadRun(ts + 3, ts + 3, runWithValue(2, schema), test.name);
        assertValue(datapointQueue, 2);
        uploadRun(ts + 3, ts + 3, runWithValue(1, schema), test.name);
        assertValue(datapointQueue, 1);
        uploadRun(ts + 3, ts + 3, runWithValue(1, schema), test.name);
        assertValue(datapointQueue, 1);
        uploadRun(ts + 3, ts + 3, runWithValue(2, schema), test.name);
        assertValue(datapointQueue, 2);
        uploadRun(ts + 3, ts + 3, runWithValue(2, schema), test.name);
        assertValue(datapointQueue, 2);

        assertNull(changeQueue.poll(50, TimeUnit.MILLISECONDS));

        int run10 = uploadRun(ts + 4, ts + 4, runWithValue(10, schema), test.name);
        assertValue(datapointQueue, 10);

        Change.Event changeEvent1 = changeQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(changeEvent1);

        testSerialization(changeEvent1, Change.Event.class);

        assertEquals(run10, changeEvent1.change.dataset.runId);
        Pattern pattern = Pattern.compile(".*`(?<change>\\+\\d*.\\d%)`.*", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(changeEvent1.change.description);
        boolean matchFound = matcher.find();
        assertTrue(matchFound);
        assertEquals("+542.9%", matcher.group(1));

    }
}
