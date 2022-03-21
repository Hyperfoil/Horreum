package io.hyperfoil.tools.horreum.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.entity.json.ConfiguredJsonPath;
import io.hyperfoil.tools.horreum.entity.json.NamedJsonPath;

@Path("/api/label")
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public interface LabelService {

   void validate(NamedJsonPath paths);

   String asValue(ConfiguredJsonPath paths);
}
