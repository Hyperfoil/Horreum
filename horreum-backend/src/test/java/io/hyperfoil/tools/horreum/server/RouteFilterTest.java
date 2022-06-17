package io.hyperfoil.tools.horreum.server;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import io.hyperfoil.tools.horreum.svc.BaseServiceTest;
import io.hyperfoil.tools.horreum.test.NoGrafanaProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(NoGrafanaProfile.class)
@DisabledIfEnvironmentVariable(named = "ENVIRONMENT",
   matches = "CI")
public class RouteFilterTest extends BaseServiceTest {

   @org.junit.jupiter.api.Test
   @Disabled
   public void testRouteRedirectsNotFoundPageToHome() {
      bareRequest().get("/duff-beer").then()
         .statusCode(200);
   }
}
