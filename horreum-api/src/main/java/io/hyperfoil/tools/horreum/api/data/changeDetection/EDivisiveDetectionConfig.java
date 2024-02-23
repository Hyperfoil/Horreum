package io.hyperfoil.tools.horreum.api.data.changeDetection;

import io.hyperfoil.tools.horreum.api.data.datastore.BaseChangeDetectionConfig;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class EDivisiveDetectionConfig extends BaseChangeDetectionConfig {
    @Schema(type = SchemaType.STRING, required = true, enumeration = {ChangeDetectionModelType.names.EDIVISIVE})
    public String model;

}
