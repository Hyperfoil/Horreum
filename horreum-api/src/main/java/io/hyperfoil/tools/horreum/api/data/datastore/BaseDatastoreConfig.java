package io.hyperfoil.tools.horreum.api.data.datastore;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public abstract class BaseDatastoreConfig {

    @Schema(type = SchemaType.BOOLEAN, required = true, description = "Built In")
    public Boolean builtIn = true;

    //    @Schema(type = SchemaType.STRING, required = true, description = "type information")
    //    public String type = "";

    public BaseDatastoreConfig() {
    }

    public BaseDatastoreConfig(Boolean builtIn/* , String type */) {
        this.builtIn = builtIn;
        //        this.type = type;
    }

    public abstract String validateConfig();
}
