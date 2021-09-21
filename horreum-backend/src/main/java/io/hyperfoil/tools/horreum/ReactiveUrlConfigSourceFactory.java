package io.hyperfoil.tools.horreum;

import java.util.Collections;
import java.util.Map;

import org.eclipse.microprofile.config.spi.ConfigSource;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.PropertiesConfigSource;

public class ReactiveUrlConfigSourceFactory implements ConfigSourceFactory {
   private static final String QUARKUS_DATASOURCE_JDBC_URL = "quarkus.datasource.jdbc.url";
   private static final String QUARKUS_DATASOURCE_REACTIVE_URL = "quarkus.datasource.reactive.url";

   @Override
   public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
      ConfigValue value = context.getValue(QUARKUS_DATASOURCE_JDBC_URL);
      if (value == null || value.getValue() == null) {
         return Collections.emptyList();
      }
      String url = value.getValue();
      if (url.startsWith("jdbc:")) {
         url = url.substring(5);
      }
      return Collections.singleton(new PropertiesConfigSource(Map.of(QUARKUS_DATASOURCE_REACTIVE_URL, url), "horreum", 275));
   }
}
