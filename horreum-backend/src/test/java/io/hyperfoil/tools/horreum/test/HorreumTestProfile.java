package io.hyperfoil.tools.horreum.test;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class HorreumTestProfile implements QuarkusTestProfile {
   @Override
   public Map<String, String> getConfigOverrides() {
      return Map.of(
            "quarkus.oidc.auth-server-url", "${keycloak.url}/realms/quarkus/",
            "quarkus.oidc.token.issuer", "https://server.example.com",
            "smallrye.jwt.sign.key.location", "/privateKey.jwk",
            "horreum.url", "http://localhost:8081",
            "horreum.test", "true");
   }
}
