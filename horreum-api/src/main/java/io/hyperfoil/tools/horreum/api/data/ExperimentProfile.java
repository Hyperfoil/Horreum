package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Collection;

public class ExperimentProfile {
    @JsonProperty(required = true )
    public Integer id;
    @NotNull
    public String name;

    //@NotNull - we can not enforce this check until we have clean workflows in the UI
    // atm it is possible to have a new test in the UI and create an experiment profile
    // before the test is saved, therefore the test might not have an ID
    public Integer testId;
    @Schema(implementation = String[].class, required = true )
    public JsonNode selectorLabels;
    public String selectorFilter;
    @Schema(implementation = String[].class, required = true )
    public JsonNode baselineLabels;
    public String baselineFilter;
    @Schema(required = true )
    public Collection<ExperimentComparison> comparisons;
    @Schema(implementation = String[].class )
    public JsonNode extraLabels;

    public ExperimentProfile() {
    }
}
