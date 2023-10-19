package io.hyperfoil.tools.horreum.svc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hyperfoil.tools.horreum.api.data.datastore.BaseDatastoreConfig;
import io.hyperfoil.tools.horreum.api.data.datastore.Datastore;
import io.hyperfoil.tools.horreum.api.data.datastore.ElasticsearchDatastoreConfig;
import io.hyperfoil.tools.horreum.api.data.datastore.PostgresDatastoreConfig;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.junit.Assert;
import org.junit.jupiter.api.TestInfo;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class ConfigServiceTest extends BaseServiceTest {

    @Inject
    ObjectMapper mapper;

    @org.junit.jupiter.api.Test
    public void getBackends(TestInfo testInfo) {
        List<Object> backends = RestAssured.given().auth().oauth2(getTesterToken())
                .get("/api/config/datastore/".concat(TESTER_ROLES[0]))
                .then()
                .statusCode(200)
                .extract().as(List.class);

        Assert.assertNotNull(backends);
        Assert.assertNotEquals(0, backends.size());

    }


    @org.junit.jupiter.api.Test
    public void parseDynamicConfig(TestInfo testInfo) {
        String elasticDatastore = """
                {   
                    "name":"Elastic - Default",
                    "config": { 
                        "host": "https://localhost:9090", 
                        "apiKey": "WThBTFJvc0JjSFlETnVzV2MwSE46MjRtem05c2lUX2V3R3dWcXAzT0tIdw==", 
                        "username": "elastic",
                        "builtIn": false
                    },
                    "type":"ELASTICSEARCH",
                    "owner":"dev-team",
                    "access":2
                }
                """;

        Datastore datastore = null;
        Object config = null;
        try {
            datastore = mapper.readValue(elasticDatastore, Datastore.class);

            config = mapper.readValue(datastore.config.toString(), datastore.type.getTypeReference());

        } catch (JsonProcessingException e) {
            fail(e);
        }

        assertNotNull(datastore);
        assertNotNull(config);
        assertTrue(config instanceof ElasticsearchDatastoreConfig);



        String postgresDatastore = """
                {   
                    "name":"Postgres - Default",
                    "config": { 
                        "builtIn": true
                    },
                    "type":"POSTGRES",
                    "owner":"dev-team",
                    "access":2
                }
                """;

        datastore = null;
        config = null;
        try {
            datastore = mapper.readValue(postgresDatastore, Datastore.class);

            config = mapper.readValue(datastore.config.toString(), datastore.type.getTypeReference());

        } catch (JsonProcessingException e) {
            fail(e);
        }

        assertNotNull(datastore);
        assertNotNull(config);
        assertTrue(config instanceof PostgresDatastoreConfig);

    }



}
