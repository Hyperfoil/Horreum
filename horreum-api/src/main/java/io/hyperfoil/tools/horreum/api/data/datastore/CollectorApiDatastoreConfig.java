package io.hyperfoil.tools.horreum.api.data.datastore;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(type = SchemaType.OBJECT, required = true,
        description = "Type of backend datastore")
public class CollectorApiDatastoreConfig extends BaseDatastoreConfig {

    public CollectorApiDatastoreConfig() {
        super(false);
    }

    @Schema(type = SchemaType.STRING, required = true,
            description = "Collector API KEY")
    public String apiKey;

    @Schema(type = SchemaType.STRING, required = true,
            description = "Collector url")
    public String url;

    @Override
    public String validateConfig() {
        if ( "".equals(apiKey) ) {
            return "apiKey must be set";
        }

        return null;
    }


}
