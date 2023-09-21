package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class ValidationError {
    public int schemaId;
    @NotNull
    @Schema(implementation = String.class, required = true)
    public String type;
    @NotNull
    @Schema(implementation = String.class, required = true)
    public String message;

    public ValidationError() {
    }

    @JsonProperty( value = "schemaId", required = true )
    public void setSchema(int id) {
        this.schemaId = id;
    }

    @JsonProperty( value = "schemaId", required = true )
    public Integer getSchemaId() {
        return schemaId;
    }
}
