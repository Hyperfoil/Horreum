package io.hyperfoil.tools.horreum.datastore;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.api.data.datastore.DatastoreType;
import io.hyperfoil.tools.horreum.entity.backend.DatastoreConfigDAO;

@ApplicationScoped
public class PostgresDatastore implements Datastore {

    @Override
    public DatastoreResponse handleRun(JsonNode payload,
            JsonNode metadata,
            DatastoreConfigDAO configuration,
            Optional<String> schemaUriOptional)
            throws BadRequestException {
        return new DatastoreResponse(payload, metadata);
    }

    @Override
    public DatastoreType type() {
        return DatastoreType.POSTGRES;
    }

    @Override
    public UploadType uploadType() {
        return UploadType.SINGLE;
    }

    @Override
    public String validateConfig(Object config) {
        //do not validate internal datastore
        return null;
    }

}
