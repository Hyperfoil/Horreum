package io.hyperfoil.tools.horreum.api.data.datastore;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@Schema(type = SchemaType.OBJECT, required = true, description = "Type of backend datastore")
public class ElasticsearchDatastoreConfig extends BaseDatastoreConfig {

    public ElasticsearchDatastoreConfig() {
        super(false);
    }

    @Schema(type = SchemaType.STRING, description = "Elasticsearch API KEY")
    public String apiKey;

    @Schema(type = SchemaType.STRING, required = true, description = "Elasticsearch url")
    public String url;

    @Schema(type = SchemaType.STRING, description = "Elasticsearch username")
    public String username;

    @Schema(type = SchemaType.STRING, description = "Elasticsearch password")
    @JsonIgnore
    public String password;

    @JsonProperty("password")
    public void setSecrets(String password) {
        this.password = password;
    }

    @JsonProperty("password")
    public String getMaskedSecrets() {
        if (this.password != null) {
            return "********";
        } else {
            return null;
        }
    }

    @Override
    public String validateConfig() {
        if ("".equals(apiKey) && ("".equals(username) || "".equals(password))) {
            return "Either apiKey or username and password must be set";
        }

        if (!"".equals(apiKey) && !("".equals(username) || "".equals(password))) {
            return "Only apiKey or username and password can be set";
        }

        return null;
    }

}
