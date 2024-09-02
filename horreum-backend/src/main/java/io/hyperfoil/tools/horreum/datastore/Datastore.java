package io.hyperfoil.tools.horreum.datastore;

import java.util.Optional;

import jakarta.ws.rs.BadRequestException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.hyperfoil.tools.horreum.api.data.datastore.DatastoreType;
import io.hyperfoil.tools.horreum.entity.backend.DatastoreConfigDAO;

/*
* A Datastore represents a backend datastore that Horreum can interact with
* Horreum will call handleRun with the payload and metadata from the request
* and the datastore will return a DatastoreResponse with the resolved response
* from the backend datastore.
*/
public interface Datastore {

    DatastoreResponse handleRun(JsonNode payload, JsonNode metadata, DatastoreConfigDAO config,
            Optional<String> schemaUriOptional, ObjectMapper mapper) throws BadRequestException;

    DatastoreType type();

    UploadType uploadType();

    String validateConfig(Object config);

    enum UploadType {
        SINGLE,
        MUILTI
    }
}
