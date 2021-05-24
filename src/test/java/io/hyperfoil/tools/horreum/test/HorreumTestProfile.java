package io.hyperfoil.tools.horreum.test;

import java.util.Collections;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class HorreumTestProfile implements QuarkusTestProfile {
   @Override
   public Map<String, String> getConfigOverrides() {
      return Collections.singletonMap("smallrye.jwt.sign.key-location", "privateKey.jwk");
   }
}
