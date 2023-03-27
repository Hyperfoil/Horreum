package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class ExperimentComparison {

    @JsonProperty( required = true )
    public String model;
    @JsonProperty( required = true )
    public JsonNode config;

    @JsonProperty(value = "variableId")
    public Integer variableId;

    @JsonIgnore
    public String variableName;

    public ExperimentComparison() {
    }

}
