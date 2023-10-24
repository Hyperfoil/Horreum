package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class ExperimentComparison {

    @NotNull
    @JsonProperty( required = true )
    @Schema(description = "Name of comparison model", example = "relativeDifference")
    public String model;
    @NotNull
    @JsonProperty( required = true )
    @Schema(implementation = String.class, description = "Model JSON configuration")
    public JsonNode config;

    @NotNull
    @JsonProperty(value = "variableId")
    @Schema(description = "Variable ID to run experiment against", example = "101")
    public Integer variableId;

    @JsonIgnore
    @Schema(description = "Variable Name to run experiment against", example = "Throughput")
    public String variableName;

    public ExperimentComparison() {
    }

}
