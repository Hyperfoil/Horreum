package io.hyperfoil.tools.horreum.api.alerting;

import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Variable {
    @JsonProperty(required = true)
    public Integer id;
    @NotNull
    @JsonProperty(required = true)
    public int testId;
    @NotNull
    @JsonProperty(required = true)
    public String name;
    public String group;
    @NotNull
    @JsonProperty(required = true)
    public int order;
    @NotNull
    @JsonProperty(required = true)
    public List<String> labels;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String calculation;
    @Schema(required = true, implementation = ChangeDetection[].class)
    public Set<ChangeDetection> changeDetection;

    public Variable() {
    }

    public Variable(Integer id, int testId, String name, String group, int order, List<String> labels, String calculation,
            Set<ChangeDetection> changeDetection) {
        this.id = id;
        this.testId = testId;
        this.name = name;
        this.group = group;
        this.order = order;
        this.labels = labels;
        this.calculation = calculation;
        this.changeDetection = changeDetection;
    }

    public String toString() {
        return "Variable{id=" + this.id + ", testId=" + this.testId + ", name='" + this.name + '\'' + ", group='" + this.group
                + '\'' + ", order=" + this.order + ", labels=" + this.labels + ", calculation='" + this.calculation + '\''
                + ", changeDetection=" + this.changeDetection + '}';
    }

}
