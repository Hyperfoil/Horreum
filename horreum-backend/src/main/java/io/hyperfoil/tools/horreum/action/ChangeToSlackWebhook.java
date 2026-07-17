package io.hyperfoil.tools.horreum.action;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.alerting.Change;
import io.hyperfoil.tools.horreum.entity.data.DatasetDAO;
import io.hyperfoil.tools.horreum.svc.Util;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;

@ApplicationScoped
public class ChangeToSlackWebhook implements BodyFormatter {
    @Location("new_issue_from_change")
    Template template;

    @ConfigProperty(name = "horreum.url")
    String publicUrl;

    @Override
    public String name() {
        return "changeToSlackWebhook";
    }

    @Override
    public String format(JsonNode config, Object payload) {
        if (!(payload instanceof Change.Event)) {
            throw new IllegalArgumentException("This formatter accepts only Change.Event!");
        }
        Change.Event event = (Change.Event) payload;
        Change change = event.change;
        String fingerprint = DatasetDAO.getEntityManager().getReference(DatasetDAO.class, change.dataset.id).getFingerprint();
        String text = template
                .data("testName", event.testName)
                .data("testNameEncoded", URLEncoder.encode(event.testName, StandardCharsets.UTF_8))
                .data("fingerprint", URLEncoder.encode(fingerprint, StandardCharsets.UTF_8))
                .data("publicUrl", publicUrl)
                .data("testId", String.valueOf(change.variable.testId))
                .data("variable", change.variable.name)
                .data("group", change.variable.group)
                .data("runId", event.change.dataset.runId)
                .data("datasetOrdinal", event.change.dataset.ordinal)
                .data("description", change.description)
                .render();
        ObjectNode body = Util.OBJECT_MAPPER.createObjectNode();
        body.put("text", text);
        return body.toString();
    }
}
