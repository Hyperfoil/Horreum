package io.hyperfoil.tools.horreum.api.data.datastore.auth;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class APIKeyAuth {
    public static final String _TYPE = "api-key";

    @Schema(type = SchemaType.STRING, description = "type")
    public String type;

    @Schema(type = SchemaType.STRING, description = "Api key")
    public String apiKey;

    public APIKeyAuth() {
        this.type = _TYPE;
    }
}
