package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.Collection;

public class Run {
    @JsonProperty(required = true)
    public Integer id;
    @NotNull
    @Schema(type = SchemaType.NUMBER, required = true)
    public Instant start;
    @NotNull
    @Schema(type = SchemaType.NUMBER, required = true)
    public Instant stop;
    public String description;
    @NotNull
    @JsonProperty(required = true)
    public Integer testid;
    @NotNull
    @JsonProperty(required = true)
    public JsonNode data;
    public JsonNode metadata;
    @NotNull
    @JsonProperty(required = true)
    public boolean trashed;
    @JsonIgnore
    public Collection<DataSet> datasets;
    public Collection<ValidationError> validationErrors;
    @NotNull
    @JsonProperty(required = true)
    public String owner;
    @NotNull
    @JsonProperty(required = true)
    public Access access;

    public Run() {
    }
}
