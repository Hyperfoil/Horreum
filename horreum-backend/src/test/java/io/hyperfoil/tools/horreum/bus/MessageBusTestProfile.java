package io.hyperfoil.tools.horreum.bus;

import java.util.HashMap;
import java.util.Map;

import io.hyperfoil.tools.horreum.test.NoGrafanaProfile;

public class MessageBusTestProfile extends NoGrafanaProfile {
   @Override
   public Map<String, String> getConfigOverrides() {
      Map<String, String> map = new HashMap<>(super.getConfigOverrides());
      map.put("horreum.messagebus.retry.after", "1s");
      return map;
   }
}
