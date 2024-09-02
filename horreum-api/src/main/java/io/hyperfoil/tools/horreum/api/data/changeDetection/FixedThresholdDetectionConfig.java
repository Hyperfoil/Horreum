package io.hyperfoil.tools.horreum.api.data.changeDetection;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.hyperfoil.tools.horreum.api.data.datastore.BaseChangeDetectionConfig;

public class FixedThresholdDetectionConfig extends BaseChangeDetectionConfig {
    @Schema(type = SchemaType.STRING, required = true, enumeration = { ChangeDetectionModelType.names.FIXED_THRESHOLD })
    public String model;
    @Schema(type = SchemaType.OBJECT, required = true, description = "Lower bound for acceptable datapoint values")
    public FixThresholdConfig min;
    @Schema(type = SchemaType.OBJECT, required = true, description = "Upper bound for acceptable datapoint values")
    public FixThresholdConfig max;

}
