package io.hyperfoil.tools.horreum.api.services;

import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.hyperfoil.tools.horreum.api.ApiIgnore;
import io.hyperfoil.tools.horreum.api.data.ValidationError;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.Run;

import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/api/run")
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public interface RunService {
   @APIResponse(content = @Content(schema = @Schema(implementation = RunExtended.class)), description = "Returns an instance of RunExtended")
   @GET
   @Path("{id}")
   Object getRun(@PathParam("id") int id,
                 @QueryParam("token") String token);

   @GET
   @Path("{id}/summary")
   RunSummary getRunSummary(@PathParam("id") int id, @QueryParam("token") String token);

   @GET
   @Path("{id}/data")
   Object getData(@PathParam("id") int id, @QueryParam("token") String token, @QueryParam("schemaUri") String schemaUri);

   @GET
   @Path("{id}/metadata")
   Object getMetadata(@PathParam("id") int id, @QueryParam("token") String token, @QueryParam("schemaUri") String schemaUri);

   @GET
   @Path("{id}/query")
   QueryResult queryData(@PathParam("id") int id,
                         @Parameter(required = true) @QueryParam("query") String jsonpath,
                         @QueryParam("uri") String schemaUri,
                         @QueryParam("array") @DefaultValue("false") boolean array);

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
   @Path("test/{test}")
   @Consumes(MediaType.APPLICATION_JSON)
   Response add(@PathParam("test") String testNameOrId,
              @QueryParam("owner") String owner,
              @QueryParam("access") Access access,
              @QueryParam("token") String token,
              @RequestBody(required = true) Run run);

   @POST
   @Path("data")
   @Produces(MediaType.TEXT_PLAIN) // run ID as string
   Response addRunFromData(@Parameter(required = true) @QueryParam("start") String start,
                         @Parameter(required = true) @QueryParam("stop") String stop,
                         @Parameter(required = true) @QueryParam("test") String test,
                         @QueryParam("owner") String owner,
                         @QueryParam("access") Access access,
                         @Parameter(description = "Horreum internal token. Incompatible with Keycloak") @QueryParam("token") String token,
                         @QueryParam("schema") String schemaUri,
                         @QueryParam("description") String description,
                         @RequestBody(required = true) JsonNode data);

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
                           @QueryParam("direction") String direction);

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
                            @QueryParam("direction") String direction);

   @GET
   @Path("bySchema")
   RunsSummary listBySchema(@Parameter(required = true) @QueryParam("uri") String uri,
                            @QueryParam("limit") Integer limit,
                            @QueryParam("page") Integer page,
                            @QueryParam("sort") String sort,
                            @QueryParam("direction") String direction);

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
      public ArrayNode datasets;
      @Schema(implementation = ValidationError[].class)
      public ArrayNode validationErrors;
   }

   class RunExtended extends Run {
      @NotNull
      public List<SchemaService.SchemaUsage> schemas;
      @NotNull
      public String testname;
      @Schema(required = true, implementation = int[].class)
      public int[] datasets;
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
