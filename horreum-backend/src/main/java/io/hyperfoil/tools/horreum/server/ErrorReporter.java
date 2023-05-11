package io.hyperfoil.tools.horreum.server;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;

@ApplicationScoped
public class ErrorReporter {
   private static final Logger log = Logger.getLogger(ErrorReporter.class);

   @ConfigProperty(name = "horreum.admin.mail")
   Optional<String> adminMail;

   @ConfigProperty(name = "horreum.mail.subject.prefix", defaultValue = "[Horreum]")
   String subjectPrefix;

   @ConfigProperty(name = "horreum.mail.timeout", defaultValue = "15s")
   Duration sendMailTimeout;

   @Inject
   ReactiveMailer mailer;

   public void reportException(Throwable t, String subject, String format, Object... params) {
      String message = String.format(format, params);
      log.error(message, t);
      if (adminMail.isPresent()) {
         ByteArrayOutputStream bos = new ByteArrayOutputStream();
         PrintWriter writer = new PrintWriter(bos);
         writer.write(message);
         writer.write(t.toString());
         writer.write("\n");
         t.printStackTrace(writer);
         writer.flush();
         try {
            mailer.send(Mail.withText(adminMail.get(), subjectPrefix + subject, bos.toString(StandardCharsets.UTF_8)))
                  .await().atMost(sendMailTimeout);
         } catch (Throwable t2) {
            log.error("Cannot send notification to admin!", t2);
         }
      }
   }
}
