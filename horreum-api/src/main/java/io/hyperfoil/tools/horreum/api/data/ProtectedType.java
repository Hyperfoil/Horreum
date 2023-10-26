package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(type = SchemaType.OBJECT)
public class ProtectedType {

    @NotNull
    @JsonProperty(required = true)
    @Schema( description = "Access rights for the test. This defines the visibility of the Test in the UI",
            example = "PUBLIC", required = true, type = SchemaType.STRING, implementation = Access.class, allOf = Access.class)
    public Access access;

    @NotNull
    @JsonProperty(required = true)
    @Schema(description="Name of the team that owns the test. Users must belong to the team that owns a test to make modifications",
            example="performance-team")
    public String owner;

    public ProtectedType() {
    }

}
