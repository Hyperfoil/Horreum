package io.hyperfoil.tools.horreum.notification;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.hyperfoil.tools.horreum.events.DatasetChanges;
import io.hyperfoil.tools.horreum.svc.MissingValuesEvent;
import io.hyperfoil.tools.horreum.svc.ServiceException;
import io.quarkus.logging.Log;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;

@ApplicationScoped
public class EmailPlugin implements NotificationPlugin {
    @ConfigProperty(name = "horreum.url")
    String baseUrl;

    @ConfigProperty(name = "horreum.mail.subject.prefix", defaultValue = "[Horreum]")
    String subjectPrefix;

    @ConfigProperty(name = "horreum.mail.timeout", defaultValue = "15s")
    Duration sendMailTimeout;

    @Location("change_notification_email")
    Template changeNotificationEmail;

    @Location("missing_dataset_notification_email")
    Template missingDatasetNotificationEmail;

    @Location("missing_values_notification_email")
    Template missingValuesNotificationEmail;

    @Location("expected_run_notification_email")
    Template expectedRunNotificationEmail;

    @Location("api_key_expiration_email")
    Template apiKeyExpirationEmail;

    @Inject
    ReactiveMailer mailer;

    private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public String method() {
        return "email";
    }

    @Override
    public Notification create(String username, String data) {
        return new EmailNotification(username, data);
    }

    @Override
    public void test(String data) {
        if (data == null || data.isBlank() || !data.contains("@")) {
            throw ServiceException.badRequest(
                    "Mail notifications require an email as a data parameter: '" + data + "' is not a valid email.");
        }
        mailer.send(Mail.withText(data, "Test message", "This is a test message from Horreum. Please ignore.")).await()
                .atMost(sendMailTimeout);
    }

    public class EmailNotification extends Notification {
        protected EmailNotification(String username, String data) {
            super(username, data);
        }

        @Override
        public void notifyChanges(DatasetChanges event) {
            String subject = subjectPrefix + " Change in " + event.testName;
            changeNotificationEmail
                    .data("username", username)
                    .data("testName", event.testName)
                    .data("fingerprint", URLEncoder.encode(event.fingerprint != null ? event.fingerprint : "", UTF_8))
                    .data("baseUrl", baseUrl)
                    .data("testId", String.valueOf(event.dataset.testId))
                    .data("runId", event.dataset.runId)
                    .data("datasetOrdinal", event.dataset.ordinal)
                    .data("changes", event.changes())
                    .createUni()
                    // when the content is ready, TRANSFORM it into the next async action
                    .onItem().transformToUni(content -> {
                        Log.debugf("Sending mail: %s", content);
                        // return the Uni from the mailer. DO NOT block or await!
                        return mailer.send(Mail.withHtml(data, subject, content));
                    })
                    .ifNoItem().after(sendMailTimeout).fail()
                    // add proper error handling to avoid "dropped" exceptions
                    // e.g., Mutiny had to drop the following exception: io.smallrye.mutiny.TimeoutException
                    .onFailure().invoke(e -> {
                        Log.errorf(e, "Failed to send notification email for test %s and run %s", event.testName,
                                event.dataset.runId);
                    })
                    .subscribe().with(item -> Log.debug("Successfully sent notification email."),
                            failure -> {
                                /* this is now handled by onFailure() above */ });
        }

        @Override
        public void notifyMissingDataset(String testName, int testId, String ruleName, long maxStaleness,
                Instant lastTimestamp) {
            String subject = "%s Missing expected data for %s/%s".formatted(subjectPrefix, testName, ruleName);
            missingDatasetNotificationEmail
                    .data("username", username)
                    .data("testName", testName)
                    .data("testId", String.valueOf(testId))
                    .data("ruleName", ruleName)
                    .data("baseUrl", baseUrl)
                    .data("maxStaleness", prettyPrintTime(maxStaleness))
                    .data("currentStaleness",
                            lastTimestamp == null ? "yet"
                                    : "in " + prettyPrintTime(System.currentTimeMillis() - lastTimestamp.toEpochMilli()))
                    .data("lastTimestamp", lastTimestamp == null ? null : dateFormat.format(Date.from(lastTimestamp)))
                    .createUni().subscribe().with(content -> {
                        mailer.send(Mail.withHtml(data, subject, content)).await().atMost(sendMailTimeout);
                        Log.debugf("Sending mail %s", content);
                    });
        }

