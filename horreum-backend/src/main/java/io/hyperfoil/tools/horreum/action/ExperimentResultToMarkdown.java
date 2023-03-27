package io.hyperfoil.tools.horreum.action;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.api.services.ExperimentService;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;

@ApplicationScoped
public class ExperimentResultToMarkdown implements BodyFormatter {
   public static final String NAME = "experimentResultToMarkdown";

   @ConfigProperty(name = "horreum.url")
   String publicUrl;

   @Location("issue_comment_from_experiment_result")
   Template template;

   @Override
   public String name() {
      return NAME;
   }

   @Override
   public String format(JsonNode config, Object payload) {
      if (!(payload instanceof ExperimentService.ExperimentResult)) {
         throw new IllegalArgumentException("This formatter accepts only ExperimentResults");
      }
      ExperimentService.ExperimentResult result = (ExperimentService.ExperimentResult) payload;
      return template.data("profile", result.profile)
            .data("dataset", result.datasetInfo)
            .data("baseline", result.baseline)
            .data("results", result.results)
            .data("publicUrl", publicUrl).render();
   }
}
