package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class ExperimentComparison {

    @NotNull
    @JsonProperty( required = true )
    public String model;
    @NotNull
    @JsonProperty( required = true )
    @Schema(implementation = String.class)
    public JsonNode config;

    @NotNull
    @JsonProperty(value = "variableId")
    public Integer variableId;

    @JsonIgnore
    public String variableName;

    public ExperimentComparison() {
    }

}
