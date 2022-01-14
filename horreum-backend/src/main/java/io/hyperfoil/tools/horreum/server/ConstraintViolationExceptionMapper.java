package io.hyperfoil.tools.horreum.server;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

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
