package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Map;

public class ValidationError {
    @Schema(description = "Schema ID that Validation Error relates to", example = "101")
    public Integer schemaId;
    @JsonProperty(required = true)
    @Schema(description = "Validation Error Details")
    public ErrorDetails error;

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

    public static class ErrorDetails{
        @NotNull
        @JsonProperty(required = true)
        @Schema(description = "Validation Error type")
        public String type;
        public String code;
        public String path;

        public String evaluationPath;
        @Deprecated
        public String schemaPath;
        public String schemaLocation;
        public String instanceLocation;
        public String property;
        public String[] arguments;
        public String details;
        public String messageKey;
        public Boolean valid;
        @NotNull
        @JsonProperty(required = true)
        public String message;

    }
}
