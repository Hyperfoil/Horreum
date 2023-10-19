package io.hyperfoil.tools.horreum.datastore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hyperfoil.tools.horreum.api.data.datastore.DatastoreType;
import io.hyperfoil.tools.horreum.entity.backend.DatastoreConfigDAO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;

import java.util.Optional;

@ApplicationScoped
public class PostgresDatastore implements Datastore {

    @Override
    public DatastoreResponse handleRun(JsonNode payload,
                              JsonNode metadata,
                              DatastoreConfigDAO configuration,
                              Optional<String> schemaUriOptional,
                              ObjectMapper mapper)
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


}
