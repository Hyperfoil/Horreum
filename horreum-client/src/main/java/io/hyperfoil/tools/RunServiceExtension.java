package io.hyperfoil.tools;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataOutput;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.client.RunService;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.ExportedLabelValues;
import io.hyperfoil.tools.horreum.api.data.Run;
import io.hyperfoil.tools.horreum.api.services.RunService.RunCount;
import io.hyperfoil.tools.horreum.api.services.RunService.RunSummary;
import io.hyperfoil.tools.horreum.api.services.RunService.RunsSummary;

// Note: IDE may show errors because variant of RunService.addRunFromData is not implemented
// but that method is not present in the class files (removed through @ApiIgnore).
public class RunServiceExtension implements RunService {
    private final ResteasyWebTarget target;
    private final RunService delegate;

    public RunServiceExtension(ResteasyWebTarget target, RunService delegate) {
        this.target = target;
        this.delegate = delegate;
    }

    /**
     * Additional method provided to add Run from data using JsonNode as data and metadata object
     * @return Response
     */
    public Response addRunFromData(String start, String stop, String test, String owner, Access access, String schemaUri,
            String description, JsonNode data) {
        return this.delegate.addRunFromData(start, stop, test, owner, access, schemaUri, description, data);
    }

    /**
     * Additional method provided to add Run from data using JsonNode as data and metadata object
     * @return Response
     */
    public Response addRunFromData(String start, String stop, String test, String owner, Access access,
            String schemaUri, String description, JsonNode data, JsonNode... metadata) {
        MultipartFormDataOutput multipart = new MultipartFormDataOutput();
        multipart.addFormData("data", data, MediaType.APPLICATION_JSON_TYPE, "data.json");
        if (metadata != null && metadata.length > 0) {
            JsonNode combinedMetadata;
            if (metadata.length > 1) {
                if (Stream.of(metadata).anyMatch(obj -> !obj.isObject())) {
                    throw new IllegalArgumentException("When passing multiple metadata each item must be an object.");
                } else if (Stream.of(metadata).anyMatch(obj -> !obj.hasNonNull("$schema"))) {
                    throw new IllegalArgumentException("All metadata must have '$schema' set.");
                }
                combinedMetadata = JsonNodeFactory.instance.arrayNode().addAll(Arrays.asList(metadata));
            } else {
                combinedMetadata = metadata[0];
                if (!combinedMetadata.isArray() && !combinedMetadata.has("$schema")) {
                    throw new IllegalArgumentException("Metadata must have '$schema' set.");
                }
            }
            multipart.addFormData("metadata", combinedMetadata, MediaType.APPLICATION_JSON_TYPE, "metadata.json");
        }
        return target.path("/api/run/data")
                .queryParam("start", start).queryParam("stop", stop).queryParam("test", test)
                .queryParam("owner", owner).queryParam("access", access)
                .queryParam("schema", schemaUri).queryParam("description", description)
                .request().post(Entity.entity(multipart, MediaType.MULTIPART_FORM_DATA));
    }

    @Override
    public io.hyperfoil.tools.horreum.api.services.RunService.RunExtended getRun(int id) {
        return this.delegate.getRun(id);
    }

    @Override
    public RunSummary getRunSummary(int id) {
        return this.delegate.getRunSummary(id);
    }

    @Override
    public Object getData(int id, String schemaUri) {
        return this.delegate.getData(id, schemaUri);
    }

    @Override
    public List<ExportedLabelValues> getRunLabelValues(int runId, String filter, String sort, String direction, int limit,
            int page, List<String> include, List<String> exclude, boolean multiFilter) {
        return this.delegate.getRunLabelValues(runId, filter, sort, direction, limit, page, include, exclude, multiFilter);
    }

    @Override
    public Object getMetadata(int id, String schemaUri) {
        return this.delegate.getMetadata(id, schemaUri);
    }

    @Override
    public void updateRunAccess(int id, String owner, Access access) {
        this.delegate.updateRunAccess(id, owner, access);
    }

    @Override
    public List<Integer> addRun(String testNameOrId, String owner, Access access, Run run) {
        return this.delegate.addRun(testNameOrId, owner, access, run);
    }

    @Override
    public List<String> autocomplete(String query) {
        return this.delegate.autocomplete(query);
    }

    @Override
    public RunsSummary listAllRuns(String query, boolean matchAll, String roles, boolean trashed, Integer limit, Integer page,
            String sort, SortDirection direction) {
        return this.delegate.listAllRuns(query, matchAll, roles, trashed, limit, page, sort, direction);
    }

    @Override
    public RunCount runCount(int testId) {
        return this.delegate.runCount(testId);
    }

    @Override
    public RunsSummary listTestRuns(int testId, boolean trashed, Integer limit, Integer page, String sort,
            SortDirection direction) {
        return this.delegate.listTestRuns(testId, trashed, limit, page, sort, direction);
    }

    @Override
    public RunsSummary listRunsBySchema(String uri, Integer limit, Integer page, String sort, SortDirection direction) {
        return this.delegate.listRunsBySchema(uri, limit, page, sort, direction);
    }

    @Override
    public void trash(int id, Boolean isTrashed) {
        this.delegate.trash(id, isTrashed);
    }

    @Override
    public void updateDescription(int id, String description) {
        this.delegate.updateDescription(id, description);
    }

    @Override
    public Map<Integer, String> updateRunSchema(int id, String path, String schemaUri) {
        return this.delegate.updateRunSchema(id, path, schemaUri);
    }

    @Override
    public List<Integer> recalculateRunDatasets(int runId) {
        return this.delegate.recalculateRunDatasets(runId);
    }

    @Override
    public void recalculateAll(String from, String to) {
        this.delegate.recalculateAll(from, to);
    }

}
