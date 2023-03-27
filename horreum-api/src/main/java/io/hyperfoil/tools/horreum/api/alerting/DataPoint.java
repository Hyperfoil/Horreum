package io.hyperfoil.tools.horreum.api.alerting;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hyperfoil.tools.horreum.api.data.DataSet;

import java.time.Instant;

public class DataPoint {
    public static final String EVENT_NEW = "datapoint/new";
    public static final String EVENT_DELETED = "datapoint/deleted";
    public static final String EVENT_DATASET_PROCESSED = "datapoint/dataset_processed";
    public Integer id;
    public Instant timestamp;
    public double value;
    public Variable variable;

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
        public DataSet.Info dataset;
        public boolean notify;

        public DatasetProcessedEvent() {
        }

        public DatasetProcessedEvent(DataSet.Info dataset, boolean notify) {
            this.dataset = dataset;
            this.notify = notify;
        }
    }

    public static class Event {
        public DataPoint dataPoint;
        public int testId;
        public boolean notify;

        public Event() {
        }

        public Event(DataPoint dataPoint, int testId, boolean notify) {
            this.dataPoint = dataPoint;
            this.testId = testId;
            this.notify = notify;
        }

        public String toString() {
            return "DataPoint.Event{dataPoint=" + this.dataPoint + ", notify=" + this.notify + '}';
        }
    }
}
