package io.hyperfoil.tools.horreum.infra.common;


import org.testcontainers.containers.Network;

import java.util.Map;
import java.util.Optional;

public interface ResourceLifecycleManager {
    Map<String, String> start(Optional<Network> network);

    void stop();


    default void init(Map<String, String> initArgs) {
    }

    default void inject(Object testInstance) {
    }


    default int order() {
        return 0;
    }
}
