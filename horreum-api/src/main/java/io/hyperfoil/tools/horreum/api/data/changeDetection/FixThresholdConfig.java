package io.hyperfoil.tools.horreum.api.data.changeDetection;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/*
 * Concrete configuration type for io.hyperfoil.tools.horreum.changedetection.FixedThresholdModel
 */
public class FixThresholdConfig {
    @Schema(type = SchemaType.INTEGER, required = true, example = "95",
            description = "Threshold Value")
    public Double value;
    @Schema(type = SchemaType.BOOLEAN, required = true, example = "true",
            description = "Threshold enabled/disabled")
    public Boolean enabled;
    @Schema(type = SchemaType.BOOLEAN, required = true, example = "false",
            description = "Is threshold inclusive of defined value?")
    public Boolean inclusive;

}
