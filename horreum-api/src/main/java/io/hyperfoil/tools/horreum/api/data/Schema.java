package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collection;

public class Schema {

    public static final int TYPE_1ST_LEVEL = 0;
    public static final int TYPE_2ND_LEVEL = 1;
    public static final int TYPE_ARRAY_ELEMENT = 2;
    @JsonProperty(required = true)
    public Integer id;
    @JsonProperty(required = true)
    public String uri;
    @JsonProperty(required = true)
    public String name;
    public String description;
    public JsonNode schema;
    @JsonProperty(required = true)
    public String owner;
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
