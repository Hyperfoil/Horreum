package io.hyperfoil.tools.horreum.api.services;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.Fingerprints;
import io.hyperfoil.tools.horreum.api.data.ExportedLabelValues;
import io.hyperfoil.tools.horreum.api.data.ProtectedType;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.api.data.TestToken;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Path("/api/test")
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Test", description = "Endpoint giving access to tests defined in Horreum.")
@Extension(name = "x-smallrye-profile-external", value = "")
public interface TestService {
   @DELETE
   @Path("{id}")
   @Operation(description="Delete a Test by id")
   void delete(@PathParam("id") int id);

   @GET
   @Path("{id}")
   @Operation(description="Retrieve a test by id")
   Test get(@PathParam("id") int id, @QueryParam("token") String token);

   @GET
   @Path("byName/{name}")
   @Operation(description="Retrieve a test by name")
   @Parameters(value = {
           @Parameter(name = "name",in = ParameterIn.PATH, description = "Name of test to retrieve", example = "my-comprehensive-benchmark"),
   }
   )
   Test getByNameOrId(@PathParam("name") String input);

   @POST
   @Operation(description="Create a new test")
   Test add(@RequestBody(required = true) Test test);

   @GET
   @Operation(description="Retrieve a paginated list of Tests with available count")
   @Parameters(value = {
           @Parameter(name = "roles", description = "__my, __all or a comma delimited  list of roles", example = "__my"),
           @Parameter(name = "limit", description = "limit the number of results", example = "20"),
           @Parameter(name = "page", description = "filter by page number of a paginated list of Tests", example = "2"),
           @Parameter(name = "sort", description = "Field name to sort results", example = "name"),
           @Parameter(name = "direction", description = "Sort direction", example ="Ascending")
      }
   )
   TestQueryResult list(@QueryParam("roles") String roles,
                   @QueryParam("limit") Integer limit,
                   @QueryParam("page") Integer page,
                   @QueryParam("sort") @DefaultValue("name") String sort,
                   @QueryParam("direction") SortDirection direction);

   @Path("summary")
   @GET
   @Operation(description="Retrieve a summary of Tests in a folder")
   @Parameters(value = {
           @Parameter(name = "roles", description = "\"__my\", \"__all\" or a comma delimited  list of roles", example = "__my"),
           @Parameter(name = "folder", description = "name of the Folder containing the Tests", example = "My Team Folder"),
   }
   )
   TestListing summary(@QueryParam("roles") String roles, @QueryParam("folder") String folder);

   @Path("folders")
   @GET
   @Operation(description="Retrieve a list of all folders")
   @Parameters(value = {
           @Parameter(name = "roles", description = "\"__my\", \"__all\" or a comma delimited  list of roles", example = "__my")
   })
   @APIResponses(
           value = {
                 @APIResponse( responseCode = "200",
                         description = "List of all folders",
                         content = {
                           @Content (
                                   schema = @Schema(type = SchemaType.ARRAY, implementation = String.class),
                                   example = "[ \"quarkus\", \"ocp-perf-team\"] ")
                         }
                 )
   }
   )
   List<String> folders(@QueryParam("roles") String roles);

   @POST
   @Path("{id}/addToken")
   @Operation(description="Add a Test API Token for access to provide access to a test data for integrated tooling, e.g. reporting services")
   @Parameters(value = {
           @Parameter(name = "id", description = "ID of test to add token to", example = "101")
   })
   int addToken(@PathParam("id") int testId, TestToken token);

   @GET
   @Path("{id}/tokens")
   @Operation(description="A collection of Test Tokens for a given Test")
   @Parameters(value = {
           @Parameter(name = "id", description = "ID of test to retrieve list of tokens", example = "101")
   })
   Collection<TestToken> tokens(@PathParam("id") int testId);

   @DELETE
   @Path("{id}/revokeToken/{tokenId}")
   @Operation(description="Revoke a Token defined for a Test")
   @Parameters(value = {
           @Parameter(name = "id", description = "Test ID to revoke token", example = "101"),
           @Parameter(name = "tokenId", description = "ID of token to revoke", example = "202")
   })
   void dropToken(@PathParam("id") int testId, @PathParam("tokenId") int tokenId);

   @POST
   @Path("{id}/updateAccess")
   // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
   @Operation(description="Update the Access configuration for a Test")
   @Parameters(value = {
           @Parameter(name = "id", required = true, description = "Test ID to revoke token", example = "101"),
           @Parameter(name = "owner", required = true, description = "Name of the new owner", example = "perf-team"),
           @Parameter(name = "access", required = true, description = "New Access level for the Test", example = "0")
   })
   void updateAccess(@PathParam("id") int id,
                     @QueryParam("owner") String owner,
                     @QueryParam("access") Access access);

   @POST
   @Consumes // any
   @Path("{id}/notifications")
   @Operation(description="Update notifications for a Test. It is possible to disable notifications for a Test, so that no notifications are sent to subscribers")
   @Parameters(value = {
           @Parameter(name = "id", required = true, description = "Test ID to update", example = "101"),
           @Parameter(name = "enabled", required = true, description = "Whether notifications are enabled", example = "false"),
   })
   void updateNotifications(@PathParam("id") int id,
                            @QueryParam("enabled") boolean enabled);

