package io.hyperfoil.tools.horreum.api.data.changeDetection;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.hyperfoil.tools.horreum.api.data.datastore.BaseChangeDetectionConfig;

public class EDivisiveDetectionConfig extends BaseChangeDetectionConfig {
    @Schema(type = SchemaType.STRING, required = true, enumeration = { ChangeDetectionModelType.names.EDIVISIVE })
    public String model;

}
