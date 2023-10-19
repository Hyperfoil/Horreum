package io.hyperfoil.tools.horreum.api.data.datastore;


import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(type = SchemaType.OBJECT, required = true,
        description = "Type of backend datastore")
public class ElasticsearchDatastoreConfig extends BaseDatastoreConfig {

    public ElasticsearchDatastoreConfig() {
        super(false);
    }

    @Schema(type = SchemaType.STRING, required = true,
            description = "Elasticsearch API KEY")
    public String apiKey;

    @Schema(type = SchemaType.STRING, required = true,
            description = "Elasticsearch url")
    public String url;


}
