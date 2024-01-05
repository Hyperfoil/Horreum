package io.hyperfoil.tools.horreum.api.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(type = SchemaType.OBJECT, description = "Report Component", name = "ReportComponent")
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
    @Schema(type = SchemaType.ARRAY, implementation = String.class, description = "Array of labels", example = "[\"Framework\"]")
    public ArrayNode labels;
    public String function;
    public String unit;
    public Integer reportId;

}
