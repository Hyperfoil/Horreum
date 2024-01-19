package io.hyperfoil.tools.horreum.api.data.ActionConfig;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class HttpActionConfig extends BaseActionConfig {
    @Schema(type = SchemaType.STRING, required = true, description = "HTTP address")
    public String url;

    public HttpActionConfig() {
        this.type = ActionType.HTTP.toString();
    }
}
