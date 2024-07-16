package io.hyperfoil.tools.horreum.api.exp;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import io.hyperfoil.tools.horreum.api.exp.data.Label;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/api/x/labels")
@Produces(APPLICATION_JSON)
@Tag(name = "exp_label", description = "Experimental label service.")
public interface LabelService {
    @GET
    Label get(long id);

    record ValueMap(ObjectNode data, long index, long labelId, long runId, long testId){};
}
