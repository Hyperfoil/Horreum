package io.hyperfoil.tools.horreum.api.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.Collection;

public class TableReport {
    @JsonProperty(required = true)
    public Integer id;
    @NotNull
    @JsonProperty(required = true)
    public TableReportConfig config;
    @NotNull
    @Schema(required = true, type = SchemaType.NUMBER)
    public Instant created;
    @NotNull
    @JsonProperty(required = true)
    public Collection<ReportComment> comments;
    @NotNull
    @JsonProperty(required = true)
    public Collection<DataDTO> data;
    @NotNull
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
        @NotNull
        @JsonProperty(required = true)
        public int datasetId;
        @NotNull
        @JsonProperty(required = true)
        public int runId;
        @NotNull
        @JsonProperty(required = true)
        public int ordinal;
        @NotNull
        @JsonProperty(required = true)
        public String category;
        @NotNull
        @JsonProperty(required = true)
        public String series;
        @NotNull
        @JsonProperty(required = true)
        public String scale;
        @NotNull
        @JsonProperty(required = true)
        public ArrayNode values;

        public DataDTO() {
        }

        public String toString() {
            return "TableReport.Data{datasetId=" + this.datasetId + ", category='" + this.category + '\'' + ", series='" + this.series + '\'' + ", label='" + this.scale + '\'' + ", values=" + this.values + '}';
        }
    }
}
