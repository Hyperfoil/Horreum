package io.hyperfoil.tools.horreum.api.data.datastore;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.hyperfoil.tools.horreum.api.data.datastore.auth.NoAuth;

@Schema(type = SchemaType.OBJECT, required = true, description = "Built in backend datastore")
public class PostgresDatastoreConfig extends BaseDatastoreConfig {

    public static final String[] auths = { NoAuth._TYPE };
    public static final String name = "Postgres";
    public static final String label = "Postgres";
    public static final Boolean builtIn = true;

    public PostgresDatastoreConfig() {
        super(true);
    }

    @Override
    public String validateConfig() {
        return null;
    }
}
