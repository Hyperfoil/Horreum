package io.hyperfoil.tools.horreum.api.services;

import java.util.List;
import java.util.Map;

import io.hyperfoil.tools.horreum.api.data.DataSet;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.hyperfoil.tools.horreum.api.ApiIgnore;
import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.ValidationError;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.Run;

import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/api/run")
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public interface RunService {
   @GET
   @Path("{id}")
   @APIResponse(
           responseCode = "404",
           description = "If no Run have been found with the given id",
           content = @Content(mediaType = MediaType.APPLICATION_JSON))
   @APIResponseSchema( value = RunExtended.class,
           responseDescription = "Run data with the referenced schemas and generated datasets",
           responseCode = "200")
   RunExtended getRun(@PathParam("id") int id,
                 @QueryParam("token") String token);

   @GET
   @Path("{id}/summary")
   @APIResponse(
           responseCode = "404",
           description = "If no Run have been found with the given id",
           content = @Content(mediaType = MediaType.APPLICATION_JSON))
   @APIResponseSchema( value = RunSummary.class,
           responseDescription = "Run summary with the referenced schemas and generated datasets",
           responseCode = "200")
   RunSummary getRunSummary(@PathParam("id") int id, @QueryParam("token") String token);

   @GET
   @Path("{id}/data")
   Object getData(@PathParam("id") int id, @QueryParam("token") String token, @QueryParam("schemaUri") String schemaUri);

   @GET
   @Path("{id}/metadata")
   Object getMetadata(@PathParam("id") int id, @QueryParam("token") String token, @QueryParam("schemaUri") String schemaUri);

   @POST
   @Path("{id}/resetToken")
   String resetToken(@PathParam("id") int id);

   @POST
   @Path("{id}/dropToken")
   String dropToken(@PathParam("id") int id);

   @POST
   @Path("{id}/updateAccess")
   // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
   void updateAccess(@PathParam("id") int id,
                     @Parameter(required = true) @QueryParam("owner") String owner,
                     @Parameter(required = true) @QueryParam("access") Access access);

   @POST
   @Path("test")
   @Consumes(MediaType.APPLICATION_JSON)
   Response add(@QueryParam("test") String testNameOrId,
              @QueryParam("owner") String owner,
              @QueryParam("access") Access access,
              @QueryParam("token") String token,
              @RequestBody(required = true) Run run);

   @POST
   @Path("data")
   @RequestBody(content = @Content( mediaType = MediaType.APPLICATION_JSON,
           schema = @Schema( type = SchemaType.STRING, implementation = String.class)) )
   @APIResponse(
           responseCode = "400",
           description = "Some fields are missing or invalid",
           content = @Content(mediaType = MediaType.APPLICATION_JSON))
   @APIResponseSchema(value = Integer.class,
           responseDescription = "Returns the id of the newly generated run.",
           responseCode = "200")
   Response addRunFromData(@Parameter(required = true) @QueryParam("start") String start,
                         @Parameter(required = true) @QueryParam("stop") String stop,
                         @Parameter(required = true) @QueryParam("test") String test,
                         @QueryParam("owner") String owner,
                         @QueryParam("access") Access access,
                         @Parameter(description = "Horreum internal token. Incompatible with Keycloak") @QueryParam("token") String token,
                         @QueryParam("schema") String schemaUri,
                         @QueryParam("description") String description,
                         @RequestBody(required = true) String data);

   @POST
   @Path("data")
   @Consumes(MediaType.MULTIPART_FORM_DATA)
   @Produces(MediaType.TEXT_PLAIN) // run ID as string
   @APIResponses(value = { 
     @APIResponse(responseCode = "200", 
       content = { @Content(mediaType = MediaType.TEXT_PLAIN, 
       schema = @Schema(type = SchemaType.STRING)) })})
   @ApiIgnore
   Response addRunFromData(@Parameter(required = true) @QueryParam("start") String start,
                           @Parameter(required = true) @QueryParam("stop") String stop,
                           @Parameter(required = true) @QueryParam("test") String test,
                           @QueryParam("owner") String owner,
                           @QueryParam("access") Access access,
                           @Parameter(description = "Horreum internal token. Incompatible with Keycloak") @QueryParam("token") String token,
                           @QueryParam("schema") String schemaUri,
                           @QueryParam("description") String description,
                           @RestForm("data") FileUpload data,
                           @RestForm("metadata") FileUpload metadata);
   @GET
   @Path("{id}/waitforDatasets")
   void waitForDatasets(@PathParam("id") int id);

   @GET
   @Path("autocomplete")
   List<String> autocomplete(@Parameter(required = true) @QueryParam("query") String query);

   @GET
   @Path("list")
   RunsSummary listAllRuns(@QueryParam("query") String query,
                           @QueryParam("matchAll") boolean matchAll,
                           @QueryParam("roles") String roles,
                           @QueryParam("trashed") boolean trashed,
                           @QueryParam("limit") Integer limit,
                           @QueryParam("page") Integer page,
                           @QueryParam("sort") String sort,
                           @QueryParam("direction") SortDirection direction);

   @GET
   @Path("count")
   RunCount runCount(@Parameter(required = true) @QueryParam("testId") int testId);

   @GET
   @Path("list/{testId}")
   RunsSummary listTestRuns(@PathParam("testId") int testId,
                            @QueryParam("trashed") boolean trashed,
                            @QueryParam("limit") Integer limit,
                            @QueryParam("page") Integer page,
                            @QueryParam("sort") String sort,
                            @QueryParam("direction") SortDirection direction);

   @GET
   @Path("bySchema")
   RunsSummary listBySchema(@Parameter(required = true) @QueryParam("uri") String uri,
                            @QueryParam("limit") Integer limit,
                            @QueryParam("page") Integer page,
                            @QueryParam("sort") String sort,
                            @QueryParam("direction") SortDirection direction);

   @POST
   @Path("{id}/trash")
   void trash(@PathParam("id") int id, @QueryParam("isTrashed") Boolean isTrashed);

   @POST
   @Path("{id}/description")
   @Consumes(MediaType.TEXT_PLAIN)
   void updateDescription(@PathParam("id") int id, @RequestBody(required = true) String description);

   @POST
   @Path("{id}/schema")
   @Consumes(MediaType.TEXT_PLAIN)
   Map<Integer, String> updateSchema(@PathParam("id") int id, @QueryParam("path") String path, @RequestBody(required = true) String schemaUri);

   @POST
   @Path("{id}/recalculate")
   List<Integer> recalculateDatasets(@PathParam("id") int runId);

   @POST
   @Path("recalculateAll")
   void recalculateAll(@QueryParam("from") String from, @QueryParam("to") String to);

   class RunSummary {
      @JsonProperty(required = true)
      public int id;
      @JsonProperty(required = true)
      public long start;
      @JsonProperty(required = true)
      public long stop;
      @JsonProperty(required = true)
      public int testid;
      @NotNull
      public String owner;
      @Schema(required = true, implementation = Access.class)
      public int access;
      public String token;
      @NotNull
      public String testname;
      @JsonProperty(required = true)
      public boolean trashed;
      @JsonProperty(required = true)
      public boolean hasMetadata;
      public String description;
      public List<SchemaService.SchemaUsage> schemas;
      @Schema(required = true, implementation = int[].class)
      public Integer[] datasets;
      @Schema(implementation = ValidationError[].class)
      public ValidationError[] validationErrors;
   }

   @JsonIgnoreProperties({ "token", "old_start" }) //ignore properties that have not been mapped
   class RunExtended extends Run {
      @NotNull
      @Schema(required = true)
      public List<SchemaService.SchemaUsage> schemas;
      @NotNull
      @Schema(required = true)
      public String testname;
      @Schema(required = true, implementation = int[].class)
      public Integer[] datasets;
   }

   class RunsSummary {
      @JsonProperty(required = true)
      public long total;
      @NotNull
      public List<RunSummary> runs;
   }

   class RunCount {
      @JsonProperty(required = true)
      public long total;
      @JsonProperty(required = true)
      public long active;
      @JsonProperty(required = true)
      public long trashed;
   }
}
