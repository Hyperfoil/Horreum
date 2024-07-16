package io.hyperfoil.tools.horreum.api.exp;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.jboss.resteasy.reactive.Separator;

import java.util.List;

import io.hyperfoil.tools.horreum.api.exp.data.Test;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/api/x/test")
@Produces(APPLICATION_JSON)
public interface TestService {
    @POST
    void create(Test t);

    @GET
    @Path("{id}")
    Test getById(@PathParam("id") int testId);

    @GET
    @Path("rhivos")
    Test createRhivos();

    @GET
    @Path("{id}/labelValues")
    List<LabelService.ValueMap> labelValues(
            @PathParam("id") int testId,
            @QueryParam("group") String group,
            @QueryParam("filter") @DefaultValue("{}") String filter,
            @QueryParam("before") @DefaultValue("") String before,
            @QueryParam("after") @DefaultValue("") String after,
            @QueryParam("filtering") @DefaultValue("true") boolean filtering,
            @QueryParam("metrics") @DefaultValue("true") boolean metrics,
            @QueryParam("sort") @DefaultValue("") String sort,
            @QueryParam("direction") @DefaultValue("Ascending") String direction,
            @QueryParam("limit") @DefaultValue("" + Integer.MAX_VALUE) int limit,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("include") @Separator(",") List<String> include,
            @QueryParam("exclude") @Separator(",") List<String> exclude,
            @QueryParam("multiFilter") @DefaultValue("false") boolean multiFilter);
}