        @Override
        public void notifyMissingValues(String testName, String fingerprint, MissingValuesEvent event) {
            String subject = "%s Missing change detection values in test %s, dataset %d#%d".formatted(
                    subjectPrefix, testName, event.dataset.runId, event.dataset.ordinal);
            missingValuesNotificationEmail
                    .data("username", username)
                    .data("testName", testName)
                    .data("testId", String.valueOf(event.dataset.testId))
                    .data("fingerprint", fingerprint)
                    .data("baseUrl", baseUrl)
                    .data("runId", event.dataset.runId)
                    .data("datasetOrdinal", event.dataset.ordinal)
                    .data("variables", event.variables)
                    .createUni().subscribe().with(content -> {
                        mailer.send(Mail.withHtml(data, subject, content)).await().atMost(sendMailTimeout);
                        Log.debugf("Sending mail: %s", content);
                    });
        }

        @Override
        public void notifyExpectedRun(String testName, int testId, long before, String expectedBy, String backlink) {
            String subject = subjectPrefix + " Missing expected run for " + testName;
            expectedRunNotificationEmail
                    .data("username", username)
                    .data("testName", testName)
                    .data("testId", String.valueOf(testId))
                    .data("baseUrl", baseUrl)
                    .data("before", dateFormat.format(new Date(before)))
                    .data("expectedBy", expectedBy)
                    .data("backlink", backlink)
                    .createUni().subscribe().with(content -> {
                        mailer.send(Mail.withHtml(data, subject, content)).await().atMost(sendMailTimeout);
                        Log.debugf("Sending mail: %s", content);
                    });
        }

        @Override
        public void notifyApiKeyExpiration(String keyName, Instant creation, Instant lastAccess, long toExpiration,
                long active) {
            String subject = "%s API key '%s' %s".formatted(subjectPrefix, keyName,
                    toExpiration == -1 ? "EXPIRED" : "about to expire");
            String content = apiKeyExpirationEmail
                    .data("baseUrl", baseUrl)
                    .data("username", username)
                    .data("keyName", keyName)
                    .data("creation", creation.atOffset(ZoneOffset.UTC).toLocalDateTime())
                    .data("lastAccess", lastAccess.atOffset(ZoneOffset.UTC).toLocalDateTime())
                    .data("expiration", toExpiration)
                    .data("active", active)
                    .render();
            mailer.send(Mail.withHtml(data, subject, content)).await().atMost(sendMailTimeout);
            Log.debugf("Sending mail: %s", content);
        }
    }

    private String prettyPrintTime(long duration) {
        StringBuilder sb = new StringBuilder();
        if (duration >= 86_400_000) {
            long days = duration / 86_400_000;
            sb.append(days).append(" day").append(days > 1 ? "s " : " ");
            duration -= days * 86_400_000;
        }
        if (duration >= 3_600_000) {
            long hours = duration / 3_600_000;
            sb.append(hours).append(" hour").append(hours > 1 ? "s " : " ");
            duration -= hours * 3_600_000;
        }
        if (duration >= 60_000) {
            long minutes = duration / 60_000;
            sb.append(minutes).append(" minute").append(minutes > 1 ? "s " : " ");
            duration -= minutes * 60_000;
        }
        if (duration > 0) {
            sb.append(duration / 1000).append(" second").append(duration >= 2000 ? "s" : "");
            // ignore ms
        }
        return sb.toString().trim();
    }
}
