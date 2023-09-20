package io.hyperfoil.tools.horreum.api.services;

import io.hyperfoil.tools.horreum.api.data.View;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

@Path("/api/ui")
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public interface UIService {

    @POST
    @Path("{testId}/view")
    int updateView(@PathParam("testId") int testId, @RequestBody(required = true) View view);

    @DELETE
    @Path("{testId}/view/{viewId}")
    void deleteView(@PathParam("testId") int testId, @PathParam("viewId") int viewId);

}
