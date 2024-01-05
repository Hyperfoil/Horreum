package io.hyperfoil.tools.horreum.api.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.hyperfoil.tools.horreum.api.data.Test;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(type = SchemaType.OBJECT, description = "Table Report Config", name = "TableReportConfig")
public class TableReportConfig {
    @JsonProperty(required = true)
    public Integer id;
    @NotNull
    @JsonProperty(required = true)
    public String title;
    public Test test;
    @Schema(type = SchemaType.ARRAY, implementation = String.class, description = "ArrayNode of filter labels")
    public ArrayNode filterLabels;
    public String filterFunction;
    @Schema(type = SchemaType.ARRAY, implementation = String.class, description = "ArrayNode of category labels")
    public ArrayNode categoryLabels;
    public String categoryFunction;
    public String categoryFormatter;
    @NotNull
    @JsonProperty(required = true)
    @Schema(type = SchemaType.ARRAY, implementation = String.class, description = "ArrayNode of series labels")
    public ArrayNode seriesLabels;
    public String seriesFunction;
    public String seriesFormatter;
    @Schema(type = SchemaType.ARRAY, implementation = String.class, description = "ArrayNode of filter labels")
    public ArrayNode scaleLabels;
    public String scaleFunction;
    public String scaleFormatter;
    public String scaleDescription;
    @NotNull
    @JsonProperty(required = true)
    @Schema(type = SchemaType.ARRAY, implementation = ReportComponent.class, description = "List of ReportComponents")
    public List<ReportComponent> components;

    public TableReportConfig() {
    }
   public void ensureLinked() {
      if (components != null) {
         for (ReportComponent c : components) {
            c.reportId = id;
         }
      }
   }

    @Override
    public String toString() {
        return "TableReportConfig{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", test=" + test +
                ", filterLabels=" + filterLabels +
                ", filterFunction='" + filterFunction + '\'' +
                ", categoryLabels=" + categoryLabels +
                ", categoryFunction='" + categoryFunction + '\'' +
                ", categoryFormatter='" + categoryFormatter + '\'' +
                ", seriesLabels=" + seriesLabels +
                ", seriesFunction='" + seriesFunction + '\'' +
                ", seriesFormatter='" + seriesFormatter + '\'' +
                ", scaleLabels=" + scaleLabels +
                ", scaleFunction='" + scaleFunction + '\'' +
                ", scaleFormatter='" + scaleFormatter + '\'' +
                ", scaleDescription='" + scaleDescription + '\'' +
                ", components=" + components +
                '}';
    }
}