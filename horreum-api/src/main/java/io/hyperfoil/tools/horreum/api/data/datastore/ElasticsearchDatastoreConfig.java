package io.hyperfoil.tools.horreum.api.data.datastore;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.hyperfoil.tools.horreum.api.data.datastore.auth.APIKeyAuth;
import io.hyperfoil.tools.horreum.api.data.datastore.auth.NoAuth;
import io.hyperfoil.tools.horreum.api.data.datastore.auth.UsernamePassAuth;

@Schema(type = SchemaType.OBJECT, required = true, description = "Type of backend datastore")
public class ElasticsearchDatastoreConfig extends BaseDatastoreConfig {

    public static final String[] auths = { NoAuth._TYPE, APIKeyAuth._TYPE, UsernamePassAuth._TYPE };
    public static final String name = "Elasticsearch";
    public static final String label = "Elasticsearch";
    public static final Boolean builtIn = false;

    public ElasticsearchDatastoreConfig() {

    }

    @Schema(type = SchemaType.STRING, required = true, description = "Elasticsearch url")
    public String url;

    @Override
    public String validateConfig() {

        //TODO:: replace with pattern matching after upgrading to java 17
        if (authentication instanceof APIKeyAuth) {
            APIKeyAuth apiKeyAuth = (APIKeyAuth) authentication;
            if (apiKeyAuth.apiKey == null || apiKeyAuth.apiKey.isBlank()) {
                return "apiKey must be set";
            }
        } else if (authentication instanceof UsernamePassAuth) {
            UsernamePassAuth usernamePassAuth = (UsernamePassAuth) authentication;

            if (usernamePassAuth.username == null || usernamePassAuth.username.isBlank()
                    || usernamePassAuth.password == null || usernamePassAuth.password.isBlank()) {
                return "username and password must be set";
            }
        }
        return null;
    }

}
