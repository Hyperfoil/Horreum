package io.hyperfoil.tools.horreum.datastore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hyperfoil.tools.horreum.api.data.datastore.DatastoreType;
import io.hyperfoil.tools.horreum.api.data.datastore.CollectorApiDatastoreConfig;
import io.hyperfoil.tools.horreum.entity.backend.DatastoreConfigDAO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

@ApplicationScoped
public class CollectorApiDatastore implements Datastore {

    protected static final Logger log = Logger.getLogger(CollectorApiDatastore.class);

    @Inject
    ObjectMapper mapper;

    @Override
    public DatastoreResponse handleRun(JsonNode payload,
                              JsonNode metaData,
                              DatastoreConfigDAO configuration,
                              Optional<String> schemaUriOptional,
                              ObjectMapper mapper)
            throws BadRequestException{

        try {

            if ( metaData != null ){
                log.warn("Empty request: " + metaData.toString());
                throw new BadRequestException("Empty request: " + metaData);
            }
            metaData = payload;

            final CollectorApiDatastoreConfig jsonDatastoreConfig = mapper.treeToValue(configuration.configuration, CollectorApiDatastoreConfig.class);

            if ( jsonDatastoreConfig != null ){

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(java.net.URI.create(jsonDatastoreConfig.url + "/api/v1/image-stats/tag/" + payload.get("tag").asText()));

                if( jsonDatastoreConfig.apiKey != null) {
                    builder.header("Content-Type", "application/json")
                            .header("token", jsonDatastoreConfig.apiKey);
                } else {
                    throw new BadRequestException("API Key is required for JSON Datastore");
                }

                HttpRequest request = builder.build();

                if ( request == null ) {
                    log.warn("Could not find collector API datastore: " + configuration.name);
                    throw new BadRequestException("Could not find collector API datastore: " + configuration.name);
                }

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                payload = mapper.readTree(response.body());

                return new DatastoreResponse(payload, metaData);
            }
            else {
                throw new RuntimeException("Could not find CollectorAPI datastore: " + configuration.name);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DatastoreType type() {
        return DatastoreType.COLLECTORAPI;
    }

    @Override
    public UploadType uploadType() {
        return UploadType.MUILTI;
    }

    @Override
    public String validateConfig(Object config) {
        try {
            return mapper.treeToValue((ObjectNode) config, CollectorApiDatastoreConfig.class).validateConfig();
        } catch (JsonProcessingException e) {
            return "Unable to read configuration. if the problem persists, please contact a system administrator";
        }
    }

}


