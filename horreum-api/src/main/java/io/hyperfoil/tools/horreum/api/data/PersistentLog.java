package io.hyperfoil.tools.horreum.api.data;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonProperty;

@Schema(type = SchemaType.OBJECT, description = "Persistent Log", name = "PersistentLog")
public abstract class PersistentLog {
    public static final int DEBUG = 0;
    public static final int INFO = 1;
    public static final int WARN = 2;
    public static final int ERROR = 3;

    @JsonProperty(required = true)
    public Long id;

    @NotNull
    @JsonProperty(required = true)
    public int level;

    @NotNull
    @JsonProperty(required = true)
    public Instant timestamp;

    @NotNull
    @JsonProperty(required = true)
    public String message;

    public PersistentLog(int level, String message) {
        this.level = level;
        this.message = message;
        this.timestamp = Instant.now();
    }

}
