package io.hyperfoil.tools.horreum.action;

import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;

public abstract class GitHubPluginBase {
   protected static final Logger log = Logger.getLogger(GitHubPluginBase.class);

   @Inject
   Vertx vertx;

   @Inject
   GitHubClient client;

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
      RequestOptions options = new RequestOptions()
            .setHost("api.github.com")
            .setPort(443)
            .setURI(path)
            .setSsl(true);
      return client.httpClient().request(HttpMethod.POST, options)
            .putHeader("Content-Type", "application/vnd.github+json")
            .putHeader("User-Agent", "Horreum")
            .putHeader("Authorization", "token " + token)
            .sendBuffer(Buffer.buffer(payload.toString()));
   }

   public abstract Uni<String> execute(JsonNode config, JsonNode secrets, Object payload);

   protected Uni<String> retry(HttpResponse<Buffer> response, JsonNode config, JsonNode secrets, Object payload) {
      int retryAfter = Integer.parseInt(response.getHeader("Retry-After"));
      log.warnf("Exceeded Github request limits, retrying after %d seconds", retryAfter);
      return Uni.createFrom()
            .emitter(em -> vertx.setTimer(TimeUnit.SECONDS.toMillis(retryAfter), id -> execute(config, secrets, payload)
                  .subscribe().with(em::complete, em::fail)));
   }

   protected BodyFormatter getFormatter(String formatter) {
      return formatters.stream().filter(f -> f.name().equals(formatter)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown body formatter '" + formatter + "'"));
   }
}
