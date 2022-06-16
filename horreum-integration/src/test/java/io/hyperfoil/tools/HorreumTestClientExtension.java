package io.hyperfoil.tools;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.hyperfoil.tools.horreum.entity.json.Test;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class HorreumTestClientExtension extends HorreumTestExtension implements BeforeEachCallback, AfterEachCallback {

   public static HorreumClient horreumClient;

   public static Test dummyTest;

   protected void initialiseRestClients() {
      horreumClient = new HorreumClient.Builder()
            .horreumUrl(HORREUM_BASE_URL + "/")
            .keycloakUrl(HORREUM_KEYCLOAK_BASE_URL)
            .horreumUser(HORREUM_USERNAME)
            .horreumPassword(HORREUM_PASSWORD)
            .build();

      assertNotNull(horreumClient);
   }

   @Override
   protected void beforeSuite(ExtensionContext context) throws Exception {
      super.beforeSuite(context);
      initialiseRestClients();
   }

   @Override
   public void close() {
      horreumClient.close();
      super.close();
   }

   @Override
   public void beforeEach(ExtensionContext context) throws Exception {
      assertNull(dummyTest);
      Test test = new Test();
      test.name = context.getUniqueId();
      test.owner = "dev-team";
      test.description = "This is a dummy test";
      dummyTest = horreumClient.testService.add(test);
      assertNotNull(dummyTest);
   }

   @Override
   public void afterEach(ExtensionContext context) throws Exception {
      horreumClient.testService.delete(dummyTest.id);
      dummyTest = null;
   }
}
