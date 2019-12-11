package io.hyperfoil.tools.repo.entity.converter;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

@Provider
public class JsonContext implements ContextResolver<Jsonb> {

   @Override
   public Jsonb getContext(Class type) {
      JsonbConfig config = new JsonbConfig();
      config.withSerializers(new JsonSerializer()).withDeserializers(new JsonSerializer());
      return JsonbBuilder.create(config);
   }
}
