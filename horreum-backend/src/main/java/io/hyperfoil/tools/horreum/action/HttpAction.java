package io.hyperfoil.tools.horreum.action;

import java.net.MalformedURLException;
import java.net.URL;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.entity.data.AllowedSiteDAO;
import io.hyperfoil.tools.horreum.svc.ServiceException;
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
public class HttpAction implements ActionPlugin {
   private static final Logger log = Logger.getLogger(HttpAction.class);

   public static final String TYPE_HTTP = "http";

   @Inject
   Vertx reactiveVertx;

   @ConfigProperty(name = "horreum.hook.tls.insecure", defaultValue = "false")
   boolean insecureTls;

   @ConfigProperty(name = "horreum.hook.maxConnections", defaultValue = "20")
   int maxConnections;

   WebClient http1xClient;

   @PostConstruct()
   public void postConstruct(){
      WebClientOptions options = new WebClientOptions()
            .setFollowRedirects(false)
            .setMaxPoolSize(maxConnections)
            .setConnectTimeout(2_000) // only wait 2s
            .setKeepAlive(false);
      if (insecureTls) {
         options.setVerifyHost(false);
         options.setTrustAll(true);
      }
      http1xClient = WebClient.create(reactiveVertx, new WebClientOptions(options).setProtocolVersion(HttpVersion.HTTP_1_1));
   }


   @Override
   public String type() {
      return TYPE_HTTP;
   }

   @Override
   public void validate(JsonNode config, JsonNode secrets) {
      String url = config.path("url").asText();
      if (AllowedSiteDAO.find("?1 LIKE CONCAT(prefix, '%')", url).count() == 0) {
         throw ServiceException.badRequest("The requested URL is not on the list of allowed URL prefixes; " +
               "visit /api/hook/prefixes to see this list. Only the administrator is allowed to add prefixes.");
      }
   }

   @Override
   public Uni<String> execute(JsonNode config, JsonNode secrets, Object payload) {
      String urlPattern = config.path("url").asText();
      if (urlPattern == null) {
         throw new IllegalArgumentException("URL is not configured.");
      }
      String input = urlPattern.startsWith("http") ? urlPattern : "http://" + urlPattern;
      JsonNode body = Util.OBJECT_MAPPER.valueToTree(payload);
      String replacedUrl = ActionUtil.replaceExpressions(input, body);
      URL url;
      try {
         url = new URL(replacedUrl);
      } catch (MalformedURLException e) {
         throw new IllegalArgumentException("URL cannot be parsed: " + replacedUrl);
      }
      RequestOptions options = new RequestOptions()
               .setHost(url.getHost())
               .setPort(url.getPort() >= 0 ? url.getPort() : url.getDefaultPort())
               .setURI(url.getFile())
               .setSsl("https".equalsIgnoreCase(url.getProtocol()));
      log.infof("Sending event to %s", url);
      return http1xClient.request(HttpMethod.POST, options)
            .putHeader("Content-Type", "application/json")
            .sendBuffer(Buffer.buffer(body.toString()))
            .onItem().transform(response -> {
         if (response.statusCode() < 400) {
            return String.format("Successfully(%d) notified hook: %s", response.statusCode(), url);
         } else {
            throw new IllegalArgumentException("Failed to POST " + url + ", response " + response.statusCode() + ": " + response.bodyAsString());
         }
      }).onFailure().transform(t -> new RuntimeException("Failed to POST " + url + ": " + t.getMessage()));
   }
}