   @POST
   @Path("{id}/move")
   @Operation(description="Update the folder for a Test. Tests can be moved to different folders")
   @Parameters(value = {
           @Parameter(name = "id", description = "Test ID to update", example = "101"),
           @Parameter(name = "folder", description = "New folder to store the tests", example = "My Benchmark Folder"),
   })
   void updateFolder(@PathParam("id") int id, @QueryParam("folder") String folder);

   @GET
   @Path("{id}/fingerprint")
   @Operation(description="List all Fingerprints for a Test")
   @Parameters(value = {
           @Parameter(name = "id", description = "Test ID to retrieve Fingerprints for", example = "101"),
   })
   @APIResponses( value = {
           @APIResponse( responseCode = "200", content = {
                   @Content ( schema = @Schema(type = SchemaType.ARRAY, implementation = Fingerprints.class))
           })
   })
   List<Fingerprints> listFingerprints(@PathParam("id") int testId);

   @GET
   @Path("{id}/labelValues")
   @Operation(description="List all Label Values for a Test")
   @Parameters(value = {
           @Parameter(name = "id", description = "Test ID to retrieve Label Values for", example = "101"),
           @Parameter(name = "filtering", description = "Retrieve values for Filtering Labels", example = "true"),
           @Parameter(name = "metrics", description = "Retrieve values for Metric Labels", example = "false"),
   })
   @APIResponses(
           value = { @APIResponse( responseCode = "200",
                   content = {
                           @Content ( schema = @Schema(type = SchemaType.ARRAY, implementation = ExportedLabelValues.class)) }
           )}
   )
   List<ExportedLabelValues> listLabelValues(@PathParam("id") int testId,
                                             @QueryParam("filtering") @DefaultValue("true") boolean filtering,
                                             @QueryParam("metrics") @DefaultValue("true") boolean metrics);

   @POST
   @Consumes(MediaType.APPLICATION_JSON)
   @Path("{id}/transformers")
   @Operation(description="Update transformers for Test")
   @Parameters(value = {
           @Parameter(name = "id", description = "Test ID to retrieve Label Values for", example = "101"),
   })
   void updateTransformers(@PathParam("id") int testId, @RequestBody(required = true) List<Integer> transformerIds);

   @POST
   @Path("{id}/recalculate")
   @Operation(description="Recalculate Datasets for Test")
   @Parameters(value = {
           @Parameter(name = "id", description = "Test ID to recalculate datasets for", example = "101"),
   })
   void recalculateDatasets(@PathParam("id") int testId);

   @GET
   @Path("{id}/recalculate")
   @Operation(description="Get recalculation status for Test")
   @Parameters(value = {
           @Parameter(name = "id", description = "Test ID to retrieve recalculation status for", example = "101"),
   })
   RecalculationStatus getRecalculationStatus(@PathParam("id") int testId);

   @GET
   @Path("{id}/export")
   @APIResponseSchema(value = String.class,
           responseDescription = "A Test defintion formatted as json",
           responseCode = "200")
   String export(@PathParam("id") int testId);

   @POST
   @Path("import")
   @APIResponse(responseCode = "204", description = "Import a new test")
   @RequestBody(content = @Content( mediaType = MediaType.APPLICATION_JSON,
           schema = @Schema( type = SchemaType.STRING, implementation = String.class)) )
   @Operation(description="Import a previously exported Test")
   void importTest( String testConfig);

   class TestListing {
      @Schema(description = "Array of Test Summaries")
      public List<TestSummary> tests;
   }

   @Schema(type = SchemaType.OBJECT, allOf = ProtectedType.class)
   class TestSummary extends ProtectedType {
      @JsonProperty(required = true)
      @Schema(description = "ID of tests", example = "101")
      public int id;
      @NotNull
      @Schema(description="Test name",
              example="my-comprehensive-benchmark")
      public String name;
      @Schema(description="Name of folder that the test is stored in. Folders allow tests to be organised in the UI",
              example="My Team Folder")
      public String folder;
      @Schema(description="Description of the test",
              example="Comprehensive benchmark to tests the limits of any system it is run against")
      public String description;
      // SQL count(*) returns BigInteger
      @Schema(description="Total number of Datasets for the Test",
              example="202")
      public Number datasets;
      @Schema(description="Total number of Runs for the Test",
              example="101")
      public Number runs;

      @Schema(description="Subscriptions for each test for authenticated user",
              example="[]")
      public Set<String> watching;

      public TestSummary(int id, String name, String folder, String description,
                         Number datasets, Number runs, String owner, Access access) {
         this.id = id;
         this.name = name;
         this.folder = folder;
         this.description = description;
         this.datasets = datasets;
         this.runs = runs;
         this.owner = owner;
         this.access = access;
      }
   }

   class RecalculationStatus {
      @JsonProperty(required = true)
      @Schema(description = "Recalculation timestamp", example = "1698013206000")
      public long timestamp;
      @JsonProperty(required = true)
      @Schema(description = "Total number of Runs being recalculated", example = "152")
      public long totalRuns;
      @JsonProperty(required = true)
      @Schema(description = "Total number of completed recalculations", example = "93")
      public long finished;
      @JsonProperty(required = true)
      @Schema(description = "Total number of generated datasets", example = "186")
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
      @Schema(description="Array of Tests")
      public List<Test> tests;
      @Schema(description="Count of available tests. This is a count of tests that the current user has access to",
              example="64")
      @JsonProperty(required = true)
      public long count;

      public TestQueryResult(List<Test> tests, long count) {
         this.tests = tests;
         this.count = count;
      }
   }
}
