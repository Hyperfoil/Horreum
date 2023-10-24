package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;

@Schema(name = "Dataset", type = SchemaType.OBJECT,
description = "A dataset is the JSON document used as the basis for all comparisons and reporting")
public class Dataset {
    @Schema(description = "Dataset Unique ID", example = "101")
    public Integer id;
    @NotNull
    @Schema(type = SchemaType.STRING, implementation = Instant.class,
            description = "Dataset Start timestamp", example = "1698013206000")
    public Instant start;
    @NotNull
    @Schema(type = SchemaType.STRING, implementation = Instant.class,
            description = "Dataset Stop timestamp", example = "1698013206000")
    public Instant stop;
    @Schema(description = "Run description", example = "Run on AWS with m7g.large")
    public String description;

    @NotNull
    @Schema(description = "Test ID that Dataset relates to", example = "101")
    public Integer testid;
    @NotNull
    @JsonProperty( required = true )
    @Schema(description="Name of the team that owns the test. Users must belong to the team that owns a test to make modifications",
            example="performance-team")
    public String owner;
    @NotNull
    @JsonProperty( required = true )
    @Schema( type = SchemaType.INTEGER, implementation = Access.class,
            description = "Access rights for the test. This defines the visibility of the Test in the UI",
            example = "0")
    public Access access;
    @NotNull
    @Schema(implementation = JsonNode.class, type = SchemaType.STRING,
            description = "Data payload")
    public JsonNode data;
    @NotNull
    @Schema(description = "Dataset ordinal for ordered list of Datasets derived from a Run", example = "1")
    public int ordinal;

    @Schema(description = "Collection of Validation Errors")
    public Collection<ValidationError> validationErrors;

    @JsonProperty("runId")
    @Schema(description = "Run ID that Dataset relates to", example = "101")
    public Integer runId;
    @JsonIgnore
    public Info getInfo() {
        return new Info(this.id, this.runId, this.ordinal, this.testid);
    }

    public Dataset() {
        access = Access.PUBLIC;
    }

    public Dataset(Run run, int ordinal, String description, JsonNode data) {
        this.runId = run.id;
        this.start = run.start;
        this.stop = run.stop;
        this.testid = run.testid;
        this.owner = run.owner;
        this.access = run.access;
        this.ordinal = ordinal;
        this.description = description;
        this.data = data;
    }

    @Schema( name = "DatasetInfo" )
    public static class Info {
        @JsonProperty( required = true )
        @Schema(description = "Dataset ID for Dataset", example = "101")
        public int id;
        @JsonProperty( required = true )
        @Schema(description = "Run ID that Dataset relates to", example = "101")
        public int runId;
        @JsonProperty( required = true )
        @Schema(description = "Ordinal position in ordered list", example = "2")
        public int ordinal;
        @JsonProperty( required = true )
        @Schema(description = "Test ID that Dataset relates to", example = "103")
        public int testId;

        public Info() {
        }

        public Info(int id, int runId, int ordinal, int testId) {
            this.id = id;
            this.runId = runId;
            this.ordinal = ordinal;
            this.testId = testId;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o != null && this.getClass() == o.getClass()) {
                Info info = (Info)o;
                return this.id == info.id && this.runId == info.runId && this.ordinal == info.ordinal && this.testId == info.testId;
            } else {
                return false;
            }
        }

        public int hashCode() {
            return Objects.hash(new Object[]{this.id, this.runId, this.ordinal, this.testId});
        }

        public String toString() {
            return "DatasetInfo{id=" + this.id + ", runId=" + this.runId + ", ordinal=" + this.ordinal + ", testId=" + this.testId + '}';
        }
    }

    public static class LabelsUpdatedEvent {
        public int testId;
        public int datasetId;
        public boolean isRecalculation;

        public LabelsUpdatedEvent() {
        }

        public LabelsUpdatedEvent(int testId, int datasetId, boolean isRecalculation) {
            this.testId = testId;
            this.datasetId = datasetId;
            this.isRecalculation = isRecalculation;
        }
    }

    public static class EventNew {

        public int datasetId;
        public int testId;
        public int runId;
        public int labelId = -1;
        public boolean isRecalculation;

        public EventNew() {
        }

        public EventNew(Dataset dataSet, boolean isRecalculation) {
            this.datasetId = dataSet.id;
            this.testId = dataSet.testid;
            this.runId = dataSet.runId;
            this.isRecalculation = isRecalculation;
        }
        public EventNew(int datasetId, int testId, int runId, int labelId, boolean isRecalculation) {
            this.datasetId = datasetId;
            this.testId = testId;
            this.runId = runId;
            this.labelId = labelId;
            this.isRecalculation = isRecalculation;
        }

        @Override
        public String toString() {
            return "EventNew{" +
                    "datasetId=" + datasetId +
                    ", testId=" + testId +
                    ", runId=" + runId +
                    ", labelId=" + labelId +
                    ", isRecalculation=" + isRecalculation +
                    '}';
        }
    }
}
