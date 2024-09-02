package io.hyperfoil.tools.horreum.api.data.datastore;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public abstract class BaseDatastoreConfig {

    @Schema(type = SchemaType.BOOLEAN, required = true, description = "Built In")
    public Boolean builtIn = true;

    public BaseDatastoreConfig() {
    }

    public BaseDatastoreConfig(Boolean builtIn) {
        this.builtIn = builtIn;
    }

    public abstract String validateConfig();
}
