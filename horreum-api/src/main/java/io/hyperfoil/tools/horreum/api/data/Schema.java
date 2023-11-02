package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;

import java.util.Collection;

@org.eclipse.microprofile.openapi.annotations.media.Schema(name = "Schema", type = SchemaType.OBJECT, allOf = ProtectedType.class,
        description = "Data object that describes the schema definition for a test" )
public class Schema extends ProtectedType {

    @org.eclipse.microprofile.openapi.annotations.media.Schema(required = true,
    description = "Unique Schema ID", example = "101")
    @JsonProperty(required = true)
    public Integer id;
    @NotNull
    @org.eclipse.microprofile.openapi.annotations.media.Schema(required = true,
    description = "Unique, versioned schema URI", example = "uri:my-schema:0.1")
    @JsonProperty(required = true)
    public String uri;
    @NotNull
    @org.eclipse.microprofile.openapi.annotations.media.Schema(required = true,
    description = "Schema name", example = "My benchmark schema")
    @JsonProperty(required = true)
    public String name;
    @org.eclipse.microprofile.openapi.annotations.media.Schema(
            description = "Schema Description", example = "Schema for processing my benchmark")
    public String description;
    @org.eclipse.microprofile.openapi.annotations.media.Schema(implementation = String.class, description = "JSON validation schema. Used to validate uploaded JSON documents",
    example = "{" +
            "  \"$schema\": \"https://json-schema.org/draft/2020-12/schema\"," +
            "  \"$id\": \"https://example.com/product.schema.json\"," +
            "  \"title\": \"Product\"," +
            "  \"description\": \"A product in the catalog\"," +
            "  \"type\": \"object\"" +
            "}")
    public JsonNode schema;

    @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "Array of API tokens associated with test",
            example = "")
    public String token;

    public Schema() {
        access = Access.PUBLIC;
    }

    public Schema(Schema s) {
        this.id = s.id;
        this.uri = s.uri;
        this.name = s.name;
        this.description = s.description;
        this.schema = s.schema;
        this.token = s.token;
        this.access = s.access;
        this.owner = s.owner;
    }

    public static class ValidationEvent {
        public int id;
        public Collection<ValidationError> errors;

        public ValidationEvent(int id, Collection<ValidationError> errors) {
            this.id = id;
            this.errors = errors;
        }

        @Override
        public String toString() {
            return "ValidationEvent{" +
                    "id=" + id +
                    ", errors=" + errors +
                    '}';
        }
    }
}
