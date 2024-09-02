package io.hyperfoil.tools.horreum.changedetection;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.hyperfoil.tools.horreum.api.data.changeDetection.ChangeDetectionModelType;
import io.quarkus.arc.All;

@ApplicationScoped
public class ChangeDetectionModelResolver {
    @Inject
    @All
    List<ChangeDetectionModel> changeDetectionModels;

    public ChangeDetectionModel getModel(ChangeDetectionModelType type) {
        return changeDetectionModels.stream()
                .filter(model -> model.type().equals(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown change detection model type: " + type));
    }

    public Map<String, ChangeDetectionModel> getModels() {
        return changeDetectionModels.stream()
                .collect(Collectors.toMap(model -> ((ChangeDetectionModel) model).type().name(), Function.identity()));
    }

}
