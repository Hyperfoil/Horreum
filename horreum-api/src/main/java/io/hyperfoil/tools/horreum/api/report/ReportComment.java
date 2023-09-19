package io.hyperfoil.tools.horreum.api.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public class ReportComment {
    public Integer id;
    @NotNull
    @JsonProperty( required = true )
    public int level;
    public String category;
    public int componentId;
    @NotNull
    @JsonProperty( required = true )
    public String comment;

}
