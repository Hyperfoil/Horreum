package io.hyperfoil.tools.horreum.entity.converter;

import io.hyperfoil.tools.yaup.json.Json;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.stream.Collectors;

@Provider
@Produces({MediaType.APPLICATION_JSON})
@Consumes({MediaType.APPLICATION_JSON})
public class JsonProvider implements MessageBodyWriter<Json>, MessageBodyReader<Json>{

   @Override
   public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
      return type == Json.class;
   }

   @Override
   public void writeTo(Json json, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
      entityStream.write(json.toString().getBytes());
   }

   @Override
   public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
      return type == Json.class;
   }

   @Override
   public Json readFrom(Class<Json> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
      String result = new BufferedReader(new InputStreamReader(entityStream))
         .lines().collect(Collectors.joining("\n"));
      return Json.fromString(result);
   }

}
