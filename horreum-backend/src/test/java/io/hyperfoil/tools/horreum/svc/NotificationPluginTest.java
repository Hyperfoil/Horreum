package io.hyperfoil.tools.horreum.svc;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.hyperfoil.tools.horreum.api.alerting.Change;
import io.hyperfoil.tools.horreum.api.alerting.Variable;
import io.hyperfoil.tools.horreum.api.data.Dataset;
import io.hyperfoil.tools.horreum.entity.data.DatasetDAO;
import io.hyperfoil.tools.horreum.events.DatasetChanges;
import io.hyperfoil.tools.horreum.notification.Notification;
import io.hyperfoil.tools.horreum.notification.NotificationPlugin;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;

@QuarkusTest
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class NotificationPluginTest {
    @Inject
    Instance<NotificationPlugin> plugins;

    @Test
    public void testDatasetChanges() {
        DatasetChanges dc1 = new DatasetChanges(new Dataset.Info(1, 1, 0, 1), "{\"runtime\": \"native\"}", "Dummy Test", true);
        Change c1 = new Change();
        c1.timestamp = Instant.now();
        c1.description = "foobar";
        c1.variable = new Variable();
        c1.variable.name = "some var";
        Change c2 = new Change();
        c2.timestamp = Instant.now();
        c2.variable = new Variable();
        c2.variable.group = "some group";
        c2.variable.name = "another var";

        dc1.addChange(new Change.Event(c1, 1, dc1.testName, dc1.dataset, true));
        dc1.addChange(new Change.Event(c2, 1, dc1.testName, dc1.dataset, true));
        withAllPlugins(notification -> notification.notifyChanges(dc1));
    }

    @Test
    public void testExpectedRun() {
        withAllPlugins(notification -> notification.notifyExpectedRun("Dummy Test", 1, System.currentTimeMillis(), "Jenkins",
                "http://jenkins.example.com"));
    }

    @Test
    public void testMissingDataset() {
        withAllPlugins(notification -> notification.notifyMissingDataset("Dummy Test", 1, "My rule", TimeUnit.DAYS.toMillis(1),
                Instant.now()));
    }

    @Test
    public void testApiKeyExpiration() {
        withAllPlugins(notification -> notification.notifyApiKeyExpiration("Dummy key", Instant.now(), Instant.now(), 1, 1));
    }

    @Test
    public void test() {
        var event = new MissingValuesEvent(new DatasetDAO.Info(1, 1, 0, 1), new HashSet<>(Arrays.asList("foo", "bar")), true);
        withAllPlugins(notification -> notification.notifyMissingValues("Dummy Test", null, event));
    }

    private void withAllPlugins(Consumer<Notification> consumer) {
        plugins.stream().forEach(p -> consumer.accept(p.create("dummy", "dummy@example.com")));
    }
}
