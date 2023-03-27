package io.hyperfoil.tools.horreum.api.alerting;

import java.time.Instant;
import java.io.Serializable;
import java.util.Objects;

public class MissingDataRuleResult {
     private Pk pk;
    public Instant timestamp;
    public MissingDataRule rule;

    public MissingDataRuleResult() {
    }

    public MissingDataRuleResult(int ruleId, int datasetId, Instant timestamp) {
        this.pk = new Pk();
        this.pk.ruleId = ruleId;
        this.pk.datasetId = datasetId;
        this.timestamp = timestamp;
    }

    public int ruleId() {
        return this.pk.ruleId;
    }

    public int datasetId() {
        return this.pk.datasetId;
    }

    public String toString() {
        return "MissingDataRuleResult{dataset_id=" + this.pk.datasetId + ", rule_id=" + this.pk.ruleId + ", timestamp=" + this.timestamp + '}';
    }

    public static class Pk implements Serializable {
        int ruleId;
        int datasetId;

        public Pk() {
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o != null && this.getClass() == o.getClass()) {
                Pk pk = (Pk)o;
                return this.ruleId == pk.ruleId && this.datasetId == pk.datasetId;
            } else {
                return false;
            }
        }

        public int hashCode() {
            return Objects.hash(new Object[]{this.ruleId, this.datasetId});
        }
    }
}
