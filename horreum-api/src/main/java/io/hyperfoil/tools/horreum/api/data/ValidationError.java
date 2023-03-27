package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class ValidationError {
    public int schemaId;
    @JsonProperty(required = true)
    public JsonNode error;

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
