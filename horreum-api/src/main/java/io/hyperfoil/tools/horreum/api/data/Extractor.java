package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "An Extractor defines how values are extracted from a JSON document, for use in Labels etc.")
public class Extractor {
    @NotNull
    @JsonProperty( required = true )
    @Schema(description = "Name of extractor. This name is used in Combination Functions to refer to values by name", example = "buildID")
    public String name;
    @NotNull
    @JsonProperty( required = true )
    @Schema(description = "JSON path expression defining the location of the extractor value in the JSON document. This is a pSQL json path expression",
            example = "$.buildInfo.buildID")
    public String jsonpath;
    @NotNull
    @JsonProperty(required = true)
    @Schema(description = "Does the JSON path expression reference an Array?",
            example = "false")
    public boolean array;

    public Extractor() {
    }

    public Extractor(String name, String jsonpath, boolean array) {
        this.name = name;
        this.jsonpath = jsonpath;
        this.array = array;
    }
}
