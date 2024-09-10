package io.hyperfoil.tools.horreum.api.data;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(type = SchemaType.OBJECT, description = "A map of label names to label values with the associated datasetId and runId")
public class ExportedLabelValues {
    @Schema
    public LabelValueMap values;
    @Schema(type = SchemaType.INTEGER, description = "the run id that created the dataset", example = "101")
    public Integer runId;
    @Schema(type = SchemaType.INTEGER, description = "the unique dataset id", example = "101")
    public Integer datasetId;

    @NotNull
    @Schema(type = SchemaType.STRING, implementation = Instant.class, description = "Start timestamp", example = "2019-09-26T07:58:30.996+0200")
    public Instant start;
    @NotNull
    @Schema(type = SchemaType.STRING, implementation = Instant.class, description = "Stop timestamp", example = "2019-09-26T07:58:30.996+0200")
    public Instant stop;

    public ExportedLabelValues() {
    }

    public ExportedLabelValues(LabelValueMap v, Integer runId, Integer datasetId, Instant start, Instant stop) {
        this.values = v;
        this.runId = runId;
        this.datasetId = datasetId;
        this.start = start;
        this.stop = stop;
    }
}
