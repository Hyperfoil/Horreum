package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public class Extractor {
    @NotNull
    @JsonProperty( required = true )
    public String name;
    @NotNull
    @JsonProperty( required = true )
    public String jsonpath;
    @NotNull
    @JsonProperty(required = true)
    public boolean array;

    public Extractor() {
    }

    public Extractor(String name, String jsonpath, boolean array) {
        this.name = name;
        this.jsonpath = jsonpath;
        this.array = array;
    }
}
