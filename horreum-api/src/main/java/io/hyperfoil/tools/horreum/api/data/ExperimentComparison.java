package io.hyperfoil.tools.horreum.api.data;

import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.data.changeDetection.ChangeDetectionModelType;

public class ExperimentComparison {

    @NotNull
    @JsonProperty(required = true)
    @Schema(description = "Name of comparison model", example = ChangeDetectionModelType.names.RELATIVE_DIFFERENCE)
    public String model;
    @NotNull
    @JsonProperty(required = true)
    @Schema(implementation = String.class, description = "Model JSON configuration")
    public ObjectNode config;

    @NotNull
    @JsonProperty(value = "variableId")
    @Schema(description = "Variable ID to run experiment against", example = "101")
    public Integer variableId;

    @JsonProperty(value = "variableName")
    @Schema(description = "Variable Name to run experiment against", example = "Throughput")
    public String variableName;

    public ExperimentComparison() {
    }

}
