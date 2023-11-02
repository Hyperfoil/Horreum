package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Collection;

@JsonIdentityInfo( property = "id", generator = ObjectIdGenerators.PropertyGenerator.class)
@Schema(description = "An Experiment Profile defines the labels and filters for the dataset and baseline")
public class ExperimentProfile {
    @JsonProperty(required = true )
    @Schema(description = "Experiment Profile unique ID", example = "101")
    public Integer id;
    @NotNull
    @Schema(description = "Name of Experiment Profile", example = "Techempower comparison")
    public String name;

    //@NotNull - we can not enforce this check until we have clean workflows in the UI
    // atm it is possible to have a new test in the UI and create an experiment profile
    // before the test is saved, therefore the test might not have an ID
    @Schema(description = "Test ID that Experiment Profile relates to", example = "101")
    public Integer testId;
    @Schema(type = SchemaType.ARRAY, implementation = String.class, required = true, description = "Array of selector labels",
    example = "[\"Framework\"]")
    public JsonNode selectorLabels;
    @Schema(description = "Selector filter to apply to Selector label values",
            example = "value => value === 'quarkus-resteasy-reactive-hibernate-reactive'")
    public String selectorFilter;
    @Schema(type = SchemaType.ARRAY, implementation = String.class, required = true,
    description = "Array of selector labels for comparison Baseline", example = "[\"timestamp\"]")
    public JsonNode baselineLabels;
    @Schema(description = "Selector filter to apply to Baseline label values", example = "value => value === 1666955225547")
    public String baselineFilter;
    @Schema(required = true, description = "Collection of Experiment Comparisons to run during an Experiment evaluation")
    public Collection<ExperimentComparison> comparisons;
    @Schema(type = SchemaType.ARRAY, implementation = String.class, description = "These labels are not used by Horreum but are added to the result event and therefore can be used e.g. when firing an Action.")
    public JsonNode extraLabels;

    public ExperimentProfile() {
    }
}
