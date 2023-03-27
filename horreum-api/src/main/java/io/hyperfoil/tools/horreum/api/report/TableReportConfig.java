package io.hyperfoil.tools.horreum.api.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.hyperfoil.tools.horreum.api.data.Test;

import java.util.List;

public class TableReportConfig {
    @JsonProperty(required = true)
    public Integer id;
    @JsonProperty(required = true)
    public String title;
    public Test test;
    public ArrayNode filterLabels;
    public String filterFunction;
    public ArrayNode categoryLabels;
    public String categoryFunction;
    public String categoryFormatter;
    @JsonProperty(required = true)
    public ArrayNode seriesLabels;
    public String seriesFunction;
    public String seriesFormatter;
    public ArrayNode scaleLabels;
    public String scaleFunction;
    public String scaleFormatter;
    public String scaleDescription;
    @JsonProperty(required = true)
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