package io.hyperfoil.tools.horreum.api.alerting;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hyperfoil.tools.horreum.api.data.PersistentLog;

public class TransformationLog extends PersistentLog {
    @JsonProperty("testId")
    private Integer testId;

    @JsonProperty("runId")
    private Integer runId;

    public TransformationLog() {
        super(0, (String)null);
    }

    public TransformationLog(Integer testId, Integer runId, int level, String message) {
        super(level, message);
        this.testId = testId;
        this.runId = runId;
    }
}
