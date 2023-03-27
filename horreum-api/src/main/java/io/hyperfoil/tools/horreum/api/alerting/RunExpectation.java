package io.hyperfoil.tools.horreum.api.alerting;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class RunExpectation {
    public Long id;
    @JsonProperty(required = true)
    public int testId;
    @JsonProperty(required = true)
    public Instant expectedBefore;
    public String expectedBy;
    public String backlink;

}
