package io.hyperfoil.tools.horreum.action;

import static io.hyperfoil.tools.horreum.action.ActionUtil.replaceExpressions;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.hyperfoil.tools.horreum.svc.Util;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class GitHubIssueCreateAction extends GitHubPluginBase implements ActionPlugin {
   @Inject
   Instance<BodyFormatter> formatters;

   @Override
   public String type() {
      return "github-issue-create";
   }

   @Override
   public void validate(JsonNode config, JsonNode secrets) {
      requireProperties(secrets, "token");
      requireProperties(config, "formatter");
      if (!config.hasNonNull("issueUrl")) {
         requireProperties(config, "owner", "repo", "title");
      }
   }

   @Override
   public Uni<String> execute(JsonNode config, JsonNode secrets, Object payload) {
      JsonNode json = Util.OBJECT_MAPPER.valueToTree(payload);
      String formatter = replaceExpressions(config.path("formatter").asText(), json);
      String title = replaceExpressions(config.path("title").asText(), json);
      String body = getFormatter(formatter).format(config, payload);

      String owner = replaceExpressions(config.path("owner").asText(), json);
      String repo = replaceExpressions(config.path("repo").asText(), json);

      String path = "/repos/" + owner + "/" + repo + "/issues";

      return post(path, secrets, JsonNodeFactory.instance.objectNode()
            .put("title", title).put("body", body).set("labels", JsonNodeFactory.instance.arrayNode().add("horreum")))
            .onItem().transformToUni(response -> {
               if (response.statusCode() < 400) {
                  return Uni.createFrom().item(String.format("Successfully(%d) created issue in %s", response.statusCode(), path));
               } else if (response.statusCode() == 403 && response.getHeader("Retry-After") != null) {
                  return retry(response, config, secrets, payload);
               } else {
                  return Uni.createFrom().failure(new RuntimeException(
                        String.format("Failed to create issue in %s, response %d: %s",
                              path, response.statusCode(), response.bodyAsString())));
               }
            }).onFailure().transform(t -> new RuntimeException("Failed to create issue in " + path + ": " + t.getMessage()));
   }
}
