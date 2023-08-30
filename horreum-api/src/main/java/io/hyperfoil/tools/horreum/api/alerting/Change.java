package io.hyperfoil.tools.horreum.api.alerting;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hyperfoil.tools.horreum.api.data.DataSet;

import java.time.Instant;

public class Change {
    public static final String EVENT_NEW = "change/new";
    @JsonProperty( required = true )
    public int id;
    @JsonProperty( required = true )
    public Variable variable;
    @JsonIgnore
    public DataSet dataset;
    @JsonProperty( required = true )
    public Instant timestamp;
    @JsonProperty( required = true )
    public boolean confirmed;
    public String description;

    public Change() {
    }

    @JsonProperty("dataset")
    public DataSet.Info getDatasetId() {
        return this.dataset != null ? this.dataset.getInfo() : null;
    }

    public String toString() {
        return "Change{id=" + this.id + ", variable=" + this.variable.id + ", dataset=" + this.dataset.id + " (" + this.dataset.runId + "/" + this.dataset.ordinal + "), timestamp=" + this.timestamp + ", confirmed=" + this.confirmed + ", description='" + this.description + '\'' + '}';
    }

    public static class Event {
        public Change change;
        public String testName;
        public DataSet.Info dataset;
        public boolean notify;

        public Event() {
        }

        public Event(Change change, String testName, DataSet.Info dataset, boolean notify) {
            this.change = change;
            this.testName = testName;
            this.dataset = dataset;
            this.notify = notify;
        }

        public String toString() {
            return "Change.Event{change=" + this.change + ", dataset=" + this.dataset + ", notify=" + this.notify + '}';
        }
    }
}
