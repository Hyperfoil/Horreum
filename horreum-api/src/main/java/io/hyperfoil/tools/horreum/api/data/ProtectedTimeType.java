package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

@Schema(type = SchemaType.OBJECT)
public class ProtectedTimeType extends ProtectedType{
    @NotNull
    @JsonProperty(required = true)
    @Schema(implementation = Instant.class, type = SchemaType.INTEGER, format = "int64",
            description = "Run Start timestamp", example = "1704965908267")
    public Instant start;
    @NotNull
    @JsonProperty(required = true)
    @Schema(implementation = Instant.class, type = SchemaType.INTEGER, format = "int64",
            description = "Run Stop timestamp", example = "1704965908267")
    public Instant stop;

    public ProtectedTimeType() {}


}


