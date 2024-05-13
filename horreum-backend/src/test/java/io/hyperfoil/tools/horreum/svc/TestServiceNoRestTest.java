package io.hyperfoil.tools.horreum.svc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.api.data.TestToken;
import io.hyperfoil.tools.horreum.api.services.TestService;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.entity.data.TestTokenDAO;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Disabled;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static io.hyperfoil.tools.horreum.svc.BaseServiceNoRestTest.DEFAULT_USER;
import static io.hyperfoil.tools.horreum.svc.BaseServiceNoRestTest.FOO_TEAM;
import static io.hyperfoil.tools.horreum.svc.BaseServiceNoRestTest.FOO_TESTER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@TestProfile(HorreumTestProfile.class)
@TestTransaction
@TestSecurity(user = DEFAULT_USER, roles = {Roles.TESTER, Roles.VIEWER, FOO_TEAM, FOO_TESTER})
class TestServiceNoRestTest extends BaseServiceNoRestTest {

   @Inject
   TestService testService;

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

      Test updated = testService.add(created);
      assertEquals(1, TestDAO.count());
      test = TestDAO.findById(updated.id);
      assertEquals(created.name, test.name);
      assertEquals(FOO_TEAM, test.owner);
      assertEquals(Access.PUBLIC, test.access);
      assertEquals(false, test.notificationsEnabled);
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

   @TestSecurity(user = DEFAULT_USER, roles = {Roles.TESTER, Roles.VIEWER, FOO_TEAM, FOO_TESTER, FOO_UPLOADER})
   @org.junit.jupiter.api.Test
   void testEnsureTestExists() {
      String testName = "MyTest";
      addTest(testName, null, null, null);

      TestDAO test = ((TestServiceImpl) testService).ensureTestExists(testName, null);
      assertNotNull(test);
   }

   @org.junit.jupiter.api.Test
   void testEnsureTestExistsMissingUploaderRoleButWithToken() {
      String testName = "MyTest";

      TestToken token = new TestToken();
      token.setValue("my-token");
      token.description = "My awesome token";
      token.permissions = TestTokenDAO.UPLOAD; // uploader

      Test test = createSampleTest(testName, null, null, null);
      test.tokens.add(token);
      testService.add(test);

      TestDAO retrieved = ((TestServiceImpl) testService).ensureTestExists(testName, token.getValue());
      assertNotNull(retrieved);
   }

   @org.junit.jupiter.api.Test
   void testEnsureTestExistsMissingUploaderRole() {
      String testName = "MyTest";
      addTest(testName, null, null, null);

      ServiceException thrown = assertThrows(ServiceException.class,
            () -> ((TestServiceImpl) testService).ensureTestExists(testName, null));
      assertEquals("Cannot upload to test " + testName, thrown.getMessage());
      assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
   }

   @org.junit.jupiter.api.Test
   void testEnsureTestExistsMissingUploaderRoleAndWrongToken() {
      String testName = "MyTest";

      TestToken token = new TestToken();
      token.setValue("my-token");
      token.description = "My awesome token";
      token.permissions = TestTokenDAO.READ; // only read, missing uploader

      Test test = createSampleTest(testName, null, null, null);
      test.tokens.add(token);
      testService.add(test);

      ServiceException thrown = assertThrows(ServiceException.class,
            () -> ((TestServiceImpl) testService).ensureTestExists(testName, null));
      assertEquals("Cannot upload to test " + testName, thrown.getMessage());
      assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
   }

