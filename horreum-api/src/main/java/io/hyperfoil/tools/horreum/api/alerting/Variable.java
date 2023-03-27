package io.hyperfoil.tools.horreum.api.alerting;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Set;

public class Variable {
    @JsonProperty( required = true )
    public Integer id;
    @JsonProperty(required = true)
    public int testId;
    @JsonProperty(required = true)
    public String name;
    public String group;
    @JsonProperty(required = true)
    public int order;
    @JsonProperty(required = true)
    public JsonNode labels;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String calculation;
    @Schema(
        required = true,
        implementation = ChangeDetection[].class
    )
    public Set<ChangeDetection> changeDetection;

    public Variable() {
    }

    public Variable(Integer id, int testId, String name, String group, int order, JsonNode labels,
                    Set<ChangeDetection> changeDetection) {
        this.id = id;
        this.testId = testId;
        this.name = name;
        this.group = group;
        this.order = order;
        this.labels = labels;
        this.changeDetection = changeDetection;
    }

    public String toString() {
        return "VariableDTO{id=" + this.id + ", testId=" + this.testId + ", name='" + this.name + '\'' + ", group='" + this.group + '\'' + ", order=" + this.order + ", labels=" + this.labels + ", calculation='" + this.calculation + '\'' + ", changeDetection=" + this.changeDetection + '}';
    }

}
