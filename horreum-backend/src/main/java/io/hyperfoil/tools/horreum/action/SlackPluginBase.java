package io.hyperfoil.tools.horreum.action;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;

public abstract class SlackPluginBase {

    @Inject
    Vertx vertx;

    @Inject
    SlackClient client;

    @Inject
    Instance<BodyFormatter> formatters;

    protected void requireProperties(JsonNode configuration, String... properties) {
        for (String property : properties) {
            if (!configuration.hasNonNull(property)) {
                throw missing(property);
            }
        }
    }

    private IllegalArgumentException missing(String property) {
        return new IllegalArgumentException("Configuration is missing property '" + property + "'");
    }

    protected Uni<HttpResponse<Buffer>> post(String path, JsonNode secrets, JsonNode payload) {
        // Token should NOT be in the dataset!
        String token = secrets.path("token").asText();
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Missing access token!");
        }
        Log.debugf("POST %s (%s): %s", path, token, payload);
        URL url;
        try {
            url = new URL(path);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("URL cannot be parsed: " + path);
        }
        RequestOptions options = new RequestOptions()
                .setHost(url.getHost())
                .setPort(url.getPort())
                .setURI(url.getPath())
                .setSsl("https".equalsIgnoreCase(url.getProtocol()));
        return client.httpClient().request(HttpMethod.POST, options)
                .putHeader("Content-Type",
                        "application/json; charset=utf-8")
                .putHeader("User-Agent", "Horreum")
                .putHeader("Authorization", "Bearer " + token)
                .sendBuffer(Buffer.buffer(payload.toString()));
    }

    public abstract Uni<String> execute(JsonNode config, JsonNode secrets, Object payload);

    protected Uni<String> retry(HttpResponse<Buffer> response, JsonNode config, JsonNode secrets, Object payload) {
        int retryAfter = Integer.parseInt(response.getHeader("Retry-After"));
        Log.warnf("Exceeded server request limits, retrying after %d seconds", retryAfter);
        return Uni.createFrom()
                .emitter(em -> vertx.setTimer(TimeUnit.SECONDS.toMillis(retryAfter), id -> execute(config, secrets, payload)
                        .subscribe().with(em::complete, em::fail)));
    }

    protected BodyFormatter getFormatter(String formatter) {
        return formatters.stream().filter(f -> f.name().equals(formatter)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown body formatter '" + formatter + "'"));
    }
}
