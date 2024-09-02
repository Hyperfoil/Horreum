package io.hyperfoil.tools.horreum.api.alerting;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RunExpectation {
    public Long id;
    @NotNull
    @JsonProperty(required = true)
    public int testId;
    @NotNull
    @JsonProperty(required = true)
    public Instant expectedBefore;
    public String expectedBy;
    public String backlink;

}
