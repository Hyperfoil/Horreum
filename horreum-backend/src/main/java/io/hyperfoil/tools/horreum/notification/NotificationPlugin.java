package io.hyperfoil.tools.horreum.notification;

public interface NotificationPlugin {
   String method();
   Notification create(String username, String data);

   void test(String data);
}
