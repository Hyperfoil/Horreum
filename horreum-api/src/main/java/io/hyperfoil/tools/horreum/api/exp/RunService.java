package io.hyperfoil.tools.horreum.api.exp;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/x/run")
@Produces(MediaType.APPLICATION_JSON)
public interface RunService {
    @POST
    @Path("data")
    Response addRunFromData(
            @QueryParam("test") String test,
            String content
    );
}
