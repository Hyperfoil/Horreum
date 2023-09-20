package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;

public class Label {
    @JsonProperty( required = true )
    public Integer id;
    @NotNull
    @JsonProperty( required = true )
    public String name;
    @NotNull
    @JsonProperty( required = true )
    public Collection<Extractor> extractors;
    public String function;
    @NotNull
    @JsonProperty( required = true )
    public boolean filtering = true;
    @NotNull
    @JsonProperty( required = true )
    public boolean metrics = true;
    @JsonProperty( required = true )
    public String owner;
    @JsonProperty( required = true )
    public Access access = Access.PUBLIC;
    @NotNull
    @JsonProperty( value = "schemaId", required = true )
    public int schemaId;

    public Label() {
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
                Value value1 = (Value)o;
                return this.datasetId == value1.datasetId && this.labelId == value1.labelId && Objects.equals(this.value, value1.value);
            } else {
                return false;
            }
        }

        public int hashCode() {
            return Objects.hash(new Object[]{this.datasetId, this.labelId, this.value});
        }

        public String toString() {
            return "Value{datasetId=" + this.datasetId + ", labelId=" + this.labelId + ", value=" + this.value + '}';
        }
    }
}
