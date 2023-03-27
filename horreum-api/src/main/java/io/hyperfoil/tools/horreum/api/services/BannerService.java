package io.hyperfoil.tools.horreum.api.services;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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
