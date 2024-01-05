package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema( type = SchemaType.OBJECT, description = "Action Log", name = "ActionLog", allOf = PersistentLog.class)
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
