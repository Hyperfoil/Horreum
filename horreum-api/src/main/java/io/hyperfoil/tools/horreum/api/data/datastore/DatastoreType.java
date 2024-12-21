package io.hyperfoil.tools.horreum.api.data.datastore;

import java.lang.reflect.Field;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.quarkus.logging.Log;

@Schema(type = SchemaType.STRING, required = true, description = "Type of backend datastore")
public enum DatastoreType {
    POSTGRES(PostgresDatastoreConfig.class),
    ELASTICSEARCH(ElasticsearchDatastoreConfig.class),
    COLLECTORAPI(CollectorApiDatastoreConfig.class);

    private static final DatastoreType[] VALUES = values();
    private final String label;
    private final String name;
    private final String[] supportedAuths;
    private final Boolean buildIn;
    private final Class<? extends BaseDatastoreConfig> klass;

    <T extends BaseDatastoreConfig> DatastoreType(Class<T> klass) {
        this.klass = klass;
        this.label = extractField(klass, "label");
        this.name = extractField(klass, "name");
        this.supportedAuths = extractField(klass, "auths");
        this.buildIn = extractField(klass, "builtIn");
    }

    private static <T, K> T extractField(Class<K> klass, String name) {
        try {
            Field supportedAuthField = klass.getField(name);
            return (T) supportedAuthField.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.errorf("Could not extract field %s from class %s", name, klass.getName(), e);
            return null;
        } catch (NullPointerException e) {
            Log.errorf("Could not extract field %s from class %s", name, klass.getName(), e);
            return null;
        }
    }

    public <T extends BaseDatastoreConfig> Class<T> getTypeReference() {
        return (Class<T>) klass;
    }

    @JsonCreator
    public static DatastoreType fromString(String str) {
        try {
            return VALUES[Integer.parseInt(str)];
        } catch (NumberFormatException e) {
            return DatastoreType.valueOf(str);
        }
    }

    public TypeConfig getConfig() {
        return new TypeConfig(this, name, label, buildIn, supportedAuths);
    }

    public static class TypeConfig {
        public String enumName;
        public String name;
        public String label;

        public String[] supportedAuths;
        public Boolean builtIn;

        public TypeConfig(DatastoreType type, String name, String label, Boolean builtIn, String[] supportedAuths) {
            this.enumName = type.name();
            this.name = name;
            this.label = label;
            this.builtIn = builtIn;
            this.supportedAuths = supportedAuths;
        }
    }
}
