package io.hyperfoil.tools.horreum.api.data.datastore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.type.TypeReference;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(type = SchemaType.STRING, required = true,
        description = "Type of backend datastore")
public enum DatastoreType {
    POSTGRES("POSTGRES", new TypeReference<PostgresDatastoreConfig>() {}),
    ELASTICSEARCH ("ELASTICSEARCH", new TypeReference<ElasticsearchDatastoreConfig>() {}),
    COLLECTORAPI("COLLECTORAPI", new TypeReference<CollectorApiDatastoreConfig>() {});
    private static final DatastoreType[] VALUES = values();

    private final String name;
    private final TypeReference<? extends BaseDatastoreConfig> typeReference;

    private <T extends BaseDatastoreConfig> DatastoreType(String name, TypeReference<T> typeReference) {
        this.typeReference = typeReference;
        this.name = name;
    }

    public <T extends BaseDatastoreConfig> TypeReference<T> getTypeReference(){
        return (TypeReference<T>) typeReference;
    }

    @JsonCreator
    public static DatastoreType fromString(String str) {
        try {
            return VALUES[Integer.parseInt(str)];
        } catch (NumberFormatException e) {
            return DatastoreType.valueOf(str);
        }
    }
}
