package io.hyperfoil.tools.horreum.api.data.changeDetection;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hyperfoil.tools.horreum.api.data.datastore.BaseChangeDetectionConfig;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class FixedThresholdDetectionConfig extends BaseChangeDetectionConfig {
    @Schema(type = SchemaType.STRING, required = true, enumeration = { "fixedThreshold" })
    public String model;
    @Schema(type = SchemaType.OBJECT, required = true,
            description = "Lower bound for acceptable datapoint values")
    public FixThresholdConfig min;
    @Schema(type = SchemaType.OBJECT, required = true,
            description = "Upper bound for acceptable datapoint values")
    public FixThresholdConfig max;

    @Override
    public String validateConfig() {
        return null;
    }
}
