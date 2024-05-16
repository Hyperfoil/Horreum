package io.hyperfoil.tools.horreum.test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;

public class HorreumTestProfile implements QuarkusTestProfile {
   @Override
   public Map<String, String> getConfigOverrides() {
      return Map.of(
            "quarkus.oidc.auth-server-url", "${keycloak.url}/realms/quarkus/",
            "quarkus.oidc.token.issuer", "https://server.example.com",
            "smallrye.jwt.sign.key.location", "/privateKey.jwk",
            "horreum.url", "http://localhost:8081",
            "horreum.roles.provider", "database",
            "horreum.roles.database.override", "false",
            "horreum.test-mode", "true",
            "horreum.privacy", "/path/to/privacy/statement/link");
   }
   @Override
   public boolean disableGlobalTestResources() {
      //we do not want all the managed resources started for tests
      return true;
   }

   @Override
   public List<TestResourceEntry> testResources() {
      return Arrays.asList(
              new TestResourceEntry(PostgresResource.class)
              , new TestResourceEntry(OidcWiremockTestResource.class)
      );
   }
}
