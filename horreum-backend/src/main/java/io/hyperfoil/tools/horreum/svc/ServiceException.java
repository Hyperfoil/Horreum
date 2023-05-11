package io.hyperfoil.tools.horreum.svc;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

public class ServiceException extends WebApplicationException {
   public static ServiceException badRequest(String message) {
      return new ServiceException(Response.Status.BAD_REQUEST, message);
   }

   public static ServiceException forbidden(String message) {
      return new ServiceException(Response.Status.FORBIDDEN, message);
   }

   public static ServiceException notFound(String message) {
      return new ServiceException(Response.Status.NOT_FOUND, message);
   }

   public static ServiceException serverError(String message) {
      return new ServiceException(Response.Status.INTERNAL_SERVER_ERROR, message);
   }

   public ServiceException(Response.Status status, String message) {
      super(message, Response.status(status)
           .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN).entity(message).build());
   }
}
