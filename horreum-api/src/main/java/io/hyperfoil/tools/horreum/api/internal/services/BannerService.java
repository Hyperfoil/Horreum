package io.hyperfoil.tools.horreum.api.internal.services;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.hyperfoil.tools.horreum.api.data.Banner;

@Path("/api/banner")
@Consumes({ MediaType.APPLICATION_JSON })
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "banner", description = "Manage banner")
public interface BannerService {
    @POST
    void setBanner(@RequestBody(required = true) Banner banner);

    @GET
    Banner getBanner();

}
