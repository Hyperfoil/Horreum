package io.hyperfoil.tools.repo.server;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
   @Override
   public Response toResponse(ConstraintViolationException exception) {
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
