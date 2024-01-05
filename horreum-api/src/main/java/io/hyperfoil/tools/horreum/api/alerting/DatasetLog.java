package io.hyperfoil.tools.horreum.api.alerting;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hyperfoil.tools.horreum.api.data.PersistentLog;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema( type = SchemaType.OBJECT, description = "Dataset Log", name = "DatasetLog", allOf = PersistentLog.class)
public class DatasetLog extends PersistentLog {
    @NotNull
    @JsonProperty( required = true )
    public String source;

    @JsonProperty( value = "testId", required = true )
    private Integer testId;

    @JsonProperty( value = "runId", required = true )
    private Integer runId;

    @JsonProperty( value = "datasetId", required = true )
    private Integer datasetId;

    @JsonProperty( value = "datasetOrdinal", required = true )
    private Integer datasetOrdinal;

    public DatasetLog() {
        super(0, (String)null);
    }

    public DatasetLog(Integer testId, Integer datasetId, Integer datasetOrdinal, Integer runId,
                      int level, String source, String message) {
        super(level, message);
        this.testId = testId;
        this.datasetId = datasetId;
        this.datasetOrdinal = datasetOrdinal;
        this.runId = runId;
        this.source = source;
    }
}
