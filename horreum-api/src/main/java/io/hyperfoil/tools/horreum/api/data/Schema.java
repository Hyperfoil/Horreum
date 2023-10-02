package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;

import java.util.Collection;

@org.eclipse.microprofile.openapi.annotations.media.Schema(name = "Schema",
        description = "Data object that describes the schema definition for a test" )
public class Schema {

    @org.eclipse.microprofile.openapi.annotations.media.Schema(required = true)
    @JsonProperty(required = true)
    public Integer id;
    @NotNull
    @org.eclipse.microprofile.openapi.annotations.media.Schema(required = true)
    @JsonProperty(required = true)
    public String uri;
    @NotNull
    @org.eclipse.microprofile.openapi.annotations.media.Schema(required = true)
    @JsonProperty(required = true)
    public String name;
    public String description;
    @org.eclipse.microprofile.openapi.annotations.media.Schema(implementation = String.class)
    public JsonNode schema;
    @org.eclipse.microprofile.openapi.annotations.media.Schema(required = true)
    @JsonProperty(required = true)
    public String owner;
    @org.eclipse.microprofile.openapi.annotations.media.Schema( type = SchemaType.INTEGER, implementation = Access.class)
    public Access access;
    public String token;

    public Schema() {
        access = Access.PUBLIC;
    }

    public static class ValidationEvent {
        public int id;
        public Collection<ValidationError> errors;

        public ValidationEvent(int id, Collection<ValidationError> errors) {
            this.id = id;
            this.errors = errors;
        }
    }
}
