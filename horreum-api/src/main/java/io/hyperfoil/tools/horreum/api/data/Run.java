package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Collection;

@Schema(type = SchemaType.OBJECT)
public class Run extends ProtectedTimeType {
    @JsonProperty(required = true)
    @Schema(description = "Unique Run ID", example = "101")
    public Integer id;
    @Schema(description = "Run description", example = "Run on AWS with m7g.large")
    public String description;
    @NotNull
    @JsonProperty(required = true)
    @Schema(description = "Test ID run relates to", example = "101")
    public Integer testid;
    @NotNull
    @Schema(implementation = JsonNode.class, type = SchemaType.STRING,
    description = "Run result payload")
    @JsonProperty(required = true)
    public JsonNode data;
    @Schema(implementation = JsonNode.class, type = SchemaType.STRING,
    description = "JSON metadata related to run, can be tool configuration etc")
    public JsonNode metadata;
    @NotNull
    @JsonProperty(required = true)
    @Schema(description = "Has Run been deleted from UI", example = "false")
    public boolean trashed;
    @JsonIgnore
    @Schema(description = "Collection of Datasets derived from Run payload")
    public Collection<Dataset> datasets;
    @Schema(description = "Collection of Validation Errors in Run payload")
    public Collection<ValidationError> validationErrors;

    public Run() {
    }
}
