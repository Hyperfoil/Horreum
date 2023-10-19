package io.hyperfoil.tools.horreum.api.data.datastore;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hyperfoil.tools.horreum.api.data.ProtectedType;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(type = SchemaType.OBJECT, required = true,
        description = "Type of backend datastore")
public class Datastore extends ProtectedType {
    @JsonProperty(required = true)
    @Schema(description="Unique Datastore id",
            example="101")
    public Integer id;

    @NotNull
    @JsonProperty(required = true)
    @Schema(description="Name of the datastore, used to identify the datastore in the Test definition",
            example="Perf Elasticsearch")
    public String name;

    @NotNull
    @JsonProperty(required = true)
    @Schema(description="Is this a built-in datastore? Built-in datastores cannot be deleted or modified",
            example="false")
    public Boolean builtIn;

    @NotNull
    @JsonProperty( required = true )
    @Schema(type = SchemaType.OBJECT,
            oneOf = {
                ElasticsearchDatastoreConfig.class,
                    PostgresDatastoreConfig.class
    })
    public ObjectNode config;

    @NotNull
    @JsonProperty(required = true)
    @Schema( type = SchemaType.STRING, implementation = DatastoreType.class, example = "ELASTICSEARCH")
    public DatastoreType type;


}
