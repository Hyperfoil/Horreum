package io.hyperfoil.tools.horreum.svc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.TestInfo;

import io.hyperfoil.tools.horreum.api.data.Banner;
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
public class BannerServiceTest extends BaseServiceTest {

    @org.junit.jupiter.api.Test
    public void testNewBannerAndGet(TestInfo info) throws InterruptedException {
        Banner b1 = new Banner();
        b1.created = Instant.now();
        b1.message = "Stay away!";
        b1.severity = "EXTREME";
        b1.title = info.getDisplayName();

        jsonRequest().body(b1)
                .auth()
                .oauth2(getAdminToken())
                .post("/api/banner")
                .then()
                .statusCode(204);

        Banner b2 = jsonRequest()
                .auth()
                .oauth2(getAdminToken())
                .get("/api/banner")
                .then()
                .extract()
                .as(Banner.class);

        assertTrue(b2.active);
        assertEquals(b1.message, b2.message);

    }

}
