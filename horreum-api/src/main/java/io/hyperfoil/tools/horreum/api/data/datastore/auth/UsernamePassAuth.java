package io.hyperfoil.tools.horreum.api.data.datastore.auth;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class UsernamePassAuth {
    public static final String _TYPE = "username";

    @Schema(type = SchemaType.STRING, description = "type")
    public String type;

    @Schema(type = SchemaType.STRING, description = "Username")
    public String username;

    @Schema(type = SchemaType.STRING, description = "Password")
    public String password;

    public UsernamePassAuth() {
        this.type = _TYPE;
    }
}
