package io.hyperfoil.tools.horreum.api.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.validation.constraints.NotNull;

public class ReportComponent {
    public Integer id;
    @NotNull
    @JsonProperty( required = true )
    public String name;
    @NotNull
    @JsonProperty( required = true )
    public int order;
    @NotNull
    @JsonProperty( required = true )
    public ArrayNode labels;
    public String function;
    public String unit;
    public Integer reportId;

}
