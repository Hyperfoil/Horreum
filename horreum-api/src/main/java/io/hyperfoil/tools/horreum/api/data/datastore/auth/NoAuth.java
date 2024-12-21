package io.hyperfoil.tools.horreum.api.data.datastore.auth;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class NoAuth {
    public static final String _TYPE = "none";

    @Schema(type = SchemaType.STRING, description = "type")
    public String type;

    public NoAuth() {
        this.type = _TYPE;
    }
}
