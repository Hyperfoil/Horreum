package io.hyperfoil.tools.horreum.api.data;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AllowedSite {

    public Long id;
    @NotNull
    @JsonProperty(required = true)
    public String prefix;

    public AllowedSite() {
    }

}
