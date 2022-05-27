package io.hyperfoil.tools.horreum.notification;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.svc.MissingValuesEvent;
import io.hyperfoil.tools.horreum.svc.ServiceException;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;

@ApplicationScoped
public class EmailPlugin implements NotificationPlugin {
   @ConfigProperty(name = "horreum.url")
   String baseUrl;

   @ConfigProperty(name = "horreum.mail.subject.prefix", defaultValue = "[Horreum]")
   String subjectPrefix;

   @Location("change_notification_email")
   Template changeNotificationEmail;

   @Location("missing_dataset_notification_email")
   Template missingDatasetNotificationEmail;

   @Location("missing_values_notification_email")
   Template missingValuesNotificationEmail;

   @Location("expected_run_notification_email")
   Template expectedRunNotificationEmail;

   @Inject
   Mailer mailer;

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
         throw ServiceException.badRequest("Mail notifications require an email as a data parameter: '" + data + "' is not a valid email.");
      }
      mailer.send(Mail.withText(data, "Test message", "This is a test message from Horreum. Please ignore."));
   }

   public class EmailNotification extends Notification {
      protected EmailNotification(String username, String data) {
         super(username, data);
      }

      @Override
      public void notifyChange(String testName, String fingerprint, Change.Event event) {
         String subject = subjectPrefix + " Change in " + testName + "/" + event.change.variable.name;
         String content = changeNotificationEmail
               .data("username", username)
               .data("testName", testName)
               .data("fingerprint", fingerprint)
               .data("baseUrl", baseUrl)
               .data("testId", String.valueOf(event.change.variable.testId))
               .data("variable", event.change.variable.name)
               .data("group", event.change.variable.group)
               .data("runId", event.dataset.runId)
               .data("datasetOrdinal", event.dataset.ordinal)
               .render();
         mailer.send(Mail.withHtml(data, subject, content));
      }

      @Override
      public void notifyMissingDataset(String testName, int testId, String ruleName, long maxStaleness, Instant lastTimestamp) {
         String subject = subjectPrefix + " Missing expected data for " + testName + "/" + ruleName;
         String content = missingDatasetNotificationEmail
               .data("username", username)
               .data("testName", testName)
               .data("testId", String.valueOf(testId))
               .data("ruleName", ruleName)
               .data("baseUrl", baseUrl)
               .data("maxStaleness", prettyPrintTime(maxStaleness))
               .data("currentStaleness", lastTimestamp == null ? "yet" : "in " + prettyPrintTime(System.currentTimeMillis() - lastTimestamp.toEpochMilli()))
               .data("lastTimestamp", lastTimestamp == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date.from(lastTimestamp)))
               .render();
         mailer.send(Mail.withHtml(data, subject, content));
      }

      @Override
      public void notifyMissingValues(String testName, String fingerprint, MissingValuesEvent event) {
         String subject = subjectPrefix + " Missing change detection values for " + testName + "/" + fingerprint + ", run " + event.datasetId;
         String content = missingValuesNotificationEmail
               .data("username", username)
               .data("testName", testName)
               .data("testId", String.valueOf(event.testId))
               .data("fingerprint", fingerprint)
               .data("baseUrl", baseUrl)
               .data("runId", event.runId)
               .data("datasetOrdinal", event.datasetOrdinal)
               .data("variables", String.join(", ", event.variables))
               .render();
         mailer.send(Mail.withHtml(data, subject, content));
      }

      @Override
      public void notifyExpectedRun(String testName, int testId, long before, String expectedBy, String backlink) {
         String subject = subjectPrefix + " Missing expected run for " + testName;
         String content = expectedRunNotificationEmail
               .data("username", username)
               .data("testName", testName)
               .data("testId", String.valueOf(testId))
               .data("baseUrl", baseUrl)
               .data("before", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(before)))
               .data("expectedBy", expectedBy)
               .data("backlink", backlink)
               .render();
         mailer.send(Mail.withHtml(data, subject, content));
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
