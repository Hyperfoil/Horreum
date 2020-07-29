package io.hyperfoil.tools.horreum.notification;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.qute.Template;
import io.quarkus.qute.api.ResourcePath;

@ApplicationScoped
public class EmailPlugin implements NotificationPlugin {
   @ConfigProperty(name = "horreum.url")
   String baseUrl;

   @ConfigProperty(name = "horreum.mail.subject.prefix", defaultValue = "[Horreum]")
   String subjectPrefix;

   @ResourcePath("notification_email")
   Template emailContent;

   @Inject
   Mailer mailer;

   @Override
   public String method() {
      return "email";
   }

   @Override
   public void notify(String testName, String name, String data, Change change) {
      String subject = subjectPrefix + " Change in " + testName + "/" + change.criterion.variable.name;
      String content = emailContent
            .data("name", name)
            .data("testName", testName)
            .data("baseUrl", baseUrl)
            .data("testId", String.valueOf(change.criterion.variable.testId))
            .data("variable", change.criterion.variable.name)
            .data("runId", String.valueOf(change.dataPoint.runId))
            .render();
      mailer.send(Mail.withHtml(data, subject, content));
   }
}
