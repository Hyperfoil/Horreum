package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Collection;

public class Test {
    @JsonProperty(required = true)
    public Integer id;
    @NotNull
    @JsonProperty(required = true)
    public String name;
    public String folder;
    public String description;
    @NotNull
    @JsonProperty(required = true)
    public String owner;
    @NotNull
    @JsonProperty(required = true)
    @Schema( type = SchemaType.INTEGER, implementation = Access.class)
    public Access access;
    public Collection<TestToken> tokens;
    @Schema(implementation = String[].class)
    public JsonNode timelineLabels;
    public String timelineFunction;
    @Schema(implementation = String[].class)
    public JsonNode fingerprintLabels;
    public String fingerprintFilter;
    public String compareUrl;
    public Collection<Transformer> transformers;
    @NotNull
    @JsonProperty(required = true)
    public Boolean notificationsEnabled;

    public Test() {
        this.access = Access.PUBLIC;
    }

    @Override
    public String toString() {
        return "Test{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", folder='" + folder + '\'' +
                ", description='" + description + '\'' +
                ", owner='" + owner + '\'' +
                ", access=" + access +
                ", tokens=" + tokens +
                ", timelineLabels=" + timelineLabels +
                ", timelineFunction='" + timelineFunction + '\'' +
                ", fingerprintLabels=" + fingerprintLabels +
                ", fingerprintFilter='" + fingerprintFilter + '\'' +
                ", compareUrl='" + compareUrl + '\'' +
                ", transformers=" + transformers +
                ", notificationsEnabled=" + notificationsEnabled +
                '}';
    }

    public void clearIds() {
        id = null;
        if(tokens != null)
            tokens.stream().forEach( t -> t.clearId());
    }
}
