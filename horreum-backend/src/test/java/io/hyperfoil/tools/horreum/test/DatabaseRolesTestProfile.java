package io.hyperfoil.tools.horreum.test;

import java.util.HashMap;
import java.util.Map;

public class DatabaseRolesTestProfile extends HorreumTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> configOverrides = new HashMap<>(super.getConfigOverrides());
        configOverrides.put("horreum.roles.provider", "database");
        configOverrides.put("horreum.roles.database.override", "false");
        return configOverrides;
    }
}
