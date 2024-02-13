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
    @Schema(type = SchemaType.STRING, implementation = Instant.class,
            description = "Run Start timestamp", example = "2019-09-26T07:58:30.996+0200")
    public Instant start;
    @NotNull
    @JsonProperty(required = true)
    @Schema(type = SchemaType.STRING, implementation = Instant.class,
            description = "Run Stop timestamp", example = "2019-09-26T07:58:30.996+0200")
    public Instant stop;

    public ProtectedTimeType() {}


}


