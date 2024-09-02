package io.hyperfoil.tools.horreum.api.report;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReportComment {
    public Integer id;
    @NotNull
    @JsonProperty(required = true)
    public int level;
    public String category;
    public int componentId;
    @NotNull
    @JsonProperty(required = true)
    public String comment;

}
