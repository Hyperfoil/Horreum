package io.hyperfoil.tools.horreum.action;

import static io.hyperfoil.tools.horreum.action.ActionUtil.replaceExpressions;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.hyperfoil.tools.horreum.svc.Util;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.WebClient;

@ApplicationScoped
public class GithubAction implements ActionPlugin {
   private static final Logger log = Logger.getLogger(GithubAction.class);

   @Inject
   Vertx vertx;

   @Inject
   Instance<BodyFormatter> formatters;

   WebClient http1xClient;

   @PostConstruct()
   public void postConstruct(){
      WebClientOptions options = new WebClientOptions()
            .setFollowRedirects(false)
            .setMaxPoolSize(1) // we won't use more than 1 connection to prevent GitHub rate limiting
            .setConnectTimeout(2_000) // only wait 2s
            .setKeepAlive(false);
      http1xClient = WebClient.create(vertx, new WebClientOptions(options).setProtocolVersion(HttpVersion.HTTP_1_1));
   }


   @Override
   public String type() {
      return "github";
   }

   @Override
   public void validate(JsonNode config, JsonNode secrets) {
      requireProperties(secrets, "token");
      requireProperties(config, "formatter");
      if (!config.hasNonNull("issueUrl")) {
         requireProperties(config, "token", "owner", "repo", "issue");
      }
   }

   private void requireProperties(JsonNode configuration, String... properties) {
      for (String property : properties) {
         if (!configuration.hasNonNull(property)) {
            throw missing(property);
         }
      }
   }

   private IllegalArgumentException missing(String property) {
      return new IllegalArgumentException("Configuration is missing property '" + property + "'");
   }

   @Override
   public Uni<String> execute(JsonNode config, JsonNode secrets, Object payload) {
      JsonNode json = Util.OBJECT_MAPPER.valueToTree(payload);
      // Token should NOT be in the dataset!
      String token = secrets.path("token").asText();
      if (token == null || token.isBlank()) {
         throw new IllegalArgumentException("Missing access token!");
      }
      String formatter = replaceExpressions(config.path("formatter").asText(), json);
      String body = formatters.stream().filter(f -> f.name().equals(formatter)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown body formatter '" + formatter + "'"))
            .format(config, payload);

      String issueUrl = replaceExpressions(config.path("issueUrl").asText(), json);
      String path;
      if (issueUrl != null) {
         try {
            URL url = new URL(issueUrl);
            String urlPath = url.getPath();
            if ("github.com".equalsIgnoreCase(url.getHost())) {
               // this is not API endpoint but the HTML page
               path = "/repos" + urlPath + "/comments";
            } else if ("api.github.com".equalsIgnoreCase(url.getHost())) {
               path = urlPath;
            } else {
               throw new IllegalArgumentException("Not a GitHub URL: " + issueUrl);
            }
         } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Cannot parse URL: " + issueUrl);
         }
      } else {
         String owner = replaceExpressions(config.path("owner").asText(), json);
         String repo = replaceExpressions(config.path("repo").asText(), json);
         String issue = replaceExpressions(config.path("issue").asText(), json);

         path = "/repos/" + owner + "/" + repo + "/issues/" + issue + "/comments";
      }

      RequestOptions options = new RequestOptions()
            .setHost("api.github.com")
            .setPort(443)
            .setURI(path)
            .setSsl(true);
      return http1xClient.request(HttpMethod.POST, options)
            .putHeader("Content-Type", "application/vnd.github+json")
            .putHeader("User-Agent", "Horreum")
            .putHeader("Authorization", "token " + token)
            .sendBuffer(Buffer.buffer(JsonNodeFactory.instance.objectNode().put("body", body).toString()))
            .onItem().transformToUni(response -> {
               if (response.statusCode() < 400) {
                  return Uni.createFrom().item(String.format("Successfully(%d) added comment to %s", response.statusCode(), path));
               } else if (response.statusCode() == 403 && response.getHeader("Retry-After") != null) {
                  int retryAfter = Integer.parseInt(response.getHeader("Retry-After"));
                  log.warnf("Exceeded Github request limits, retrying after %d seconds", retryAfter);
                  return Uni.createFrom().emitter(em -> {
                     vertx.setTimer(TimeUnit.SECONDS.toMillis(retryAfter), id -> execute(config, secrets, payload)
                           .subscribe().with(em::complete, em::fail));
                  });
               } else {
                  return Uni.createFrom().failure(new RuntimeException(
                        String.format("Failed to add comment to %s, response %d: %s",
                              path, response.statusCode(), response.bodyAsString())));
               }
            }).onFailure().transform(t -> new RuntimeException("Failed to add comment to " + path + ": " + t.getMessage()));
   }
}
