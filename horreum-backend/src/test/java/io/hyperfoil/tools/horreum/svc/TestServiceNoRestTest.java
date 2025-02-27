package io.hyperfoil.tools.horreum.svc;

import static io.hyperfoil.tools.horreum.svc.BaseServiceNoRestTest.DEFAULT_USER;
import static io.hyperfoil.tools.horreum.svc.BaseServiceNoRestTest.FOO_TEAM;
import static io.hyperfoil.tools.horreum.svc.BaseServiceNoRestTest.FOO_TESTER;
import static io.hyperfoil.tools.horreum.svc.BaseServiceNoRestTest.FOO_UPLOADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Disabled;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.Run;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.api.services.RunService;
import io.hyperfoil.tools.horreum.api.services.TestService;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@TestProfile(HorreumTestProfile.class)
@TestTransaction
@TestSecurity(user = DEFAULT_USER, roles = { Roles.TESTER, Roles.VIEWER, FOO_TEAM, FOO_TESTER })
class TestServiceNoRestTest extends BaseServiceNoRestTest {

    @Inject
    TestService testService;

    @Inject
    RunService runService;

    @Inject
    ObjectMapper objectMapper;

    @org.junit.jupiter.api.Test
    void testCreateTest() {
        Test t1 = createSampleTest("test", null, null, null);
        Test t2 = createSampleTest("1234", null, null, null);

        Test created1 = testService.add(t1);
        assertNotNull(created1.id);
        assertEquals(1, TestDAO.count());
        TestDAO test1 = TestDAO.findById(created1.id);
        assertEquals(t1.name, test1.name);
        assertEquals(FOO_TEAM, test1.owner);
        assertEquals(Access.PUBLIC, test1.access);
        assertEquals(true, test1.notificationsEnabled);
        assertNull(test1.folder);

        Test created2 = testService.add(t2);
        assertNotNull(created2.id);
        assertEquals(2, TestDAO.count());
        TestDAO test2 = TestDAO.findById(created2.id);
        assertEquals(t2.name, test2.name);
        assertEquals(FOO_TEAM, test2.owner);
        assertEquals(Access.PUBLIC, test2.access);
        assertEquals(true, test2.notificationsEnabled);
        assertNull(test2.folder);
    }

    @org.junit.jupiter.api.Test
    void testCreateTestWithFolders() {
        Test t1 = createSampleTest("test", null, "folder/trailing/slash/", null);
        Test t2 = createSampleTest("1234", null, " folder/with/spaces  ", null);
        Test t3 = createSampleTest("another-test", null, " folder/with/spaces/trailing/slash  /  ", null);
        Test t4 = createSampleTest("single-slash", null, "  /  ", null);

        TestDAO test1 = TestDAO.findById(testService.add(t1).id);
        assertEquals(1, TestDAO.count());
        assertEquals("folder/trailing/slash", test1.folder);

        TestDAO test2 = TestDAO.findById(testService.add(t2).id);
        assertEquals(2, TestDAO.count());
        assertEquals("folder/with/spaces", test2.folder);

        TestDAO test3 = TestDAO.findById(testService.add(t3).id);
        assertEquals(3, TestDAO.count());
        assertEquals("folder/with/spaces/trailing/slash", test3.folder);

        TestDAO test4 = TestDAO.findById(testService.add(t4).id);
        assertEquals(4, TestDAO.count());
        assertNull(test4.folder);
    }

    @org.junit.jupiter.api.Test
    void testCreateTestWithBlankName() {
        Test t = createSampleTest("", null, null, null);

        // empty test name
        ServiceException thrown = assertThrows(ServiceException.class, () -> testService.add(t));
        assertEquals("Test name can not be empty", thrown.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());

        // null test name
        t.name = null;
        thrown = assertThrows(ServiceException.class, () -> testService.add(t));
        assertEquals("Test name can not be empty", thrown.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testCreateTestWithMissingRole() {
        Test t = createSampleTest("test", "missing-role", null, null);

        ServiceException thrown = assertThrows(ServiceException.class, () -> testService.add(t));
        assertEquals(String.format("This user does not have the %s role!", t.owner), thrown.getMessage());
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), thrown.getResponse().getStatus());
    }

    @TestSecurity(user = DEFAULT_USER)
    @org.junit.jupiter.api.Test
    void testCreateTestForbidden() {
        Test t = createSampleTest("test", null, null, null);
        assertThrows(ForbiddenException.class, () -> testService.add(t));
    }

