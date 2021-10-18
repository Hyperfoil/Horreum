package io.hyperfoil.tools.horreum.notification;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.svc.MissingRunValuesEvent;

public abstract class Notification {
   protected final String username;
   protected final String data;

   protected Notification(String username, String data) {
      this.username = username;
      this.data = data;
   }

   public abstract void notifyChange(String testName, String tags, Change change);
   public abstract void notifyMissingRun(String testName, int testId, String tags, long maxStaleness, int lastRunId, long lastRunTimestamp);
   public abstract void notifyMissingRunValues(String testName, String tags, MissingRunValuesEvent missing);
   public abstract void notifyExpectedRun(String testName, int testId, String tags, long before, String expectedBy, String backlink);
}
