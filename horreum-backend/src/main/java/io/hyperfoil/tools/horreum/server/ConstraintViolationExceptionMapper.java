package io.hyperfoil.tools.horreum.server;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import io.quarkus.logging.Log;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        Log.error("Mapping exception to response", exception);
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
