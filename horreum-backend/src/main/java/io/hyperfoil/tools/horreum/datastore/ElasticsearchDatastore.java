package io.hyperfoil.tools.horreum.datastore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.data.datastore.DatastoreType;
import io.hyperfoil.tools.horreum.api.data.datastore.ElasticsearchDatastoreConfig;
import io.hyperfoil.tools.horreum.api.data.datastore.auth.APIKeyAuth;
import io.hyperfoil.tools.horreum.api.data.datastore.auth.UsernamePassAuth;
import io.hyperfoil.tools.horreum.entity.backend.DatastoreConfigDAO;
import io.quarkus.logging.Log;

@ApplicationScoped
public class ElasticsearchDatastore implements Datastore {

    @Inject
    ObjectMapper mapper;

    @Override
    public DatastoreResponse handleRun(JsonNode payload,
            JsonNode metaData,
            DatastoreConfigDAO configuration,
            Optional<String> schemaUriOptional)
            throws BadRequestException {

        RestClient restClient = null;

        try {
            if (metaData != null) {
                Log.warnf("Empty request: %s", metaData);
                throw new BadRequestException("Empty request: " + metaData);
            }
            metaData = payload;

            final ElasticsearchDatastoreConfig elasticsearchDatastoreConfig = mapper.treeToValue(configuration.configuration,
                    ElasticsearchDatastoreConfig.class);

            if (elasticsearchDatastoreConfig != null) {

                RestClientBuilder builder = RestClient.builder(HttpHost.create(elasticsearchDatastoreConfig.url));

                if (elasticsearchDatastoreConfig.authentication instanceof APIKeyAuth) {

                    APIKeyAuth apiKeyAuth = (((APIKeyAuth) elasticsearchDatastoreConfig.authentication));

                    builder.setDefaultHeaders(new Header[] {
                            new BasicHeader("Authorization", "ApiKey " + apiKeyAuth.apiKey)
                    });

                } else if (elasticsearchDatastoreConfig.authentication instanceof UsernamePassAuth) {
                    final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

                    UsernamePassAuth usernamePassAuth = (((UsernamePassAuth) elasticsearchDatastoreConfig.authentication));

                    credentialsProvider.setCredentials(AuthScope.ANY,
                            new UsernamePasswordCredentials(usernamePassAuth.username,
                                    usernamePassAuth.password));

                    builder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                            .setDefaultCredentialsProvider(credentialsProvider));

                }

                restClient = builder.build();

                if (restClient == null) {
                    Log.warnf("Could not find elasticsearch datastore: %s", configuration.name);
                    throw new BadRequestException("Could not find elasticsearch datastore: " + configuration.name);
                }

                ElasticRequest apiRequest;
                try {
                    apiRequest = mapper.treeToValue(payload, ElasticRequest.class);
                } catch (JsonProcessingException e) {
                    String msg = "Could not parse request: %s, %s".formatted(metaData, e.getMessage());
                    Log.warn(msg);
                    throw new BadRequestException(msg);
                }

                Request request;
                String finalString;
                String schemaUri;
                ArrayNode elasticResults;
                ArrayNode extractedResults;

                switch (apiRequest.type) {
                    case DOC:
                        request = new Request(
                                "GET",
                                "/" + apiRequest.index + "/_doc/" + apiRequest.query.textValue());

                        try {
                            finalString = extracted(restClient, request);
                        } catch (IOException e) {
                            String msg = "Could not query doc request: %s, %s".formatted(metaData, e.getMessage());
                            Log.warn(msg);
                            throw new BadRequestException(msg);
                        }

                        return new DatastoreResponse(mapper.readTree(finalString).get("_source"), payload);
                    case SEARCH:
                        schemaUri = schemaUriOptional.orElse(null);
                        if (schemaUri == null) {
                            throw new BadRequestException("Schema is required for search requests");
                        }

                        request = new Request(
                                "GET",
                                "/" + apiRequest.index + "/_search");
                        request.addParameter("size", "1000");
                        request.setJsonEntity(mapper.writeValueAsString(apiRequest.query));
                        finalString = extracted(restClient, request);

                        elasticResults = (ArrayNode) mapper.readTree(finalString).get("hits").get("hits");
                        extractedResults = mapper.createArrayNode();

                        elasticResults.forEach(jsonNode -> extractedResults
                                .add(((ObjectNode) jsonNode.get("_source")).put("$schema", schemaUri)));

                        return new DatastoreResponse(extractedResults, payload);

                    case MULTI_INDEX:
                        schemaUri = schemaUriOptional.orElse(null);
                        if (schemaUri == null) {
                            throw new BadRequestException("Schema is required for search requests");
                        }

                        try {
                            final MultiIndexQuery multiIndexQuery = mapper.treeToValue(apiRequest.query, MultiIndexQuery.class);
                            //1st retrieve the list of docs from 1st Index
                            request = new Request(
                                    "GET",
                                    "/" + apiRequest.index + "/_search");

                            request.setJsonEntity(mapper.writeValueAsString(multiIndexQuery.metaQuery));
                            finalString = extracted(restClient, request);

                            elasticResults = (ArrayNode) mapper.readTree(finalString).get("hits").get("hits");
                            extractedResults = mapper.createArrayNode();

                            //2nd retrieve the docs from 2nd Index and combine into a single result with metadata and doc contents
                            final RestClient finalRestClient = restClient; //copy of restClient for use in lambda

                            elasticResults.forEach(jsonNode -> {

                                ObjectNode result = ((ObjectNode) jsonNode.get("_source")).put("$schema", schemaUri);
                                String docString = """
                                        {
                                            "error": "Could not retrieve doc from secondary index"
                                            "msg": "ERR_MSG"
                                        }
                                        """;

                                var subRequest = new Request(
                                        "GET",
                                        "/" + multiIndexQuery.targetIndex + "/_doc/"
                                                + jsonNode.get("_source").get(multiIndexQuery.docField).textValue());

                                try {
                                    docString = extracted(finalRestClient, subRequest);

                                } catch (IOException e) {

                                    docString.replaceAll("ERR_MSG", e.getMessage());
                                    Log.error("Could not query doc request: index: %s; docID: %s (%s)".formatted(
                                            multiIndexQuery.targetIndex, multiIndexQuery.docField, e.getMessage()));
                                }

                                try {
                                    result.put("$doc", mapper.readTree(docString));
                                } catch (JsonProcessingException e) {
                                    docString.replaceAll("ERR_MSG", e.getMessage());
                                    Log.error("Could not parse doc result: %s, %s".formatted(docString, e.getMessage()));
                                }

                                extractedResults.add(result);

                            });

                            return new DatastoreResponse(extractedResults, payload);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException("Could not process json query: " + e.getMessage());
                        }

                    default:
                        throw new BadRequestException("Invalid request type: " + apiRequest.type);
                }
            } else {
                throw new RuntimeException("Could not find elasticsearch datastore: " + configuration.name);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (restClient != null) {
                try {
                    restClient.close();
                } catch (IOException e) {
                    Log.errorf("Error closing rest client: %s", e.getMessage());
                }
            }
        }
    }

    private static String extracted(RestClient restClient, Request request) throws IOException {
        Response response = restClient.performRequest(request);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
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

    @Override
    public String validateConfig(Object config) {
        try {
            return mapper.treeToValue((ObjectNode) config, ElasticsearchDatastoreConfig.class).validateConfig();
        } catch (JsonProcessingException e) {
            return "Unable to read configuration. if the problem persists, please contact a system administrator";
        }
    }

    private static class ElasticRequest {
        public ElasticRequest() {
        }

        public String index;
        public RequestType type;
        public JsonNode query;

    }

    static class MultiIndexQuery {
        public MultiIndexQuery() {
        }

        public String docField;
        public String targetIndex;
        public JsonNode metaQuery;
    }

    private enum RequestType {
        DOC,
        SEARCH,
        MULTI_INDEX;

        @JsonCreator
        public static RequestType fromString(String key) {
            // ignore case when deserializing
            return key == null ? null : RequestType.valueOf(key.toUpperCase());
        }
    }

}
