package io.hyperfoil.tools.horreum.svc;

import static io.hyperfoil.tools.horreum.svc.BaseServiceNoRestTest.DEFAULT_USER;
import static io.hyperfoil.tools.horreum.svc.BaseServiceNoRestTest.FOO_TEAM;
import static io.hyperfoil.tools.horreum.svc.BaseServiceNoRestTest.FOO_TESTER;
import static io.hyperfoil.tools.horreum.svc.BaseServiceNoRestTest.FOO_UPLOADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.Run;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.api.services.RunService;
import io.hyperfoil.tools.horreum.api.services.TestService;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@TestProfile(HorreumTestProfile.class)
@TestTransaction
@TestSecurity(user = DEFAULT_USER, roles = { Roles.TESTER, Roles.VIEWER, Roles.UPLOADER, FOO_TEAM, FOO_TESTER, FOO_UPLOADER })
class RunServiceNoRestTest extends BaseServiceNoRestTest {

    @Inject
    RunService runService;

    @Inject
    TestService testService;

    @Inject
    ObjectMapper objectMapper;

    @org.junit.jupiter.api.Test
    void testUploadRun() {
        Test t1 = createSampleTest("test", null, null, null);

        Test created1 = testService.add(t1);
        assertNotNull(created1.id);
        assertEquals(1, TestDAO.count());

        int runId = uploadRun(created1.id, FOO_TEAM, JsonNodeFactory.instance.objectNode());
        assertEquals(1, RunDAO.count());
        assertNotNull(RunDAO.findById(runId));
    }

    @org.junit.jupiter.api.Test
    void testTrashRun() {
        Test t1 = createSampleTest("test", null, null, null);

        Test created1 = testService.add(t1);
        assertNotNull(created1.id);
        assertEquals(1, TestDAO.count());

        int runId = uploadRun(created1.id, FOO_TEAM, JsonNodeFactory.instance.objectNode());
        assertEquals(1, RunDAO.count());
        RunDAO run = RunDAO.findById(runId);
        assertFalse(run.trashed);

        runService.trash(runId, true);
        assertTrue(run.trashed);
    }

    @org.junit.jupiter.api.Test
    void testTrashAlreadyTrashedRun() {
        Test t1 = createSampleTest("test", null, null, null);

        Test created1 = testService.add(t1);
        assertNotNull(created1.id);
        assertEquals(1, TestDAO.count());

        int runId = uploadRun(created1.id, FOO_TEAM, JsonNodeFactory.instance.objectNode());
        assertEquals(1, RunDAO.count());
        RunDAO run = RunDAO.findById(runId);
        assertFalse(run.trashed);

        runService.trash(runId, true);
        assertTrue(run.trashed);

        // trash again the same trashed run
        runService.trash(runId, true);
        assertTrue(run.trashed);
    }

    // utility to create a sample test and add to Horreum
    private Test addTest(String name, String owner, String folder, Integer datastoreId) {
        Test test = createSampleTest(name, owner, folder, datastoreId);
        return testService.add(test);
    }

    // utility to create a sample test and add to Horreum
    private int uploadRun(int testId, String owner, JsonNode runData) {
        Run run = createSampleRun(testId, runData, owner);

        List<Integer> runIds = runService.add(String.valueOf(testId), owner, Access.PUBLIC, run);
        assertEquals(1, runIds.size());
        return runIds.get(0);
    }
}
