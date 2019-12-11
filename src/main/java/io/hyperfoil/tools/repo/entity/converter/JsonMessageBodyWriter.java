package io.hyperfoil.tools.repo.entity.converter;

import io.hyperfoil.tools.yaup.json.Json;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
@Produces({MediaType.APPLICATION_JSON})
public class JsonMessageBodyWriter implements MessageBodyWriter<Json> {
   @Override
   public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
      return type == Json.class;
   }

   @Override
   public void writeTo(Json json, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
      entityStream.write(json.toString(0).getBytes());
   }
}
