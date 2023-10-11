package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class ValidationError {
    public int schemaId;
    @JsonProperty(required = true)
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
        public String type;
        public String code;
        public String path;
        public String schemaPath;
        public String[] arguments;
        public Map<String, Object> details;
        @NotNull
        @JsonProperty(required = true)
        public String message;

    }
}
