package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class Banner {

    public Integer id;

    public Instant created;

    @NotNull
    @JsonProperty( required = true )
    public boolean active;
    @NotNull
    @JsonProperty(required = true)
    public String severity;

    @NotNull
    @JsonProperty(required = true)
    public String title;

    public String message;

    public Banner() {
    }
}
