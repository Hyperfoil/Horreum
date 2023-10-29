package io.hyperfoil.tools.horreum.dev.services.runtime.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hyperfoil.tools.HorreumClient;
import io.hyperfoil.tools.horreum.api.alerting.NotificationSettings;
import io.hyperfoil.tools.horreum.api.alerting.Variable;
import io.hyperfoil.tools.horreum.api.alerting.Watch;
import io.hyperfoil.tools.horreum.api.data.Action;
import io.hyperfoil.tools.horreum.api.data.Label;
import io.hyperfoil.tools.horreum.api.data.Run;
import io.hyperfoil.tools.horreum.api.data.Schema;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.api.data.Transformer;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;

public class HorreumDevJsonRpcService {

    private static final Logger log = Logger.getLogger(HorreumDevJsonRpcService.class);

    @Inject
    ObjectMapper mapper;

    public HorreumDevInfo getInfo() {
        return HorreumDevController.get().getInfo();
    }

    public int getNumberOfTests() {
        return getInfo().getNumberOfTests();
    }

    public String getIsSampleDataLoaded() {
        return getInfo().getIsLoaded();
    }


    public String getSampleData() {

        log.info("Loading sample data");
        if ( getInfo().getIsLoaded().equals("true")) {
            log.warn("Loading sample data");
            return "Data has already been loaded";
        }

        StringBuilder sb = new StringBuilder();

        //TODO: build dynamically
        try(HorreumClient horreumClient = new HorreumClient.Builder().horreumUrl("http://localhost:8080").horreumUser("user").horreumPassword("secret").build();) {

            executeAction(sb, "schema hyperfoil_schema.json", () -> horreumClient.schemaService.add(mapFromResource("example-data/hyperfoil_schema.json", Schema.class)));
            executeAction(sb, "test protected_test.json", () -> horreumClient.testService.add(mapFromResource("example-data/protected_test.json", Test.class)));

            Integer ACME_BENCHMARK_SCHEMA_ID = executeAction(sb, "schema acme_benchmark_schema.json", () -> horreumClient.schemaService.add(mapFromResource("example-data/acme_benchmark_schema.json", Schema.class)));
            Integer ACME_HORREUM_SCHEMA_ID = executeAction(sb, "schema acme_horreum_schema.json", () -> horreumClient.schemaService.add(mapFromResource("example-data/acme_horreum_schema.json", Schema.class)));
            Integer ACME_TRANSFORMER_ID = executeAction(sb, "schema transfomer acme_transformer.json", () -> horreumClient.schemaService.addOrUpdateTransformer(ACME_BENCHMARK_SCHEMA_ID, mapFromResource("example-data/acme_transformer.json", Transformer.class)));
            Integer ROADRUNNER_TEST_ID = executeAction(sb, "test roadrunner_test.json", () -> horreumClient.testService.add(mapFromResource("example-data/roadrunner_test.json", Test.class)).id);

            executeAction(sb, "test updateTransformers: " + ACME_TRANSFORMER_ID, () -> {
                horreumClient.testService.updateTransformers(ROADRUNNER_TEST_ID, List.of(ACME_TRANSFORMER_ID));
                return (Void) null;
            });

            executeAction(sb, "run roadrunner_run.json", () -> horreumClient.runService.add(Integer.toString(ROADRUNNER_TEST_ID), null, null, null, mapFromResource("example-data/roadrunner_run.json", Run.class)));

            executeAction(sb, "schema labels test_label.json", () -> horreumClient.schemaService.addOrUpdateLabel(ACME_HORREUM_SCHEMA_ID, mapFromResource("example-data/test_label.json", Label.class)));
            executeAction(sb, "schema labels throughput_label.json", () -> horreumClient.schemaService.addOrUpdateLabel(ACME_HORREUM_SCHEMA_ID, mapFromResource("example-data/throughput_label.json", Label.class)));

            executeAction(sb, "schema alerting variables roadrunner_variables.json", () -> {
                horreumClient.alertingService.updateVariables(ROADRUNNER_TEST_ID, List.of(mapFromResource("example-data/roadrunner_variables.json", Variable[].class)));
                return (Void) null;
            });

            executeAction(sb, "subscriptions roadrunner_watch.json", () -> {
                horreumClient.subscriptionService.update(ROADRUNNER_TEST_ID, mapFromResource("example-data/roadrunner_watch.json", Watch.class));
                return (Void) null;
            });

            executeAction(sb, "notification settings user_notifications.json", () -> {
                horreumClient.notificationService.updateSettings("user", false, mapFromResource("example-data/user_notifications.json", NotificationSettings[].class));
                return (Void) null;
            });

            executeAction(sb, "Allowed site example.com", () -> horreumClient.actionService.addSite("http://example.com"));

            Action action = executeAction(sb, "Action new_test_action.json", () -> horreumClient.actionService.add(mapFromResource("example-data/new_test_action.json", Action.class)));

            Action newRunAction = mapFromResource("example-data/new_run_action.json", Action.class);
            newRunAction.testId = action.testId;

            executeAction(sb, "Action newRunAction", () -> horreumClient.actionService.add(newRunAction));

            String result = sb.toString();

            log.info(result);
            log.info("Loading sample data: DONE");

            return result;
        }
    }

    private <T> T executeAction(StringBuilder output, String msg, Supplier<T> function) {
        output.append("Loading ").append(msg).append("...");
        T result = null;
        try {
            result = function.get();
            output.append("Done\n");
        } catch (Exception e) {
            output.append("FAILED: ").append(e.getMessage()).append("\n");
        }
        return result;
    }

    private <T> T mapFromResource(String resource, Class<T> klass) {
        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream(resource)) {
            return mapper.treeToValue(mapper.readTree(is), klass);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
