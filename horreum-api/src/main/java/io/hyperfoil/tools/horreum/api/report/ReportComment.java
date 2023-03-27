package io.hyperfoil.tools.horreum.api.report;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReportComment {
    public Integer id;
    @JsonProperty( required = true )
    public int level;
    public String category;
    public int componentId;
    @JsonProperty( required = true )
    public String comment;

}
