package io.hyperfoil.tools.horreum.exp.svc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hyperfoil.tools.horreum.api.exp.RunService;
import io.hyperfoil.tools.horreum.exp.data.RunDao;
import io.hyperfoil.tools.horreum.exp.data.TestDao;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.hyperfoil.tools.horreum.svc.Roles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class RunServiceImpl implements RunService {


    @Inject
    TestServiceImpl testService;

    @Inject
    LabelServiceImpl labelService;

    @Transactional
    @WithRoles
//    @RolesAllowed(Roles.UPLOADER)
    @PermitAll
    public Response addRunFromData(
            @QueryParam("test") String test,
            String content
    ){
        //String test = "rhivos-perf-comprehensive";
        try {
            JsonNode json = new ObjectMapper().readValue(content, JsonNode.class);
            RunDao r = createRun(test,json,new ObjectMapper().getNodeFactory().objectNode());
            labelService.calculateLabelValues(r.test.labels, r.id);
            return Response.ok().entity(r.id).build();
        } catch (JsonProcessingException e) {
            return Response.status(500).entity(e.getMessage()).build();
        }
    }

    @Transactional
    @WithRoles
//    @RolesAllowed(Roles.UPLOADER)
    @PermitAll
    RunDao createRun(String test,JsonNode json,JsonNode metadata){
        TestDao t = testService.getByName(test);
        if(t==null){
            return null;
        }
        RunDao r = new RunDao(t.id,json,metadata);
        r.persistAndFlush();
        return r;
    }
}
