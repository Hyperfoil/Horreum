package io.hyperfoil.tools.horreum.server;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;

@ApplicationScoped
public class ErrorReporter {
   private static Logger log = Logger.getLogger(ErrorReporter.class);

   @ConfigProperty(name = "horreum.admin.mail")
   Optional<String> adminMail;

   @ConfigProperty(name = "horreum.mail.subject.prefix", defaultValue = "[Horreum]")
   String subjectPrefix;

   @Inject
   Mailer mailer;

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
         mailer.send(Mail.withText(adminMail.get(), subjectPrefix + subject, bos.toString(StandardCharsets.UTF_8)));
      }
   }
}
