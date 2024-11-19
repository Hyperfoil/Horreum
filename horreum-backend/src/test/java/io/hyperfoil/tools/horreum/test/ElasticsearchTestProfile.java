package io.hyperfoil.tools.horreum.test;

import java.util.HashMap;
import java.util.Map;

public class ElasticsearchTestProfile extends InMemoryAMQTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> configOverrides = new HashMap<>(super.getConfigOverrides());
        configOverrides.put("quarkus.elasticsearch.devservices.enabled", "true");
        return configOverrides;
    }

}
