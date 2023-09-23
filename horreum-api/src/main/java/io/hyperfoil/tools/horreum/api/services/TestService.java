package io.hyperfoil.tools.horreum.api.services;

import java.util.Collection;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.*;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;

@Path("/api/test")
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public interface TestService {
   @DELETE
   @Path("{id}")
   void delete(@PathParam("id") int id);

   @GET
   @Path("{id}")
   Test get(@PathParam("id") int id, @QueryParam("token") String token);

   @GET
   @Path("byName/{name}")
   Test getByNameOrId(@PathParam("name") String input);

   @POST
   Test add(@RequestBody(required = true) Test test);

   @GET
   TestQueryResult list(@QueryParam("roles") String roles,
                   @QueryParam("limit") Integer limit,
                   @QueryParam("page") Integer page,
                   @QueryParam("sort") @DefaultValue("name") String sort,
                   @QueryParam("direction") @DefaultValue("Ascending") SortDirection direction);

   @Path("summary")
   @GET
   TestListing summary(@QueryParam("roles") String roles, @QueryParam("folder") String folder);

   @Path("folders")
   @GET
   List<String> folders(@QueryParam("roles") String roles);

   @POST
   @Path("{id}/addToken")
   @Produces(MediaType.TEXT_PLAIN)
   int addToken(@PathParam("id") int testId, TestToken token);

   @GET
   @Path("{id}/tokens")
   Collection<TestToken> tokens(@PathParam("id") int testId);

   @POST
   @Path("{id}/revokeToken/{tokenId}")
   void dropToken(@PathParam("id") int testId, @PathParam("tokenId") int tokenId);

   @POST
   @Path("{id}/updateAccess")
   // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
   void updateAccess(@PathParam("id") int id,
                     @Parameter(required = true) @QueryParam("owner") String owner,
                     @Parameter(required = true) @QueryParam("access") Access access);

   @POST
   @Consumes // any
   @Path("{id}/notifications")
   void updateNotifications(@PathParam("id") int id, @Parameter(required = true) @QueryParam("enabled") boolean enabled);

   @POST
   @Path("{id}/move")
   void updateFolder(@PathParam("id") int id, @QueryParam("folder") String folder);

   @GET
   @Path("{id}/fingerprint")
   List<JsonNode> listFingerprints(@PathParam("id") int testId);

   @GET
   @Path("{id}/labelValues")
   List<JsonNode> listLabelValues(@PathParam("id") int testId,
                                  @QueryParam("filtering") @DefaultValue("true") boolean filtering,
                                  @QueryParam("metrics") @DefaultValue("true") boolean metrics);

   @POST
   @Consumes(MediaType.APPLICATION_JSON)
   @Path("{id}/transformers")
   void updateTransformers(@PathParam("id") int testId, @RequestBody(required = true) List<Integer> transformerIds);

   @POST
   @Path("{id}/recalculate")
   void recalculateDatasets(@PathParam("id") int testId);

   @GET
   @Path("{id}/recalculate")
   RecalculationStatus getRecalculationStatus(@PathParam("id") int testId);

   @GET
   @Path("{id}/export")
   @APIResponseSchema(value = String.class,
           responseDescription = "A Run data object formatted as json",
           responseCode = "200")
   String export(@PathParam("id") int testId);

   @POST
   @Path("import")
   @APIResponse(responseCode = "204", description = "Import a new test")
   @RequestBody(content = @Content( mediaType = MediaType.APPLICATION_JSON,
           schema = @Schema( type = SchemaType.STRING, implementation = String.class)) )
   void importTest( String testConfig);

   class TestListing {
      public List<TestSummary> tests;
   }

   class TestSummary {
      @JsonProperty(required = true)
      public int id;
      @NotNull
      public String name;
      public String folder;
      public String description;
      // SQL count(*) returns BigInteger
      public Number datasets;
      public Number runs;
      @NotNull
      public String owner;
      @Schema(implementation = Access.class, required = true)
      public int access;
   }

   class RecalculationStatus {
      @JsonProperty(required = true)
      public long timestamp;
      @JsonProperty(required = true)
      public long totalRuns;
      @JsonProperty(required = true)
      public long finished;
      @JsonProperty(required = true)
      public long datasets;

      public RecalculationStatus() {
      }

      public RecalculationStatus(long totalRuns) {
         this.timestamp = System.currentTimeMillis();
         this.totalRuns = totalRuns;
      }
   }

   class TestQueryResult {
      @NotNull
      public List<Test> tests;
      @JsonProperty(required = true)
      public long count;

      public TestQueryResult(List<Test> tests, long count) {
         this.tests = tests;
         this.count = count;
      }
   }
}
