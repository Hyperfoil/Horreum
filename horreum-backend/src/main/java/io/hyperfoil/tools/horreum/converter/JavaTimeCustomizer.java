package io.hyperfoil.tools.horreum.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.quarkus.jackson.ObjectMapperCustomizer;
import javax.inject.Singleton;

@Singleton
public class JavaTimeCustomizer implements ObjectMapperCustomizer {
   public void customize(ObjectMapper mapper) {
      mapper.registerModule(new JavaTimeModule());
   }
}
