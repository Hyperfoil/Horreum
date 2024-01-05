package io.hyperfoil.tools.horreum.api.data;

import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@Schema( type = SchemaType.OBJECT, description = "Persistent Log", name = "PersistentLog")
public abstract class PersistentLog {
   public static final int DEBUG = 0;
   public static final int INFO = 1;
   public static final int WARN = 2;
   public static final int ERROR = 3;

   @JsonProperty(required = true)
   public Long id;

   @NotNull
   @JsonProperty(required = true)
   public int level;

   @NotNull
   @JsonProperty(required = true)
   public Instant timestamp;

   @NotNull
   @JsonProperty(required = true)
   public String message;

   public PersistentLog(int level, String message) {
      this.level = level;
      this.message = message;
      this.timestamp = Instant.now();
   }

   public static Logger.Level logLevel(int level) {
      switch (level) {
         case DEBUG:
            return Logger.Level.DEBUG;
         case INFO:
            return Logger.Level.INFO;
         case WARN:
            return Logger.Level.WARN;
         case ERROR:
         default:
            return Logger.Level.ERROR;
      }
   }
}
