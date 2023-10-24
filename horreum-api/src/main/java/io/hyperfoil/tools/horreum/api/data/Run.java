package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.Collection;

@Schema(type = SchemaType.OBJECT,
        description = "Data object that represents a test run entry")
public class Run {
    @JsonProperty(required = true)
    @Schema(description = "Unique Run ID", example = "101")
    public Integer id;
    @NotNull
    @Schema(type = SchemaType.NUMBER, implementation = Instant.class,
    description = "Run Start timestamp", example = "1698013206000")
    public Instant start;
    @NotNull
    @Schema(type = SchemaType.NUMBER, implementation = Instant.class,
    description = "Run Stop timestamp", example = "1698013206000")
    public Instant stop;
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
    @NotNull
    @JsonProperty(required = true)
    @Schema(description="Name of the team that owns the test. Users must belong to the team that owns a test to make modifications",
            example="performance-team")
    public String owner;
    @NotNull
    @JsonProperty(required = true)
    @Schema( type = SchemaType.INTEGER, implementation = Access.class,
            description = "Access rights for the test. This defines the visibility of the Test in the UI",
            example = "0")
    public Access access;

    public Run() {
    }
}
