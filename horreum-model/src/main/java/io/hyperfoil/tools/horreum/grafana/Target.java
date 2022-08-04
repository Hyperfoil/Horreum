package io.hyperfoil.tools.horreum.grafana;

public class Target {
   public String target;
   public String type;
   public String refId;
   public String data = "";
   public String payload = "";

   public Target() {
   }

   public Target(String target, String type, String refId) {
      this.target = target;
      this.type = type;
      this.refId = refId;
   }
}
