package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AllowedSite {

    public Long id;
    @JsonProperty(required = true)
    public String prefix;

    public AllowedSite() {
    }

}
