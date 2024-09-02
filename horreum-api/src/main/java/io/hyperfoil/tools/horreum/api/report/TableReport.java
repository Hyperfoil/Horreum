package io.hyperfoil.tools.horreum.api.report;

import java.time.Instant;
import java.util.Collection;

import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ArrayNode;

@Schema(type = SchemaType.OBJECT, description = "Table Report", name = "TableReport")
public class TableReport {
    @JsonProperty(required = true)
    public Integer id;
    @NotNull
    @JsonProperty(required = true)
    @Schema(type = SchemaType.OBJECT, implementation = TableReportConfig.class, description = "Table Report Config", allOf = TableReportConfig.class)
    public TableReportConfig config;
    @NotNull
    @Schema(type = SchemaType.STRING, implementation = Instant.class, description = "Created timestamp", example = "2019-09-26T07:58:30.996+0200")
    public Instant created;
    @NotNull
    @JsonProperty(required = true)
    @Schema(type = SchemaType.ARRAY, implementation = ReportComment.class, description = "List of ReportComments")
    public Collection<ReportComment> comments;
    @NotNull
    @JsonProperty(required = true)
    @Schema(type = SchemaType.ARRAY, implementation = DataDTO.class, description = "List of TableReportData")
    public Collection<DataDTO> data;
    @NotNull
    @JsonProperty(required = true)
    @Schema(type = SchemaType.ARRAY, implementation = ReportLog.class, description = "List of ReportLogs")
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

    @Schema(name = "TableReportData", type = SchemaType.OBJECT, description = "Table Report Data")
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
        @Schema(type = SchemaType.ARRAY, implementation = Number.class, description = "Array of values")
        public ArrayNode values;

        public DataDTO() {
        }

        public String toString() {
            return "TableReport.Data{datasetId=" + this.datasetId + ", category='" + this.category + '\'' + ", series='"
                    + this.series + '\'' + ", label='" + this.scale + '\'' + ", values=" + this.values + '}';
        }
    }
}
