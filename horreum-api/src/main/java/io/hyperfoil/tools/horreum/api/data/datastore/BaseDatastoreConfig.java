package io.hyperfoil.tools.horreum.api.data.datastore;

import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.hyperfoil.tools.horreum.api.data.datastore.auth.APIKeyAuth;
import io.hyperfoil.tools.horreum.api.data.datastore.auth.NoAuth;
import io.hyperfoil.tools.horreum.api.data.datastore.auth.UsernamePassAuth;

public abstract class BaseDatastoreConfig {

    @NotNull
    @JsonProperty(required = true)
    @Schema(type = SchemaType.OBJECT, discriminatorProperty = "type", discriminatorMapping = {
            @DiscriminatorMapping(schema = NoAuth.class, value = NoAuth._TYPE),
            @DiscriminatorMapping(schema = APIKeyAuth.class, value = APIKeyAuth._TYPE),
            @DiscriminatorMapping(schema = UsernamePassAuth.class, value = UsernamePassAuth._TYPE)
    }, oneOf = { //subtype mapping for openapi
            NoAuth.class,
            APIKeyAuth.class,
            UsernamePassAuth.class
    })
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({ //subtype mapping for jackson
            @JsonSubTypes.Type(value = NoAuth.class, name = NoAuth._TYPE),
            @JsonSubTypes.Type(value = APIKeyAuth.class, name = APIKeyAuth._TYPE),
            @JsonSubTypes.Type(value = UsernamePassAuth.class, name = UsernamePassAuth._TYPE)
    })
    public Object authentication; //the python generator is failing if this is a concrete type

    @Schema(type = SchemaType.BOOLEAN, required = true, description = "Built In")
    public Boolean builtIn;

    public BaseDatastoreConfig(Boolean builtIn) {
        this.builtIn = builtIn;
    }

    public BaseDatastoreConfig() {
        this.builtIn = false;
    }

    public abstract String validateConfig();

}
