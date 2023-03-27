package io.hyperfoil.tools.horreum.api.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.Collection;

public class TableReport {
    @JsonProperty(required = true)
    public Integer id;
    @JsonProperty(required = true)
    public TableReportConfig config;
    @Schema(required = true, type = SchemaType.NUMBER)
    public Instant created;
    @JsonProperty(required = true)
    public Collection<ReportComment> comments;
    @JsonProperty(required = true)
    public Collection<DataDTO> data;
    @JsonProperty(required = true)
    public Collection<ReportLog> logs;

    public TableReport() {
    }

    @Override
    public String toString() {
        return "TableReport{" +
                "id=" + id +
                ", config=" + config +
                ", created=" + created +
                ", comments=" + comments +
                ", data=" + data +
                ", logs=" + logs +
                '}';
    }

    @Schema(name = "TableReportData")
    public static class DataDTO {
        @JsonProperty(required = true)
        public int datasetId;
        @JsonProperty(required = true)
        public int runId;
        @JsonProperty(required = true)
        public int ordinal;
        @JsonProperty(required = true)
        public String category;
        @JsonProperty(required = true)
        public String series;
        @JsonProperty(required = true)
        public String scale;
        @JsonProperty(required = true)
        public ArrayNode values;

        public DataDTO() {
        }

        public String toString() {
            return "TableReport.Data{datasetId=" + this.datasetId + ", category='" + this.category + '\'' + ", series='" + this.series + '\'' + ", label='" + this.scale + '\'' + ", values=" + this.values + '}';
        }
    }
}
