package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;

public class DataSet {
    public Integer id;
    public Instant start;
    public Instant stop;
    public String description;

    public Integer testid;
    @JsonProperty( required = true )
    public String owner;
    @JsonProperty( required = true )
    public Access access;
    public JsonNode data;
    public int ordinal;

    public Collection<ValidationError> validationErrors;

    @JsonProperty("runId")
    public Integer runId;
    @JsonIgnore
    public Info getInfo() {
        return new Info(this.id, this.runId, this.ordinal, this.testid);
    }

    public DataSet() {
        access = Access.PUBLIC;
    }

    public DataSet(Run run, int ordinal, String description, JsonNode data) {
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
        public int id;
        @JsonProperty( required = true )
        public int runId;
        @JsonProperty( required = true )
        public int ordinal;
        @JsonProperty( required = true )
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
        public DataSet dataset;
        public boolean isRecalculation;

        public EventNew() {
        }

        public EventNew(DataSet dataset, boolean isRecalculation) {
            this.dataset = dataset;
            this.isRecalculation = isRecalculation;
        }

        public String toString() {
            return "DataSetDTO.EventNew{dataset=" + this.dataset.id + " (" + this.dataset.runId + "/" + this.dataset.ordinal + "), isRecalculation=" + this.isRecalculation + '}';
        }
    }
}