    @TestSecurity()
    @org.junit.jupiter.api.Test
    void testCreateTestUnauthorized() {
        Test t = createSampleTest("test", null, null, null);
        assertThrows(UnauthorizedException.class, () -> testService.add(t));
    }

    @org.junit.jupiter.api.Test
    void testCreateTestWithWildcardFolder() {
        Test t = createSampleTest("test", null, null, null);
        t.folder = "*";
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> testService.add(t));
        assertEquals("Illegal folder name '*': this is used as wildcard.", thrown.getMessage());
    }

    @org.junit.jupiter.api.Test
    void testCreateTestWithExistingName() {
        Test t = createSampleTest("test", null, null, null);

        Test created = testService.add(t);
        assertNotNull(created.id);
        assertEquals(1, TestDAO.count());

        // try to create the same test without id, it will try to create a different test
        ServiceException thrown = assertThrows(ServiceException.class, () -> testService.add(t));
        assertEquals("Could not persist test due to another test.", thrown.getMessage());
        assertEquals(Response.Status.CONFLICT.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testCheckTestExists() {
        Test t1 = createSampleTest("test", null, null, null);
        Test t2 = createSampleTest("1234", null, null, null);

        Test created1 = testService.add(t1);
        assertNotNull(created1.id);
        Test created2 = testService.add(t2);
        assertNotNull(created2.id);

        assertTrue(((TestServiceImpl) testService).checkTestExists(created1.id));
        assertTrue(((TestServiceImpl) testService).checkTestExists(created2.id));
        assertFalse(((TestServiceImpl) testService).checkTestExists(9999));
    }

    @org.junit.jupiter.api.Test
    void testUpdateTest() {
        Test t = createSampleTest("test", null, null, null);

        Test created = testService.add(t);
        assertNotNull(created.id);
        assertEquals(1, TestDAO.count());
        TestDAO test = TestDAO.findById(created.id);
        assertEquals(t.name, test.name);
        assertEquals(FOO_TEAM, test.owner);
        assertEquals(Access.PUBLIC, test.access);
        assertEquals(true, test.notificationsEnabled);

        created.name = "differentName";
        created.notificationsEnabled = false;

        Test updated = testService.update(created);
        assertEquals(1, TestDAO.count());
        test = TestDAO.findById(updated.id);
        assertEquals(created.name, test.name);
        assertEquals(FOO_TEAM, test.owner);
        assertEquals(Access.PUBLIC, test.access);
        assertEquals(false, test.notificationsEnabled);
    }

    @org.junit.jupiter.api.Test
    void testUpdateNotExistingTest() {
        Test t = createSampleTest("test", null, null, null);

        Test created = testService.add(t);
        assertNotNull(created.id);
        assertEquals(1, TestDAO.count());
        TestDAO test = TestDAO.findById(created.id);
        assertEquals(t.name, test.name);
        assertEquals(FOO_TEAM, test.owner);
        assertEquals(Access.PUBLIC, test.access);
        assertEquals(true, test.notificationsEnabled);

        // change to a non-existing id
        created.id = 999;
        ServiceException thrown = assertThrows(ServiceException.class, () -> testService.update(created));
        assertEquals("Missing test id or test with id 999 does not exist", thrown.getMessage());
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), thrown.getResponse().getStatus());

        // change to a null id
        created.id = null;
        thrown = assertThrows(ServiceException.class, () -> testService.update(created));
        assertEquals("Missing test id or test with id null does not exist", thrown.getMessage());
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testGetTestByName() {
        String testName = "MyTest1";
        Test created = addTest(testName, null, null, null);

        assertNotNull(created.id);
        assertEquals(1, TestDAO.count());

        Test retrieved = testService.getByNameOrId(testName);
        assertEquals(created.id, retrieved.id);
        assertEquals(created.name, retrieved.name);
    }

    @org.junit.jupiter.api.Test
    void testGetTestByNumericName() {
        String testName = "1234";
        Test created = addTest(testName, null, null, null);

        assertNotNull(created.id);
        assertEquals(1, TestDAO.count());

        Test retrieved = testService.getByNameOrId(testName);
        assertEquals(created.id, retrieved.id);
        assertEquals(created.name, retrieved.name);
    }

    @org.junit.jupiter.api.Test
    void testGetTestById() {
        String testName = "MyTest";
        Test created = addTest(testName, null, null, null);

        assertNotNull(created.id);
        assertEquals(1, TestDAO.count());

        Test retrieved = testService.getByNameOrId(String.valueOf(created.id));
        assertEquals(created.id, retrieved.id);
        assertEquals(created.name, retrieved.name);
    }

    @org.junit.jupiter.api.Test
    void testGetTestByNameOrIdNotFound() {
        ServiceException thrown = assertThrows(ServiceException.class, () -> testService.getByNameOrId("NotFoundTest"));
        assertEquals("No test with name or id NotFoundTest", thrown.getMessage());
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), thrown.getResponse().getStatus());
    }

    @Disabled("Because of https://github.com/Hyperfoil/Horreum/issues/1670")
    @org.junit.jupiter.api.Test
    void testGetTestByNameOrIdWithCollision() {
        String testName = "MyTest";
        Test created1 = addTest(testName, null, null, null);
        // create another test having as name the id of the previous one
        Test created2 = addTest(String.valueOf(created1.id), null, null, null);

        // FIXME: the result would depend on the order the underlying query would give us
        Test retrieved = testService.getByNameOrId(String.valueOf(created1.id));
        assertEquals(created2.id, retrieved.id);
    }

    @org.junit.jupiter.api.Test
    void testListTestsWithPagination() {
        addTest("test1", FOO_TEAM, "folder1", null);
        addTest("test2", FOO_TEAM, "folder2", null);
        addTest("test3", FOO_TEAM, "folder2/folder21", null);
        addTest("test4", FOO_TEAM, "folder1", null);
        addTest("test5", FOO_TEAM, "folder3/folder31", null);

        assertEquals(5, TestDAO.count());

        TestService.TestQueryResult result = testService.list(null, null, null, "name",
                SortDirection.Ascending);
        assertEquals(5, result.count);
        assertEquals(5, result.tests.size());

        // page 0 limited to 3 results
        result = testService.list(null, 3, 0, null, SortDirection.Ascending);
        assertEquals(5, result.count);
        assertEquals(3, result.tests.size());

        // page 1 limited to 3 results -> only 2 left
        result = testService.list(null, 3, 1, null, SortDirection.Ascending);
        assertEquals(5, result.count);
        assertEquals(2, result.tests.size());

        // page 2 limited to 3 results -> no more left
        result = testService.list(null, 3, 2, null, SortDirection.Ascending);
        assertEquals(5, result.count);
        assertEquals(0, result.tests.size());
    }

    @org.junit.jupiter.api.Test
    void testListTestsWithOrdering() {
        addTest("test5", FOO_TEAM, "folder3/folder31", null);
        addTest("test1", FOO_TEAM, "folder1", null);
        addTest("test2", FOO_TEAM, "folder2", null);
        addTest("test3", FOO_TEAM, "folder2/folder21", null);
        addTest("test4", FOO_TEAM, "folder1", null);

        assertEquals(5, TestDAO.count());

        // name ascending
        TestService.TestQueryResult result = testService.list(null, null, null, "name", SortDirection.Ascending);
        assertEquals(5, result.count);
        assertEquals(5, result.tests.size());
        assertEquals("test1", result.tests.get(0).name);
        assertEquals("test5", result.tests.get(4).name);

        // name descending
        result = testService.list(null, null, null, "name", SortDirection.Descending);
        assertEquals(5, result.count);
        assertEquals(5, result.tests.size());
        assertEquals("test5", result.tests.get(0).name);
        assertEquals("test1", result.tests.get(4).name);

        // folder descending
        result = testService.list(null, null, null, "folder", SortDirection.Descending);
        assertEquals(5, result.count);
        assertEquals(5, result.tests.size());
        assertEquals("folder3/folder31", result.tests.get(0).folder);
        assertEquals("folder1", result.tests.get(4).folder);
    }

    @org.junit.jupiter.api.Test
    void testSummaryWithPagination() {
        addTest("test1", FOO_TEAM, "folder1", null);
        addTest("test2", FOO_TEAM, "folder2", null);
        addTest("test3", FOO_TEAM, "folder2/folder21", null);
        addTest("test4", FOO_TEAM, "folder1", null);
        addTest("test5", FOO_TEAM, "folder3/folder31", null);
        addTest("test6", FOO_TEAM, null, null);
        addTest("test7", FOO_TEAM, null, null);
        addTest("test8", FOO_TEAM, null, null);

        assertEquals(8, TestDAO.count());

        // 2 tests in the root folder
        TestService.TestListing result = testService.summary(null, null, 20, 0, null, null);
        assertEquals(3, result.count);
        assertEquals(3, result.tests.size());

        // page set to 0, always return all
        result = testService.summary(null, null, 2, 0, null, null);
        assertEquals(3, result.count);
        assertEquals(3, result.tests.size());

        // page 1 limited to 2 results
        result = testService.summary(null, null, 2, 1, null, null);
        assertEquals(3, result.count);
        assertEquals(2, result.tests.size());

        // page 2 limited to 2 results -> only 1 left
        result = testService.summary(null, null, 2, 2, null, null);
        assertEquals(3, result.count);
        assertEquals(1, result.tests.size());

        // page 3 limited to 2 results -> no more left
        result = testService.summary(null, null, 2, 3, null, null);
        assertEquals(3, result.count);
        assertEquals(0, result.tests.size());
    }

    @org.junit.jupiter.api.Test
    void testSummaryInDifferentFolders() {
        addTest("test1", FOO_TEAM, "folder1", null);
        addTest("test2", FOO_TEAM, "folder2", null);
        addTest("test3", FOO_TEAM, "folder2/folder21", null);
        addTest("test4", FOO_TEAM, "folder1", null);
        addTest("test5", FOO_TEAM, "folder3/folder31", null);
        addTest("test6", FOO_TEAM, null, null);
        addTest("test7", FOO_TEAM, null, null);
        addTest("test8", FOO_TEAM, null, null);

        assertEquals(8, TestDAO.count());

        // 2 tests in the root folder
        TestService.TestListing result = testService.summary(null, null, 20, 0, null, null);
        assertEquals(3, result.count);
        assertEquals(3, result.tests.size());

        // folder folder1
        result = testService.summary(null, "folder1", 20, 0, null, null);
        assertEquals(2, result.count);
        assertEquals(2, result.tests.size());

        // folder folder3/folder31
        result = testService.summary(null, "folder3/folder31", 20, 0, null, null);
        assertEquals(1, result.count);
        assertEquals(1, result.tests.size());
    }

    @org.junit.jupiter.api.Test
    void testSummaryWithOrdering() {
        addTest("test1", FOO_TEAM, "folder1", null);
        addTest("test2", FOO_TEAM, "folder2", null);
        addTest("test3", FOO_TEAM, "folder2/folder21", null);
        addTest("test4", FOO_TEAM, "folder1", null);
        addTest("test5", FOO_TEAM, "folder3/folder31", null);
        addTest("test6", FOO_TEAM, null, null);
        addTest("test7", FOO_TEAM, null, null);
        addTest("test8", FOO_TEAM, null, null);

        assertEquals(8, TestDAO.count());

        // default is descending order
        TestService.TestListing result = testService.summary(null, null, 20, 1, null, null);
        assertEquals(3, result.count);
        assertEquals(3, result.tests.size());
        assertEquals("test8", result.tests.get(0).name);

        // explicitly setting descending order
        result = testService.summary(null, null, 20, 1, SortDirection.Descending, null);
        assertEquals(3, result.count);
        assertEquals(3, result.tests.size());
        assertEquals("test8", result.tests.get(0).name);

        // changing to ascending order
        result = testService.summary(null, null, 20, 1, SortDirection.Ascending, null);
        assertEquals(3, result.count);
        assertEquals(3, result.tests.size());
        assertEquals("test6", result.tests.get(0).name);

        // ordering ignored when page is set to 0
        result = testService.summary(null, null, 20, 0, SortDirection.Descending, null);
        assertEquals(3, result.count);
        assertEquals(3, result.tests.size());
        assertEquals("test6", result.tests.get(0).name);
    }

    @org.junit.jupiter.api.Test
    void testSummaryWithFiltering() {
        addTest("test1", FOO_TEAM, "folder1", null);
        addTest("test2", FOO_TEAM, "folder2", null);
        addTest("test3", FOO_TEAM, "folder2/folder21", null);
        addTest("test4", FOO_TEAM, "folder1", null);
        addTest("test5", FOO_TEAM, "folder3/folder31", null);
        addTest("test6", FOO_TEAM, null, null);
        addTest("test7", FOO_TEAM, null, null);
        addTest("test8", FOO_TEAM, null, null);

        assertEquals(8, TestDAO.count());

        // name filter null -> return all under that folder
        TestService.TestListing result = testService.summary(null, null, 20, 1, null, null);
        assertEquals(3, result.count);
        assertEquals(3, result.tests.size());

        // filter by exact match
        result = testService.summary(null, null, 20, 1, null, "test6");
        assertEquals(3, result.count);
        assertEquals(1, result.tests.size());
        assertEquals("test6", result.tests.get(0).name);

        // filter by exact match with no results as in different folder
        result = testService.summary(null, null, 20, 1, null, "test1");
        assertEquals(3, result.count);
        assertEquals(0, result.tests.size());

        // filter by exact match in different folder
        result = testService.summary(null, "folder1", 20, 1, null, "test1");
        assertEquals(2, result.count);
        assertEquals(1, result.tests.size());
        assertEquals("test1", result.tests.get(0).name);

        // filter by partial match
        result = testService.summary(null, null, 20, 1, null, "est");
        assertEquals(3, result.count);
        assertEquals(3, result.tests.size());

        // filter by partial match case insesitive
        result = testService.summary(null, null, 20, 1, null, "EsT");
        assertEquals(3, result.count);
        assertEquals(3, result.tests.size());
    }

    @org.junit.jupiter.api.Test
    void testDeleteTest() {
        Test created1 = addTest("test", null, null, null);
        Test created2 = addTest("1234", null, null, null);

        assertNotNull(created1.id);
        assertNotNull(created2.id);
        assertEquals(2, TestDAO.count());

        testService.delete(created1.id);

        assertEquals(1, TestDAO.count());
        assertNull(TestDAO.findById(created1.id));
        assertNotNull(TestDAO.findById(created2.id));
    }

    @org.junit.jupiter.api.Test
    void testDeleteTestNotFound() {
        ServiceException thrown = assertThrows(ServiceException.class, () -> testService.delete(999));
        assertEquals("No test with id 999", thrown.getMessage());
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), thrown.getResponse().getStatus());
    }

    @TestSecurity(user = DEFAULT_USER, roles = { Roles.TESTER, Roles.VIEWER, Roles.UPLOADER, FOO_TEAM, FOO_TESTER,
            FOO_UPLOADER })
    @org.junit.jupiter.api.Test
    void testDeleteTestWithRun() {
        Test created1 = addTest("test", null, null, null);
        assertNotNull(created1.id);
        assertEquals(1, TestDAO.count());

        Run run1 = createSampleRun(created1.id, JsonNodeFactory.instance.objectNode(), FOO_TEAM);
        int runId;
        try (Response resp = runService.add(created1.name, FOO_TEAM, Access.PUBLIC, run1)) {
            assertEquals(Response.Status.ACCEPTED.getStatusCode(), resp.getStatus());
            runId = Integer.parseInt(resp.getEntity().toString());
        }
        assertEquals(1, RunDAO.count());

        // flush data
        em.clear();

        testService.delete(created1.id);
        assertEquals(0, TestDAO.count());

        // atm when a test is deleted, its runs are simply trashed
        assertEquals(1, RunDAO.count());
        RunDAO persistedRun = RunDAO.findById(runId);
        assertNotNull(persistedRun);
        assertTrue(persistedRun.trashed);
    }

    @TestSecurity(user = DEFAULT_USER, roles = { Roles.TESTER, Roles.VIEWER, Roles.UPLOADER, FOO_TEAM, FOO_TESTER,
            FOO_UPLOADER })
    @org.junit.jupiter.api.Test
    void testDeleteTestWithAlreadyTrashedRun() {
        Test created1 = addTest("test", null, null, null);
        assertNotNull(created1.id);
        assertEquals(1, TestDAO.count());

        Run run1 = createSampleRun(created1.id, JsonNodeFactory.instance.objectNode(), FOO_TEAM);
        int runId;
        try (Response resp = runService.add(created1.name, FOO_TEAM, Access.PUBLIC, run1)) {
            assertEquals(Response.Status.ACCEPTED.getStatusCode(), resp.getStatus());
            runId = Integer.parseInt(resp.getEntity().toString());
        }
        assertEquals(1, RunDAO.count());

        // flush data
        em.clear();

        // trash the run
        runService.trash(runId, true);

        testService.delete(created1.id);
        assertEquals(0, TestDAO.count());

        // atm when a test is deleted, its runs are simply trashed
        assertEquals(1, RunDAO.count());
        RunDAO persistedRun = RunDAO.findById(runId);
        assertNotNull(persistedRun);
        assertTrue(persistedRun.trashed);
    }

    @TestSecurity(user = DEFAULT_USER, roles = { Roles.TESTER, Roles.VIEWER, FOO_TEAM, FOO_TESTER, FOO_UPLOADER })
    @org.junit.jupiter.api.Test
    void testEnsureTestExists() {
        String testName = "MyTest";
        addTest(testName, null, null, null);

        TestDAO test = ((TestServiceImpl) testService).ensureTestExists(testName);
        assertNotNull(test);
    }

    @org.junit.jupiter.api.Test
    void testEnsureTestExistsMissingUploaderRole() {
        String testName = "MyTest";
        addTest(testName, null, null, null);

        ServiceException thrown = assertThrows(ServiceException.class,
                () -> ((TestServiceImpl) testService).ensureTestExists(testName));
        assertEquals("Cannot upload to test " + testName, thrown.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testEnsureTestExistsMissingUploaderRoleAndWrongToken() {
        String testName = "MyTest";
        Test test = createSampleTest(testName, null, null, null);
        testService.add(test);

        ServiceException thrown = assertThrows(ServiceException.class,
                () -> ((TestServiceImpl) testService).ensureTestExists(testName));
        assertEquals("Cannot upload to test " + testName, thrown.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testEnsureTestExistsFailure() {
        ServiceException thrown = assertThrows(ServiceException.class,
                () -> ((TestServiceImpl) testService).ensureTestExists("NotExisting"));
        assertEquals("Cannot upload to test NotExisting", thrown.getMessage());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testGetFolders() {
        addTest("test1", FOO_TEAM, "folder1", null);
        addTest("test2", FOO_TEAM, "folder2", null);
        addTest("test3", FOO_TEAM, "folder2/folder21", null);
        addTest("test4", FOO_TEAM, "folder1", null);
        addTest("test5", FOO_TEAM, "folder3/folder31", null);

        assertEquals(5, TestDAO.count());

        List<String> folders = testService.folders(null);
        // 5 distinct folders + null (the root)
        assertEquals(6, folders.size());
        assertNull(folders.get(0));

        assertEquals(List.of("folder1", "folder2", "folder2/folder21", "folder3", "folder3/folder31"),
                folders.stream().filter(Objects::nonNull).toList());

        folders = testService.folders(Roles.MY_ROLES);
        // 5 distinct folders + null (the root)
        assertEquals(6, folders.size());
        assertNull(folders.get(0));

        folders = testService.folders(BAR_TEAM);
        // only the root folder is returned (represented by null)
        assertEquals(1, folders.size());
        assertNull(folders.get(0));
    }

    @org.junit.jupiter.api.Test
    void testUpdateTestAccess() {
        String testName = "MyTest";

        Test t = addTest(testName, null, null, null);

        TestDAO test = TestDAO.findById(t.id);
        assertEquals(Access.PUBLIC, test.access);

        testService.updateAccess(test.id, test.owner, Access.PRIVATE);

        test = TestDAO.findById(t.id);
        assertEquals(Access.PRIVATE, test.access);
    }

    @org.junit.jupiter.api.Test
    void testUpdateAccessWithTestNotFound() {
        ServiceException thrown = assertThrows(ServiceException.class,
                () -> testService.updateAccess(999, FOO_TEAM, Access.PRIVATE));
        assertEquals("Test not found", thrown.getMessage());
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testUpdateTestAccessWithWrongOwner() {
        Test t = addTest("testName", null, null, null);
        ServiceException thrown = assertThrows(ServiceException.class,
                () -> testService.updateAccess(t.id, BAR_TEAM, Access.PRIVATE));
        assertEquals("Access change failed (missing permissions?)", thrown.getMessage());
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), thrown.getResponse().getStatus());
    }

    @org.junit.jupiter.api.Test
    void testUpdateTestNotification() {
        String testName = "MyTest";

        Test t = addTest(testName, null, null, null);

        TestDAO test = TestDAO.findById(t.id);
        assertEquals(true, test.notificationsEnabled);

        testService.updateNotifications(test.id, false);

        test = TestDAO.findById(t.id);
        assertEquals(false, test.notificationsEnabled);
    }

    @org.junit.jupiter.api.Test
    void testUpdateTestFolder() {
        Test t = addTest("MyTest", null, "", null);
        TestDAO test = TestDAO.findById(t.id);
        assertNull(test.folder);

        testService.updateFolder(test.id, "folder1/folder2");

        test = TestDAO.findById(t.id);
        assertEquals("folder1/folder2", test.folder);
    }

    @org.junit.jupiter.api.Test
    void testUpdateTestFolderWithWildcardFailure() {
        Test t = addTest("MyTest", null, "", null);
        TestDAO test = TestDAO.findById(t.id);
        assertNull(test.folder);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> testService.updateFolder(t.id, "*"));
        assertEquals("Illegal folder name '*': this is used as wildcard.", thrown.getMessage());

        thrown = assertThrows(IllegalArgumentException.class,
                () -> testService.updateFolder(t.id, "*/"));
        assertEquals("Illegal folder name '*': this is used as wildcard.", thrown.getMessage());

        test = TestDAO.findById(t.id);
        assertNull(test.folder);
    }

    @org.junit.jupiter.api.Test
    void testImportTestWithValidStructure() throws JsonProcessingException {
        String testImport = """
                {
                  "access": "PUBLIC",
                  "owner": "TEAM_NAME",
                  "name": "Quarkus - config-quickstart - JVM",
                  "folder": "quarkus",
                  "description": "",
                  "datastoreId": null,
                  "timelineLabels": [],
                  "timelineFunction": null,
                  "fingerprintLabels": [
                    "buildType"
                  ],
                  "fingerprintFilter": null,
                  "compareUrl": null,
                  "transformers": [],
                  "notificationsEnabled": true,
                  "variables": [],
                  "missingDataRules": [],
                  "experiments": [],
                  "actions": [],
                  "subscriptions": null,
                  "datastore": null
                }
                """;

        ObjectNode testJson = (ObjectNode) objectMapper.readTree(testImport.replaceAll("TEAM_NAME", FOO_TEAM));
        testService.importTest(testJson);

    }

    @org.junit.jupiter.api.Test
    void testImportTestWithIncorrectTeam() throws JsonProcessingException {
        String testImport = """
                {
                  "access": "PUBLIC",
                  "owner": "perf-team",
                  "id": 14,
                  "name": "Quarkus - config-quickstart - JVM",
                  "folder": "quarkus",
                  "description": "",
                  "datastoreId": null,
                  "timelineLabels": [],
                  "timelineFunction": null,
                  "fingerprintLabels": [
                    "buildType"
                  ],
                  "fingerprintFilter": null,
                  "compareUrl": null,
                  "transformers": [],
                  "notificationsEnabled": true,
                  "variables": [],
                  "missingDataRules": [],
                  "experiments": [],
                  "actions": [],
                  "subscriptions": {},
                  "datastore": null
                }
                """;

        ObjectNode testJson = (ObjectNode) objectMapper.readTree(testImport);

        ServiceException thrown = assertThrows(ServiceException.class, () -> testService.importTest(testJson));
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), thrown.getResponse().getStatus());
        assertEquals("This user does not have the perf-team role!", thrown.getMessage());

    }

    @org.junit.jupiter.api.Test
    void testImporttestWithInvalidStructure() throws JsonProcessingException {
        String testImport = """
                {
                  "accccess": "PUBLIC",
                  "ownerrr": "TEAM_NAME",
                  "name": "Quarkus - config-quickstart - JVM",
                  "folder": "quarkus",
                  "description": "",
                  "datastoreId": null,
                  "timelineLabels": [],
                  "timelineFunction": null,
                  "fingerprintLabels": [
                    "buildType"
                  ],
                  "fingerprintFilter": null,
                  "compareUrl": null,
                  "transformers": [],
                  "notificationsEnabled": true,
                  "variables": [],
                  "missingDataRules": [],
                  "experiments": [],
                  "actions": [],
                  "subscriptions": null,
                  "datastore": null
                }
                """;

        ObjectNode testJson = (ObjectNode) objectMapper.readTree(testImport.replaceAll("TEAM_NAME", FOO_TEAM));

        ServiceException thrown = assertThrows(ServiceException.class, () -> testService.importTest(testJson));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());

    }

    // utility to create a sample test and add to Horreum
    private Test addTest(String name, String owner, String folder, Integer datastoreId) {
        Test test = createSampleTest(name, owner, folder, datastoreId);
        return testService.add(test);
    }
}
