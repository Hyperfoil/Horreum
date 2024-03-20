package io.hyperfoil.tools.horreum.action;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.enterprise.context.ApplicationScoped;

import io.hyperfoil.tools.horreum.api.data.Test;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;

@ApplicationScoped
public class TestToSlackMarkdown implements BodyFormatter {
   @Location("slack_from_test")
   Template template;

   @ConfigProperty(name = "horreum.url")
   String publicUrl;

   @Override
   public String name() {
      return "testToSlack";
   }

   @Override
   public String format(JsonNode config, Object payload) {
      if (!(payload instanceof Test)) {
         throw new IllegalArgumentException("This formatter accepts only Test payload!");
      }
      Test test = (Test) payload;
      return template
            .data("testName", test.name)
            .data("testNameEncoded", URLEncoder.encode(test.name, StandardCharsets.UTF_8))
            .data("publicUrl", publicUrl)
            .data("testId", String.valueOf(test.id))
            .data("description", test.description)
            .data("owner", test.owner)
            .data("datastoreId", test.datastoreId)
            .render();
   }
}
