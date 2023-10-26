package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Collection;

@Schema(type = SchemaType.OBJECT, allOf = ProtectedType.class,
description = "A transformer extracts labals and applies a Function to convert a Run into one or more Datasets")
public class Transformer extends ProtectedType {
    @JsonProperty(required = true)
    @Schema(description="Unique Transformer id",
            example="101")
    public Integer id;
    @NotNull
    @JsonProperty(required = true)
    @Schema(description="Transformer name",
            example="normalize-techempower-result")
    public String name;
    @Schema(description="Transformer description",
            example="Normalizers a techempower output file to separate each framework into a dataset and normalize the JSON structure")
    public String description;
    @Schema(description="The schema associated with the calculated Datasets. Where a transformer creates a new JSON object with a new structure, this Schema is used to extafct values from the new Dataset JSON document",
            example="uri:normalized-techempower:0.1")
    public String targetSchemaUri;
    @NotNull
    @JsonProperty(required = true)
    @Schema(description="A collection of extractors to extract JSON values to create new Dataset JSON document")
    public Collection<Extractor> extractors;
    public String function;
    @NotNull
    @JsonProperty(value = "schemaId", required = true)
    @Schema(description="Schema ID that the transform is registered against", example = "101")
    public Integer schemaId;

    @JsonProperty(value = "schemaUri", required = true)
    @Schema(description="Schema Uri that the transform is registered against", example = "urn:techempower:0.1")
    public String schemaUri;

    @JsonProperty(value = "schemaName", required = true)
    @Schema(description="Schema name that the transform is registered against", example = "techempower")
    public String schemaName;

    public Transformer() {
        access = Access.PUBLIC;
    }

    public int compareTo(Transformer o) {
        if (o != null) {
            return o == this ? 0 : Integer.compare(this.id, o.id);
        } else {
            throw new IllegalArgumentException("cannot compare a null reference");
        }
    }

    @Override
    public String toString() {
        return "TransformerDTO{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", targetSchemaUri='" + targetSchemaUri + '\'' +
                ", extractors=" + extractors +
                ", function='" + function + '\'' +
                ", schemaId=" + schemaId +
                ", schemaUri='" + schemaUri + '\'' +
                ", schemaName='" + schemaName + '\'' +
                ", owner='" + owner + '\'' +
                ", access=" + access +
                '}';
    }

    public void clearId() {
        id = null;
    }
}
