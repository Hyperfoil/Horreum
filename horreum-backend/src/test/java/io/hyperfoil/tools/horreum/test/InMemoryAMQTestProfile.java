package io.hyperfoil.tools.horreum.test;

import java.util.Arrays;
import java.util.List;

import io.quarkus.test.oidc.server.OidcWiremockTestResource;

public class InMemoryAMQTestProfile extends HorreumTestProfile {

    @Override
    public List<TestResourceEntry> testResources() {
        return Arrays.asList(
                new TestResourceEntry(PostgresResource.class),
                new TestResourceEntry(OidcWiremockTestResource.class),
                new TestResourceEntry(AMQPInMemoryResource.class));
    }
}
