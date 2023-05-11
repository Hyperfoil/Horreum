package io.hyperfoil.tools.horreum.action;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.runtime.Startup;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;

@ApplicationScoped
@Startup
public class GitHubClient {
   @Inject
   Vertx vertx;

   private WebClient httpClient;

   @PostConstruct
   public void postConstruct(){
      WebClientOptions options = new WebClientOptions()
            .setFollowRedirects(false)
            .setMaxPoolSize(1) // we won't use more than 1 connection to prevent GitHub rate limiting
            .setConnectTimeout(2_000) // only wait 2s
            .setKeepAlive(false);
      httpClient = WebClient.create(vertx, new WebClientOptions(options).setProtocolVersion(HttpVersion.HTTP_1_1));
   }

   public WebClient httpClient() {
      return httpClient;
   }
}
