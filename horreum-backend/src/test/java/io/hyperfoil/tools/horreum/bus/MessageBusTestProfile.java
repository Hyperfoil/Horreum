package io.hyperfoil.tools.horreum.bus;

import io.hyperfoil.tools.horreum.test.HorreumTestProfile;

import java.util.HashMap;
import java.util.Map;

public class MessageBusTestProfile extends HorreumTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> map = new HashMap<>(super.getConfigOverrides());
        map.put("horreum.messagebus.retry.after", "1s");
        return map;
    }
}