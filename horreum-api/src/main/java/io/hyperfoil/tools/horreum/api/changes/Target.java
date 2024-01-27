package io.hyperfoil.tools.horreum.api.changes;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class Target {
   public String target;
   public String type;
   public String refId;
   public String data = "";
   public ObjectNode payload = null;

   public Target() {
   }

   public Target(String target, String type, String refId) {
      this.target = target;
      this.type = type;
      this.refId = refId;
   }
}
