package io.hyperfoil.tools.horreum.api.data;

import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;

import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@Schema(type = SchemaType.OBJECT, allOf = ProtectedType.class, description = "A Label is a core component of Horreum, defining which components of the JSON document are part of a KPI and how the metric values are calculated")
public class Label extends ProtectedType {
    @JsonProperty(required = true)
    @Schema(description = "Unique ID for Label", example = "101")
    public Integer id;
    @NotNull
    @JsonProperty(required = true)
    @Schema(description = "Name for label. NOTE: all Labels are considered to have the same semantic meaning throughout the entire system", example = "Throughput")
    public String name;
    @NotNull
    @JsonProperty(required = true)
    @Schema(description = "A collection of Extractors, that will be combined in the Combination Function")
    public Collection<Extractor> extractors;
    @Schema(description = "A Combination Function that defines how values from Extractors are combined to produce a Label Value", example = "value => { return ((value.reduce((a,b) => a+b))/value.length*1000).toFixed(3); }")
    public String function;
    @NotNull
    @JsonProperty(required = true)
    @Schema(description = "Is Label a filtering label? Filtering labels contains values that are used to filter datasets for comparison", example = "true")
    public boolean filtering = true;
    @NotNull
    @JsonProperty(required = true)
    @Schema(description = "Is Label a metrics label? Metrics labels are contain Metrics that are used for comparison", example = "true")
    public boolean metrics = true;

    @NotNull
    @JsonProperty(value = "schemaId", required = true)
    @Schema(description = "Schema ID that the Label relates to", example = "101")
    public int schemaId;

    public Label() {
    }

    public Label(String name, int schemaId) {
        this.name = name;
        this.schemaId = schemaId;
    }

    public void clearIds() {
        id = null;
    }

    public static class Value implements Serializable {
        public int datasetId;
        public int labelId;
        @Schema(implementation = String.class)
        public JsonNode value;

        public Value() {
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o != null && this.getClass() == o.getClass()) {
                Value value1 = (Value) o;
                return this.datasetId == value1.datasetId && this.labelId == value1.labelId
                        && Objects.equals(this.value, value1.value);
            } else {
                return false;
            }
        }

        public int hashCode() {
            return Objects.hash(new Object[] { this.datasetId, this.labelId, this.value });
        }

        public String toString() {
            return "Value{datasetId=" + this.datasetId + ", labelId=" + this.labelId + ", value=" + this.value + '}';
        }
    }
}
