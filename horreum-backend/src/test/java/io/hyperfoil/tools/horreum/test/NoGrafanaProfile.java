package io.hyperfoil.tools.horreum.test;

import java.util.HashMap;
import java.util.Map;

public class NoGrafanaProfile extends HorreumTestProfile {
   @Override
   public Map<String, String> getConfigOverrides() {
      Map<String, String> map = new HashMap<>(super.getConfigOverrides());
      map.put("horreum.grafana.url", "");
      map.put("horreum.grafana/mp-rest/url", "http://grafana-disabled-in-tests.io");
      return map;
   }
}
