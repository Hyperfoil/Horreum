package io.hyperfoil.tools.horreum.api.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class ReportComponent {
    public Integer id;
    @JsonProperty( required = true )
    public String name;
    @JsonProperty( required = true )
    public int order;
    @JsonProperty( required = true )
    public ArrayNode labels;
    public String function;
    public String unit;
    public Integer reportId;

}
