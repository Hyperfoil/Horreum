package io.hyperfoil.tools.horreum.api.data.datastore;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(type = SchemaType.OBJECT, required = true, description = "Built in backend datastore")
public class PostgresDatastoreConfig extends BaseDatastoreConfig {

    public PostgresDatastoreConfig() {
        super(false);
    }

    @Override
    public String validateConfig() {
        return null;
    }
}
