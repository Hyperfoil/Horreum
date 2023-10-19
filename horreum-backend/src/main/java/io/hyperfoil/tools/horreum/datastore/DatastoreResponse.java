package io.hyperfoil.tools.horreum.datastore;

import com.fasterxml.jackson.databind.JsonNode;

public class DatastoreResponse {
    public final JsonNode payload;
    public final JsonNode metadata;

    public DatastoreResponse(JsonNode payload, JsonNode metadata) {
        this.payload = payload;
        this.metadata = metadata;
    }
}
