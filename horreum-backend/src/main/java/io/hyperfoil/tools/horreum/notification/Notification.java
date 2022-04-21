package io.hyperfoil.tools.horreum.notification;

import java.time.Instant;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.svc.MissingValuesEvent;

public abstract class Notification {
   protected final String username;
   protected final String data;

   protected Notification(String username, String data) {
      this.username = username;
      this.data = data;
   }

   public abstract void notifyChange(String testName, String fingerprint, Change.Event change);
   public abstract void notifyMissingDataset(String testName, int testId, String ruleName, long maxStaleness, Instant lastTimestamp);
   public abstract void notifyMissingValues(String testName, String fingerprint, MissingValuesEvent missing);
   public abstract void notifyExpectedRun(String testName, int testId, String tags, long before, String expectedBy, String backlink);
}
