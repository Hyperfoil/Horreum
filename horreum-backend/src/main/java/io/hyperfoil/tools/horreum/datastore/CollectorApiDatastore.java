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

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.data.datastore.CollectorApiDatastoreConfig;
import io.hyperfoil.tools.horreum.api.data.datastore.DatastoreType;
import io.hyperfoil.tools.horreum.entity.backend.DatastoreConfigDAO;
import io.hyperfoil.tools.horreum.svc.ServiceException;

@ApplicationScoped
public class CollectorApiDatastore implements Datastore {

    protected static final Logger log = Logger.getLogger(CollectorApiDatastore.class);

    @Inject
    ObjectMapper mapper;

    @Override
    public DatastoreResponse handleRun(JsonNode payload,
            JsonNode metaData,
            DatastoreConfigDAO configuration,
            Optional<String> schemaUriOptional)
            throws BadRequestException {

        if (metaData != null) {
            log.warn("Empty request: " + metaData);
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
            builder.header("Content-Type", "application/json")
                    .header("token", jsonDatastoreConfig.apiKey);
            HttpRequest request = builder.build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != Response.Status.OK.getStatusCode()) {
                log.error("Collector API returned " + response.statusCode() + " body : " + response.body());
                throw ServiceException
                        .serverError("Collector API returned " + response.statusCode() + " body : " + response.body());
            }

            payload = mapper.readTree(response.body());
            return new DatastoreResponse(payload, metaData);
        } catch (JsonProcessingException e) {
            log.error("Error while parsing responde from collector API ", e);
            throw ServiceException.serverError("Error while sending request to collector API");
        } catch (IOException | InterruptedException e) {
            log.error("Error while sending request to collector API", e);
            throw ServiceException.serverError("Error while sending request to collector API");
        }
    }

    private static void verifyPayload(ObjectMapper mapper, CollectorApiDatastoreConfig jsonDatastoreConfig,
            HttpClient client, String tag, String newerThan, String olderThan)
            throws IOException, InterruptedException {
        // Verify that the tag is in the distinct list of tags
        URI tagsUri = URI.create(jsonDatastoreConfig.url + "/tags/distinct");
        HttpRequest.Builder tagsBuilder = HttpRequest.newBuilder().uri(tagsUri);
        HttpRequest tagsRequest = tagsBuilder
                .header("Content-Type", "application/json")
                .header("token", jsonDatastoreConfig.apiKey).build();
        HttpResponse<String> response = client.send(tagsRequest, HttpResponse.BodyHandlers.ofString());
        String[] distinctTags;
        try {
            distinctTags = mapper.readValue(response.body(), String[].class);
        } catch (JsonProcessingException e) {
            log.error("Error while parsing response from collector API: " + response.body(), e);
            throw ServiceException.badRequest("Error while parsing response from collector API " + response.body());
        }
        if (distinctTags == null || distinctTags.length == 0) {
            log.warn("No tags found in collector API");
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
            log.error("Could not find collector API datastore: " + configuration.name);
            throw ServiceException.serverError("Could not find CollectorAPI datastore: " + configuration.name);
        }
        assert jsonDatastoreConfig.apiKey != null : "API key must be set";
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
