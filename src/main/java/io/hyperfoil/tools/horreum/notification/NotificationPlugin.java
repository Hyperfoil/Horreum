package io.hyperfoil.tools.horreum.notification;

import io.hyperfoil.tools.horreum.entity.alerting.Change;

public interface NotificationPlugin {
   String method();
   void notify(String testName, String tags, String name, String data, Change change);
}
