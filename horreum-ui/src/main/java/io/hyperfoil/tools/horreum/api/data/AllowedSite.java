package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public class AllowedSite {

    public Long id;
    @NotNull
    @JsonProperty(required = true)
    public String prefix;

    public AllowedSite() {
    }

}
