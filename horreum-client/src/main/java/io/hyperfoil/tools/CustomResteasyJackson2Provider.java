package io.hyperfoil.tools;

import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class CustomResteasyJackson2Provider extends ResteasyJackson2Provider {
    public CustomResteasyJackson2Provider() {
        ObjectMapper customJsonMapper = new ObjectMapper();
        // This is useful if the client is old and we have added some new properties
        customJsonMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        customJsonMapper.registerModule(new JavaTimeModule());
        this.setMapper(customJsonMapper);
    }
}
