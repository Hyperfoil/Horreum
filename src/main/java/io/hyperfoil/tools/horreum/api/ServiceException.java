package io.hyperfoil.tools.horreum.api;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public class ServiceException extends WebApplicationException {
   public static ServiceException badRequest(String message) {
      return new ServiceException(Response.Status.BAD_REQUEST, message);
   }

   public ServiceException(int status, String message) {
      super(message, Response.status(status)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN).entity(message).build());
   }

   public ServiceException(Response.Status status, String message) {
      super(message, Response.status(status)
           .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN).entity(message).build());
   }
}
