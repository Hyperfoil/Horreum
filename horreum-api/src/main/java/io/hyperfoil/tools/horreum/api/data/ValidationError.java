package io.hyperfoil.tools.horreum.api.data;

import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonProperty;

@Schema(type = SchemaType.OBJECT, description = "Schema validation error")
public class ValidationError {
    @Schema(description = "Schema ID that Validation Error relates to", example = "101")
    @JsonProperty(value = "schemaId", required = true)
    public Integer schemaId;
    @JsonProperty(required = true)
    @Schema(description = "Validation Error Details", implementation = ErrorDetails.class)
    public ErrorDetails error;

    public ValidationError() {
    }

    @Schema(type = SchemaType.OBJECT, description = "Schema validation error details")
    public static class ErrorDetails {
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
