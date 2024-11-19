package io.hyperfoil.tools.horreum.test;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;

public class AMQPInMemoryResource implements QuarkusTestResourceLifecycleManager {
    @Override
    public Map<String, String> start() {
        Map<String, String> env = new HashMap<>();
        Map<String, String> props1 = InMemoryConnector.switchIncomingChannelsToInMemory("run-upload-in");
        Map<String, String> props2 = InMemoryConnector.switchOutgoingChannelsToInMemory("run-upload-out");
        Map<String, String> props3 = InMemoryConnector.switchIncomingChannelsToInMemory("dataset-event-in");
        Map<String, String> props4 = InMemoryConnector.switchOutgoingChannelsToInMemory("dataset-event-out");
        Map<String, String> props5 = InMemoryConnector.switchIncomingChannelsToInMemory("run-recalc-in");
        Map<String, String> props6 = InMemoryConnector.switchOutgoingChannelsToInMemory("run-recalc-out");
        Map<String, String> props7 = InMemoryConnector.switchIncomingChannelsToInMemory("schema-sync-in");
        Map<String, String> props8 = InMemoryConnector.switchOutgoingChannelsToInMemory("schema-sync-out");
        env.putAll(props1);
        env.putAll(props2);
        env.putAll(props3);
        env.putAll(props4);
        env.putAll(props5);
        env.putAll(props6);
        env.putAll(props7);
        env.putAll(props8);
        return env;
    }

    @Override
    public void stop() {
        InMemoryConnector.clear();
    }
}
