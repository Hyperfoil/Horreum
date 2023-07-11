package io.hyperfoil.tools.horreum.dev.services.deployment;

import io.quarkus.builder.item.SimpleBuildItem;

import java.util.Map;

public class HorreumDevServicesConfigBuildItem extends SimpleBuildItem {

    private final Map<String, String> config;
    private final Map<String, Object> properties;
    private final boolean containerRestarted;

    public HorreumDevServicesConfigBuildItem(Map<String, String> config, Map<String, Object> configProperties,
                                             boolean containerRestarted) {
        this.config = config;
        this.properties = configProperties;
        this.containerRestarted = containerRestarted;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public Map<String, String> getConfig() {
        return config;
    }

    boolean isContainerRestarted() {
        return containerRestarted;
    }
}
