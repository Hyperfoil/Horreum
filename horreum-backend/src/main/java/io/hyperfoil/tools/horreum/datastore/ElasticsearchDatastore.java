package io.hyperfoil.tools.horreum.datastore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hyperfoil.tools.horreum.api.data.datastore.DatastoreType;
import io.hyperfoil.tools.horreum.api.data.datastore.ElasticsearchDatastoreConfig;
import io.hyperfoil.tools.horreum.entity.backend.DatastoreConfigDAO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.jboss.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ElasticsearchDatastore implements Datastore {

    protected static final Logger log = Logger.getLogger(ElasticsearchDatastore.class);

    Map<String, RestClient> hostCache = new ConcurrentHashMap<>();

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

            final ElasticsearchDatastoreConfig elasticsearchDatastoreConfig = mapper.treeToValue(configuration.configuration, ElasticsearchDatastoreConfig.class);

            if ( elasticsearchDatastoreConfig != null ){

                RestClientBuilder builder = RestClient.builder(HttpHost.create(elasticsearchDatastoreConfig.url))
                            .setDefaultHeaders(new Header[]{
                                    new BasicHeader("Authorization", "ApiKey " + elasticsearchDatastoreConfig.apiKey)
                            });

                RestClient restClient = builder.build();

                if ( restClient == null ) {
                    log.warn("Could not find elasticsearch datastore: " + configuration.name);
                    throw new BadRequestException("Could not find elasticsearch datastore: " + configuration.name);
                }

                ElasticRequest apiRequest;
                try{
                    apiRequest = mapper.treeToValue(payload, ElasticRequest.class);
                } catch (JsonProcessingException e) {
                    String msg = String.format("Could not parse request: %s, %s", metaData.toString(), e.getMessage());
                    log.warn(msg);
                    throw new BadRequestException(msg);
                }

                Request request;
                String finalString;
                switch (apiRequest.type){
                    case DOC:
                        request = new Request(
                                "GET",
                                "/" + apiRequest.index  + "/_doc/" + apiRequest.query.textValue());

                        try {
                            finalString = extracted(restClient, request);
                        } catch (IOException e) {
                            String msg = String.format("Could not query doc request: %s, %s", metaData.toString(), e.getMessage());
                            log.warn(msg);
                            throw new BadRequestException(msg);
                        }

                        return new DatastoreResponse(mapper.readTree(finalString).get("_source"), payload);
                    case SEARCH:
                        String schemaUri = schemaUriOptional.orElse(null);
                        if( schemaUri == null){
                            throw new BadRequestException("Schema is required for search requests");
                        }

                        request = new Request(
                                "GET",
                                "/" + apiRequest.index  + "/_search");
                        request.setJsonEntity(mapper.writeValueAsString(apiRequest.query));
                        finalString = extracted(restClient, request);

                        ArrayNode elasticResults = (ArrayNode) mapper.readTree(finalString).get("hits").get("hits");
                        ArrayNode extractedResults = mapper.createArrayNode();

                        elasticResults.forEach(jsonNode -> extractedResults.add(((ObjectNode) jsonNode.get("_source")).put("$schema", schemaUri)));

                        return  new DatastoreResponse(extractedResults, payload);
                    default:
                        throw new BadRequestException("Invalid request type: " + apiRequest.type);
                }
            }
            else {
                throw new RuntimeException("Could not find elasticsearch datastore: " + configuration.name);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String extracted(RestClient restClient, Request request) throws IOException {
        Response response = restClient.performRequest(request);

        ByteArrayOutputStream stream= new ByteArrayOutputStream();
        response.getEntity().writeTo(stream);

        return new String(stream.toByteArray());
    }

    @Override
    public DatastoreType type() {
        return DatastoreType.ELASTICSEARCH;
    }

    @Override
    public UploadType uploadType() {
        return UploadType.MUILTI;
    }

    private static class ElasticRequest {
        public String index;
        public RequestType type;
        public JsonNode query;

    }

    private enum RequestType {
        DOC ("doc"),
        SEARCH ("search");
        private final String type;

        RequestType(String s) {
            type = s;
        }
    }

}


