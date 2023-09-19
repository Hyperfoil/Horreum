package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public class ActionLog extends PersistentLog {
    @NotNull
    @JsonProperty( required = true )
    public int testId;
    @NotNull
    @JsonProperty( required = true )
    public String event;
    public String type;

    public ActionLog() {
        super(0, (String)null);
    }

    public ActionLog(int level, int testId, String event, String type, String message) {
        super(level, message);
        this.testId = testId;
        this.event = event;
        this.type = type;
    }
}
