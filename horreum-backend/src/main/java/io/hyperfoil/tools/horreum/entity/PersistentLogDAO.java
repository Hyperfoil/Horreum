package io.hyperfoil.tools.horreum.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;

import org.jboss.logging.Logger;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@MappedSuperclass
public abstract class PersistentLogDAO extends PanacheEntityBase {
   public static final int DEBUG = 0;
   public static final int INFO = 1;
   public static final int WARN = 2;
   public static final int ERROR = 3;

   @NotNull
   public int level;

   @NotNull
   @Column(columnDefinition = "timestamp")
   public Instant timestamp;

   @NotNull
   public String message;

   public PersistentLogDAO(int level, String message) {
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