   @org.junit.jupiter.api.Test
   void testEnsureTestExistsFailure() {
      ServiceException thrown = assertThrows(ServiceException.class,
            () -> ((TestServiceImpl) testService).ensureTestExists("NotExisting", null));
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
   void testAddToken() {
      String testName = "MyTest";

      Test test = addTest(testName, null, null, null);
      assertEquals(0, test.tokens.size());

      TestToken token = new TestToken();
      token.setValue("my-token");
      token.description = "My awesome token";
      token.permissions = TestTokenDAO.READ;
      int tokenId = testService.addToken(test.id, token);

      TestDAO retrieved = TestDAO.findById(test.id);
      assertNotNull(retrieved);
      assertEquals(1, retrieved.tokens.size());
      assertEquals(tokenId, retrieved.tokens.stream().findFirst().orElse(new TestTokenDAO()).id);
   }

   @org.junit.jupiter.api.Test
   void testAddTokenWithMissingReadFailure() {
      String testName = "MyTest";

      Test test = addTest(testName, null, null, null);
      assertEquals(0, test.tokens.size());

      TestToken token = new TestToken();
      token.setValue("my-token");
      token.description = "My awesome token";
      token.permissions = TestTokenDAO.UPLOAD;

      ServiceException thrown = assertThrows(ServiceException.class, () -> testService.addToken(test.id, token));
      assertEquals("Upload permission requires read permission as well.", thrown.getMessage());
      assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
   }

   @org.junit.jupiter.api.Test
   void testAddTokenWithMissingTest() {
      TestToken token = new TestToken();
      token.setValue("my-token");
      token.description = "My awesome token";
      token.permissions = TestTokenDAO.READ + TestTokenDAO.UPLOAD;

      ServiceException thrown = assertThrows(ServiceException.class, () -> testService.addToken(999, token));
      assertEquals("Test 999 was not found", thrown.getMessage());
      assertEquals(Response.Status.NOT_FOUND.getStatusCode(), thrown.getResponse().getStatus());
   }

   @org.junit.jupiter.api.Test
   void testListTokens() {
      String testName = "MyTest";

      Test test = addTest(testName, null, null, null);
      assertEquals(0, test.tokens.size());

      TestToken token = new TestToken();
      token.setValue("my-token");
      token.description = "My awesome token";
      token.permissions = TestTokenDAO.READ;
      testService.addToken(test.id, token);
      token.setValue("my-second-token");
      token.description = "My second awesome token";
      token.permissions = TestTokenDAO.UPLOAD + TestTokenDAO.READ;
      testService.addToken(test.id, token);

      TestDAO retrieved = TestDAO.findById(test.id);
      assertEquals(2, retrieved.tokens.size());

      Collection<TestToken> tokens = testService.tokens(test.id);
      assertEquals(retrieved.tokens.size(), tokens.size());
   }

   @org.junit.jupiter.api.Test
   void testDropToken() {
      String testName = "MyTest";

      Test test = addTest(testName, null, null, null);
      assertEquals(0, test.tokens.size());

      TestToken token = new TestToken();
      token.setValue("my-token");
      token.description = "My awesome token";
      token.permissions = TestTokenDAO.READ;
      int tokenId = testService.addToken(test.id, token);

      TestDAO retrieved = TestDAO.findById(test.id);
      assertEquals(1, retrieved.tokens.size());

      testService.dropToken(test.id, tokenId);

      retrieved = TestDAO.findById(test.id);
      assertEquals(0, retrieved.tokens.size());
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
      String testImport  = """
              {
                "access": "PUBLIC",
                "owner": "TEAM_NAME",
                "name": "Quarkus - config-quickstart - JVM",
                "folder": "quarkus",
                "description": "",
                "datastoreId": null,
                "tokens": null,
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

      ObjectNode testJson = (ObjectNode) objectMapper.readTree(testImport .replaceAll("TEAM_NAME", FOO_TEAM));
      testService.importTest(testJson);

   }

   @org.junit.jupiter.api.Test
   void testImportTestWithIncorrectTeam() throws JsonProcessingException {
      String testImport  = """
              {
                "access": "PUBLIC",
                "owner": "perf-team",
                "id": 14,
                "name": "Quarkus - config-quickstart - JVM",
                "folder": "quarkus",
                "description": "",
                "datastoreId": null,
                "tokens": null,
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
         String testImport  = """
              {
                "accccess": "PUBLIC",
                "ownerrr": "TEAM_NAME",
                "name": "Quarkus - config-quickstart - JVM",
                "folder": "quarkus",
                "description": "",
                "datastoreId": null,
                "tokens": null,
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
