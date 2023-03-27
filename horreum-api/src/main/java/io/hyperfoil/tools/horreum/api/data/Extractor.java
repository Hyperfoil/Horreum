package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Extractor {
    @JsonProperty( required = true )
    public String name;
    @JsonProperty( required = true )
    public String jsonpath;
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
