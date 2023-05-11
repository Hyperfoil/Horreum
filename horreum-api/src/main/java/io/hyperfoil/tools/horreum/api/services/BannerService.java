package io.hyperfoil.tools.horreum.api.services;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.api.data.Banner;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

@Path("/api/banner")
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public interface BannerService {
   @POST
   void set(@RequestBody(required = true) Banner banner);

   @GET
   Banner get();

}
