package io.hyperfoil.tools.horreum.entity;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@MappedSuperclass
public abstract class PersistentLog extends PanacheEntityBase {
   public static final int DEBUG = 0;
   public static final int INFO = 1;
   public static final int WARN = 2;
   public static final int ERROR = 3;

   @JsonProperty(required = true)
   @Id
   @GeneratedValue
   public Long id;

   @NotNull
   public int level;

   @Schema(required = true)
   @NotNull
   @Column(columnDefinition = "timestamp")
   public Instant timestamp;

   @NotNull
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
