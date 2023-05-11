package io.hyperfoil.tools.horreum.server;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.svc.SchemaServiceImpl;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
   private static final Logger log = Logger.getLogger(ConstraintViolationExceptionMapper.class);

   @Override
   public Response toResponse(ConstraintViolationException exception) {
      log.error("Mapping exception to response", exception);
      JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
      for (ConstraintViolation<?> cv : exception.getConstraintViolations()) {
         arrayBuilder.add(Json.createObjectBuilder()
               .add("message", cv.getMessage())
               .add("class", cv.getRootBeanClass().getName())
               .add("path", cv.getPropertyPath().toString())
               .build());
      }
      return Response.status(Response.Status.BAD_REQUEST).entity(Json.createObjectBuilder()
            .add("error", exception.getClass().getName())
            .add("violations", arrayBuilder).build()).build();
   }
}
