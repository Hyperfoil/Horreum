package io.hyperfoil.tools.horreum.it.profile;

import io.hyperfoil.tools.horreum.it.ItResource;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

public class InContainerProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.datasource.jdbc.url", "jdbc:postgresql://172.17.0.1:5432/horreum",
                "quarkus.datasource.migration.jdbc.url", "jdbc:postgresql://172.17.0.1:5432/horreum",

                // uncomment the following to test authentication with Horreum instead of Keycloak
                // "horreum.roles.provider", "database",

                // disable certificate validation (but still require a SSL connection)
                "quarkus.datasource.jdbc.additional-jdbc-properties.sslmode", "require"
        );
    }

    @Override
    public boolean disableGlobalTestResources() {
        //we do not want all the managed resources started for tests
        return true;
    }

    //TODO:: idk why the annotated classes resources are not resolved
    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(
                new TestResourceEntry(ItResource.class)
        );
    }
}
