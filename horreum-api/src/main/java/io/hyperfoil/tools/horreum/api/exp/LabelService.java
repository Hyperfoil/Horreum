package io.hyperfoil.tools.horreum.api.exp;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.exp.data.Label;
import io.hyperfoil.tools.horreum.api.exp.data.LabelGroup;

@Path("/api/x/labels")
@Produces(APPLICATION_JSON)
@Tag(name = "exp_label", description = "Experimental label service.")
public interface LabelService {

    public static class ValueMap {
        public final ObjectNode data;
        public final long index;
        public final long labelId;
        public final long runId;
        public final long testId;

        public ValueMap(ObjectNode data, long index, long labelId, long runId, long testId) {
            this.data = data;
            this.index = index;
            this.labelId = labelId;
            this.runId = runId;
            this.testId = testId;
        }

        public ObjectNode data() {
            return data;
        }

        public long index() {
            return index;
        }

        public long labelId() {
            return labelId;
        }

        public long runId() {
            return runId;
        }

        public long testId() {
            return testId;
        }

    }

    @GET
    Label get(long id);

    @Path("findLabel")
    public List<Label> whatCanWeFind(@QueryParam("name") String name, @QueryParam("groupId") long groupId);

    //not exposed atm
    public List<Label> findLabelFromFqdn(String name, long groupId);

    @Path("findGroup")
    public List<LabelGroup> findGroup(@QueryParam("name") String name, @QueryParam("scope") String scope);

}
