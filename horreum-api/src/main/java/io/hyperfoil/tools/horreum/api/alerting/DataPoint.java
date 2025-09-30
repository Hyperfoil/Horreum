package io.hyperfoil.tools.horreum.api.alerting;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.tools.horreum.api.data.Dataset;

public class DataPoint {
    public Integer id;
    @NotNull
    public Instant timestamp;
    @NotNull
    public double value;
    @NotNull
    public Variable variable;

    @NotNull
    @JsonProperty("datasetId")
    public Integer datasetId;

    public DataPoint() {
    }

    public double value() {
        return this.value;
    }

    public String toString() {
        return this.id + "|" + this.datasetId + "@" + this.timestamp + ": " + this.value;
    }

    public static class DatasetProcessedEvent {
        public Dataset.Info dataset;
        public boolean notify;

        public DatasetProcessedEvent() {
        }

        public DatasetProcessedEvent(Dataset.Info dataset, boolean notify) {
            this.dataset = dataset;
            this.notify = notify;
        }

        @Override
        public String toString() {
            return "DatasetProcessedEvent{" +
                    "dataset=" + dataset +
                    ", notify=" + notify +
                    '}';
        }
    }

    public static class Event {
        public int dataPointId;
        public int datasetId;
        public boolean notify;

        public Event() {
        }

        public Event(int dataPointId, int datasetId, boolean notify) {
            this.dataPointId = dataPointId;
            this.datasetId = datasetId;
            this.notify = notify;
        }

        public String toString() {
            return "DataPoint.Event{dataPoint=" + this.dataPointId + ", notify=" + this.notify + '}';
        }
    }
}
