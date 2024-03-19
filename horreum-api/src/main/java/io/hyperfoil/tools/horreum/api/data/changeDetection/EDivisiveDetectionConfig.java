package io.hyperfoil.tools.horreum.api.data.changeDetection;

import io.hyperfoil.tools.horreum.api.data.datastore.BaseChangeDetectionConfig;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class EDivisiveDetectionConfig extends BaseChangeDetectionConfig {
    @Schema(type = SchemaType.STRING, required = true, example = "hunterEDivisive",
            description = "model descriminator")
    public static final String model = "hunterEDivisive";

    @Override
    public String validateConfig() {
        return null;
    }
}
