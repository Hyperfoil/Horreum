package io.hyperfoil.tools.horreum.datastore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.data.datastore.CollectorApiDatastoreConfig;
import io.hyperfoil.tools.horreum.api.data.datastore.DatastoreType;
import io.hyperfoil.tools.horreum.api.data.datastore.auth.APIKeyAuth;
import io.hyperfoil.tools.horreum.entity.backend.DatastoreConfigDAO;
import io.hyperfoil.tools.horreum.svc.ServiceException;
import io.quarkus.logging.Log;

@ApplicationScoped
public class CollectorApiDatastore implements Datastore {

    @Inject
    ObjectMapper mapper;

    @Override
    public DatastoreResponse handleRun(JsonNode payload,
            JsonNode metaData,
            DatastoreConfigDAO configuration,
            Optional<String> schemaUriOptional)
            throws BadRequestException {

        if (metaData != null) {
            Log.warnf("Empty request: %s", metaData);
            throw ServiceException.badRequest("Empty request: " + metaData);
        }
        metaData = payload;

        final CollectorApiDatastoreConfig jsonDatastoreConfig = getCollectorApiDatastoreConfig(configuration, mapper);

        HttpClient client = HttpClient.newHttpClient();
        try {
            String tag = payload.get("tag").asText();
            String imgName = payload.get("imgName").asText();
            String newerThan = payload.get("newerThan").asText().replace(" ", "%20"); // Handle spaces in dates
            String olderThan = payload.get("olderThan").asText().replace(" ", "%20");

            verifyPayload(mapper, jsonDatastoreConfig, client, tag, newerThan, olderThan);

            URI uri = URI.create(jsonDatastoreConfig.url
                    + "?tag=" + tag
                    + "&imgName=" + imgName
                    + "&newerThan=" + newerThan
                    + "&olderThan=" + olderThan);
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri);
            builder.header("Content-Type", "application/json");
            if (jsonDatastoreConfig.authentication instanceof APIKeyAuth) {
                builder.header("token", ((APIKeyAuth) jsonDatastoreConfig.authentication).apiKey);
            }
            HttpRequest request = builder.build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != Response.Status.OK.getStatusCode()) {
                Log.errorf("Collector API returned %d body : %s", response.statusCode(), response.body());
                throw ServiceException
                        .serverError("Collector API returned " + response.statusCode() + " body : " + response.body());
            }

            payload = mapper.readTree(response.body());
            return new DatastoreResponse(payload, metaData);
        } catch (JsonProcessingException e) {
            Log.error("Error while parsing response from collector API ", e);
            throw ServiceException.serverError("Error while parsing response from collector API");
        } catch (IOException | InterruptedException e) {
            Log.error("Error while sending request to collector API", e);
            throw ServiceException.serverError("Error while sending request to collector API");
        }
    }

    private static void verifyPayload(ObjectMapper mapper, CollectorApiDatastoreConfig jsonDatastoreConfig,
            HttpClient client, String tag, String newerThan, String olderThan)
            throws IOException, InterruptedException {
        // Verify that the tag is in the distinct list of tags
        URI tagsUri = URI.create(jsonDatastoreConfig.url + "/tags/distinct");
        HttpRequest.Builder tagsBuilder = HttpRequest.newBuilder().uri(tagsUri);

        tagsBuilder
                .header("Content-Type", "application/json");

        if (jsonDatastoreConfig.authentication instanceof APIKeyAuth) {
            tagsBuilder
                    .header("token", ((APIKeyAuth) jsonDatastoreConfig.authentication).apiKey);
        }
        HttpResponse<String> response = client.send(tagsBuilder.build(), HttpResponse.BodyHandlers.ofString());
        String[] distinctTags;
        try {
            distinctTags = mapper.readValue(response.body(), String[].class);
        } catch (JsonProcessingException e) {
            Log.errorf("Error while parsing response from collector API: %s", response.body(), e);
            throw ServiceException.badRequest("Error while parsing response from collector API " + response.body());
        }
        if (distinctTags == null || distinctTags.length == 0) {
            Log.warn("No tags found in collector API");
            throw ServiceException.badRequest("No tags found in collector API");
        }
        if (Arrays.stream(distinctTags).noneMatch(tag::equals)) {
            String tags = String.join(",", distinctTags);
            throw ServiceException.badRequest("Tag not found in list of distinct tags: " + tags);
        }
        // Verify that the dates format is correct
        final String DATE_FORMAT = "yyyy-MM-dd%20HH:mm:ss.SSS";
        final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT);
        try {
            final LocalDateTime oldest = LocalDateTime.parse(newerThan, DATE_FORMATTER);
            final LocalDateTime newest = LocalDateTime.parse(olderThan, DATE_FORMATTER);
            if (oldest.isAfter(newest)) {
                throw ServiceException.badRequest(
                        "newerThan must be before olderThan (newerThan=" + newerThan + " olderThan=" + olderThan + ")");
            }
        } catch (DateTimeParseException e) {
            throw ServiceException.badRequest(
                    "Invalid date format (" + newerThan + ", " + olderThan + "). Dates must be in the format " + DATE_FORMAT);
        }
    }

    private static CollectorApiDatastoreConfig getCollectorApiDatastoreConfig(DatastoreConfigDAO configuration,
            ObjectMapper mapper) {
        final CollectorApiDatastoreConfig jsonDatastoreConfig;
        try {
            jsonDatastoreConfig = mapper.treeToValue(configuration.configuration,
                    CollectorApiDatastoreConfig.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        if (jsonDatastoreConfig == null) {
            Log.errorf("Could not find collector API datastore: %s", configuration.name);
            throw ServiceException.serverError("Could not find CollectorAPI datastore: " + configuration.name);
        }
        if (jsonDatastoreConfig.authentication instanceof APIKeyAuth) {
            assert ((APIKeyAuth) jsonDatastoreConfig.authentication).apiKey != null : "API key must be set";
        }
        assert jsonDatastoreConfig.url != null : "URL must be set";
        return jsonDatastoreConfig;
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
