package io.hyperfoil.tools.horreum.notification;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.svc.MissingRunValuesEvent;
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

   @Location("missing_run_notification_email")
   Template missingRunNotificationEmail;

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

   public class EmailNotification extends Notification {
      protected EmailNotification(String username, String data) {
         super(username, data);
      }

      @Override
      public void notifyChange(String testName, String tags, Change change) {
         String subject = subjectPrefix + " Change in " + testName + "/" + change.variable.name;
         String content = changeNotificationEmail
               .data("username", username)
               .data("testName", testName)
               .data("tags", tags)
               .data("baseUrl", baseUrl)
               .data("testId", String.valueOf(change.variable.testId))
               .data("variable", change.variable.name)
               .data("runId", String.valueOf(change.run.id))
               .data("group", change.variable.group)
               .render();
         mailer.send(Mail.withHtml(data, subject, content));
      }

      @Override
      public void notifyMissingRun(String testName, int testId, String tags,
                                   long maxStaleness, int lastRunId, long lastRunTimestamp) {
         String subject = subjectPrefix + " Missing expected run for " + testName + "/" + tags;
         String content = missingRunNotificationEmail
               .data("username", username)
               .data("testName", testName)
               .data("testId", String.valueOf(testId))
               .data("tags", tags)
               .data("baseUrl", baseUrl)
               .data("maxStaleness", prettyPrintTime(maxStaleness))
               .data("currentStaleness", prettyPrintTime(System.currentTimeMillis() - lastRunTimestamp))
               .data("lastRunId", String.valueOf(lastRunId))
               .data("lastRunTimestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(lastRunTimestamp)))
               .render();
         mailer.send(Mail.withHtml(data, subject, content));
      }

      @Override
      public void notifyMissingRunValues(String testName, String tags, MissingRunValuesEvent event) {
         String subject = subjectPrefix + " Missing change detection values for " + testName + "/" + tags + ", run " + event.runId;
         String content = missingValuesNotificationEmail
               .data("username", username)
               .data("testName", testName)
               .data("testId", String.valueOf(event.testId))
               .data("tags", tags)
               .data("baseUrl", baseUrl)
               .data("runId", event.runId)
               .data("variables", String.join(", ", event.variables))
               .render();
         mailer.send(Mail.withHtml(data, subject, content));
      }

      @Override
      public void notifyExpectedRun(String testName, int testId, String tags, long before, String expectedBy, String backlink) {
         String subject = subjectPrefix + " Missing expected run for " + testName;
         if (tags != null) {
            subject += "/" + tags;
         }
         String content = expectedRunNotificationEmail
               .data("username", username)
               .data("testName", testName)
               .data("testId", String.valueOf(testId))
               .data("tags", tags)
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
