package io.hyperfoil.tools;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.hyperfoil.tools.horreum.entity.json.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtensionContext;

public class HorreumTestClientExtension extends HorreumTestExtension {

   static HorreumClient horreumClient;

   static Test dummyTest;

   @BeforeAll
   protected void initialiseRestClients() {
      horreumClient = new HorreumClient.Builder()
            .horreumUrl(HORREUM_BASE_URL + "/")
            .keycloakUrl(HORREUM_KEYCLOAK_BASE_URL)
            .horreumUser(HORREUM_USERNAME)
            .horreumPassword(HORREUM_PASSWORD)
            .resteasyClientBuilder(clientBuilder)
            .build();

      assertNotNull(horreumClient);
   }

   public void createOrLookupTest() {

      boolean createTest = Boolean.parseBoolean(getProperty("horreum.create-test"));
      if (createTest) {
         Test test = new Test();
         test.name = "Dummy5";
         test.owner = "dev-team";
         test.description = "This is a dummy test";
         dummyTest = horreumClient.testService.add(test);
      } else {
         // TODO: id from configuration?
         dummyTest = horreumClient.testService.get(10, null);
      }
      assertNotNull(dummyTest);
   }

   @Override
   public void beforeAll(ExtensionContext context) throws Exception {
      super.beforeAll(context);
      initialiseRestClients();
      createOrLookupTest();
   }
}
