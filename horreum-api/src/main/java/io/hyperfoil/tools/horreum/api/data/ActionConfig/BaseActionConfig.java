package io.hyperfoil.tools.horreum.api.data.ActionConfig;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public abstract class BaseActionConfig {

    @Schema(type = SchemaType.STRING, required = true, description = "Action type")
    public String type;

    public BaseActionConfig() {
    }
}
