package io.hyperfoil.tools.horreum.action;

import static io.hyperfoil.tools.horreum.action.ActionUtil.replaceExpressions;

import java.net.MalformedURLException;
import java.net.URL;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.hyperfoil.tools.horreum.svc.Util;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class GitHubIssueCommentAction extends GitHubPluginBase implements ActionPlugin {
   public static final String TYPE_GITHUB_ISSUE_COMMENT = "github-issue-comment";

   @Override
   public String type() {
      return TYPE_GITHUB_ISSUE_COMMENT;
   }

   @Override
   public void validate(JsonNode config, JsonNode secrets) {
      requireProperties(secrets, "token");
      requireProperties(config, "formatter");
      if (!config.hasNonNull("issueUrl")) {
         requireProperties(config, "owner", "repo", "issue");
      }
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
      String comment = getFormatter(formatter).format(config, payload);

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

      return post(path, secrets, JsonNodeFactory.instance.objectNode().put("body", comment))
            .onItem().transformToUni(response -> {
               if (response.statusCode() < 400) {
                  return Uni.createFrom().item(String.format("Successfully(%d) added comment to %s", response.statusCode(), path));
               } else if (response.statusCode() == 403 && response.getHeader("Retry-After") != null) {
                  return retry(response, config, secrets, payload);

               } else {
                  return Uni.createFrom().failure(new RuntimeException(
                        String.format("Failed to add comment to %s, response %d: %s",
                              path, response.statusCode(), response.bodyAsString())));
               }
            }).onFailure().transform(t -> new RuntimeException("Failed to add comment to " + path + ": " + t.getMessage()));
   }
}
