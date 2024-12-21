package io.hyperfoil.tools.horreum.api.data.datastore;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.hyperfoil.tools.horreum.api.data.datastore.auth.APIKeyAuth;
import io.hyperfoil.tools.horreum.api.data.datastore.auth.NoAuth;

@Schema(type = SchemaType.OBJECT, required = true, description = "Type of backend datastore")
public class CollectorApiDatastoreConfig extends BaseDatastoreConfig {

    public static final String[] auths = { NoAuth._TYPE, APIKeyAuth._TYPE };
    public static final String name = "Collectorapi";
    public static final String label = "Collector API";
    public static final Boolean builtIn = false;

    public CollectorApiDatastoreConfig() {
    }

    @Schema(type = SchemaType.STRING, required = true, description = "Collector url, e.g. https://collector.foci.life/api/v1/image-stats")
    public String url;

    @Override
    public String validateConfig() {
        if (authentication instanceof APIKeyAuth) {
            APIKeyAuth apiKeyAuth = (APIKeyAuth) authentication;
            if (apiKeyAuth.apiKey.isBlank() || apiKeyAuth.apiKey == null) {
                return "apiKey must be set";
            }
        }

        return null;
    }

}
