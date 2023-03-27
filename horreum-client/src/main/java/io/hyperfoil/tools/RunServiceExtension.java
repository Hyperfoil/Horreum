package io.hyperfoil.tools;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.hyperfoil.tools.horreum.api.data.Run;
import io.hyperfoil.tools.horreum.api.services.QueryResult;
import io.hyperfoil.tools.horreum.api.services.RunService;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataOutput;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.hyperfoil.tools.horreum.api.data.Access;

// Note: IDE may show errors because variant of RunService.addRunFromData is not implemented
// but that method is not present in the class files (removed through @ApiIgnore).
public class RunServiceExtension implements RunService {
   private final ResteasyWebTarget target;
   private final RunService delegate;

   public RunServiceExtension(ResteasyWebTarget target, RunService delegate) {
      this.target = target;
      this.delegate = delegate;
   }

   @Override
   public Object getRun(int id, String token) {
      return delegate.getRun(id, token);
   }

   @Override
   public RunSummary getRunSummary(int id, String token) {
      return delegate.getRunSummary(id, token);
   }

   @Override
   public Object getData(int id, String token, String schemaUri) {
      return delegate.getData(id, token, schemaUri);
   }

   @Override
   public Object getMetadata(int id, String token, String schemaUri) {
      return delegate.getMetadata(id, token, schemaUri);
   }

   @Override
   public QueryResult queryData(int id, String jsonpath, String schemaUri, boolean array) {
      return delegate.queryData(id, jsonpath, schemaUri, array);
   }

   @Override
   public String resetToken(int id) {
      return delegate.resetToken(id);
   }

   @Override
   public String dropToken(int id) {
      return delegate.dropToken(id);
   }

   @Override
   public void updateAccess(int id, String owner, Access access) {
      delegate.updateAccess(id, owner, access);
   }

   @Override
   public Response add(String testNameOrId, String owner, Access access, String token, Run run) {
      return delegate.add(testNameOrId, owner, access, token, run);
   }

   @Override
   public Response addRunFromData(String start, String stop, String test, String owner, Access access, String token, String schemaUri, String description, JsonNode data) {
      return delegate.addRunFromData(start, stop, test, owner, access, token, schemaUri, description, data);
   }

   @Override
   public Response addRunFromData(String start, String stop, String test, String owner, Access access, String token, String schemaUri, String description, org.jboss.resteasy.reactive.multipart.FileUpload data, org.jboss.resteasy.reactive.multipart.FileUpload metadata) {
      return delegate.addRunFromData(start, stop, test, owner, access, token, schemaUri, description, data, metadata);
   }

   public Response addRunFromData(String start, String stop, String test, String owner, Access access, String token, String schemaUri, String description, JsonNode data, JsonNode... metadata) {
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
            .queryParam("owner", owner).queryParam("access", access).queryParam("token", token)
            .queryParam("schema", schemaUri).queryParam("description", description)
            .request().post(Entity.entity(multipart, MediaType.MULTIPART_FORM_DATA));
   }

   @Override
   public List<String> autocomplete(String query) {
      return delegate.autocomplete(query);
   }

   @Override
   public RunsSummary listAllRuns(String query, boolean matchAll, String roles, boolean trashed, Integer limit, Integer page, String sort, String direction) {
      return delegate.listAllRuns(query, matchAll, roles, trashed, limit, page, sort, direction);
   }

   @Override
   public RunCount runCount(int testId) {
      return delegate.runCount(testId);
   }

   @Override
   public RunsSummary listTestRuns(int testId, boolean trashed, Integer limit, Integer page, String sort, String direction) {
      return delegate.listTestRuns(testId, trashed, limit, page, sort, direction);
   }

   @Override
   public RunsSummary listBySchema(String uri, Integer limit, Integer page, String sort, String direction) {
      return delegate.listBySchema(uri, limit, page, sort, direction);
   }

   @Override
   public void trash(int id, Boolean isTrashed) {
      delegate.trash(id, isTrashed);
   }

   @Override
   public void updateDescription(int id, String description) {
      delegate.updateDescription(id, description);
   }

   @Override
   public Map<Integer, String> updateSchema(int id, String path, String schemaUri) {
      return delegate.updateSchema(id, path, schemaUri);
   }

   @Override
   public List<Integer> recalculateDatasets(int runId) {
      return delegate.recalculateDatasets(runId);
   }

   @Override
   public void recalculateAll(String from, String to) {
      delegate.recalculateAll(from, to);
   }
}
