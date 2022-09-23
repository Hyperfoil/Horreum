package io.hyperfoil.tools.horreum.action;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;

@ApplicationScoped
public class ChangeToMarkdown implements BodyFormatter {
   @Location("new_issue_from_change")
   Template template;

   @ConfigProperty(name = "horreum.url")
   String publicUrl;

   @Override
   public String name() {
      return "changeToMarkdown";
   }

   @Override
   public String format(JsonNode config, Object payload) {
      if (!(payload instanceof Change.Event)) {
         throw new IllegalArgumentException("This formatter accepts only Change.Event!");
      }
      Change.Event event = (Change.Event) payload;
      Change change = event.change;
      return template
            .data("testName", event.testName)
            .data("testNameEncoded", URLEncoder.encode(event.testName, StandardCharsets.UTF_8))
            .data("fingerprint", URLEncoder.encode(change.dataset.getFingerprint(), StandardCharsets.UTF_8))
            .data("publicUrl", publicUrl)
            .data("testId", String.valueOf(change.variable.testId))
            .data("variable", change.variable.name)
            .data("group", change.variable.group)
            .data("runId", event.dataset.runId)
            .data("datasetOrdinal", event.dataset.ordinal)
            // TODO: remove me after Quarkus update >= 2.12
            .data("datasetOrdinalPlusOne", event.dataset.ordinal + 1)
            .data("description", change.description)
            .render();
   }
}
