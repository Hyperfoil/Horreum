package io.hyperfoil.tools.horreum.api.data.changeDetection;

import java.util.Arrays;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.type.TypeReference;

import io.hyperfoil.tools.horreum.api.data.datastore.BaseChangeDetectionConfig;

@Schema(type = SchemaType.STRING, required = true, description = "Type of Change Detection Model")
public enum ChangeDetectionModelType {

    FIXED_THRESHOLD(names.FIXED_THRESHOLD, new TypeReference<FixedThresholdDetectionConfig>() {
    }),
    RELATIVE_DIFFERENCE(names.RELATIVE_DIFFERENCE, new TypeReference<RelativeDifferenceDetectionConfig>() {
    }),
    EDIVISIVE(names.EDIVISIVE, new TypeReference<EDivisiveDetectionConfig>() {
    });

    private static final ChangeDetectionModelType[] VALUES = values();

    private final String name;
    private final TypeReference<? extends BaseChangeDetectionConfig> typeReference;

    private <T extends BaseChangeDetectionConfig> ChangeDetectionModelType(String name, TypeReference<T> typeReference) {
        this.typeReference = typeReference;
        this.name = name;
    }

    public <T extends BaseChangeDetectionConfig> TypeReference<T> getTypeReference() {
        return (TypeReference<T>) typeReference;
    }

    @JsonCreator
    public static ChangeDetectionModelType fromString(String str) {
        return Arrays.stream(VALUES).filter(v -> v.name.equals(str)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Unknown model: " + str));
    }

    public static class names {
        public static final String FIXED_THRESHOLD = "fixedThreshold";
        public static final String RELATIVE_DIFFERENCE = "relativeDifference";
        public static final String EDIVISIVE = "eDivisive";
    }
}
