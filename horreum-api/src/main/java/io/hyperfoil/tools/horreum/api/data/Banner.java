package io.hyperfoil.tools.horreum.api.data;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Banner {

    public Integer id;

    public Instant created;

    @NotNull
    @JsonProperty(required = true)
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
