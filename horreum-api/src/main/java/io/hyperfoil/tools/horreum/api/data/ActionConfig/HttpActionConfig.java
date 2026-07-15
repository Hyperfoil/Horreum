package io.hyperfoil.tools.horreum.api.data.ActionConfig;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class HttpActionConfig extends BaseActionConfig {
    @Schema(type = SchemaType.STRING, required = true, description = "HTTP address")
    public String url;

    @Schema(type = SchemaType.STRING, required = false, description = "Optional formatter to transform payload before sending; when set, wraps formatted text in {\"text\": \"...\"}")
    public String formatter;

    public HttpActionConfig() {
        this.type = ActionType.HTTP.toString();
    }
}
