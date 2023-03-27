package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ActionLog extends PersistentLog {
    @JsonProperty( required = true )
    public int testId;
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
