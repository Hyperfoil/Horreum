package io.hyperfoil.tools.horreum.api.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.Label;
import io.hyperfoil.tools.horreum.api.data.Schema;
import io.hyperfoil.tools.horreum.api.data.Transformer;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;


@Path("api/schema")
@Produces(MediaType.APPLICATION_JSON)
@Consumes({ MediaType.APPLICATION_JSON})
public interface SchemaService {
   @GET
   @Path("{id}")
   @APIResponse(
           responseCode = "404",
           description = "No Schema with the given id was found",
           content = @Content(mediaType = "application/json"))
   @APIResponseSchema(value = Schema.class,
           responseCode = "200",
           responseDescription = "Returns Schema if a matching id is found")
   Schema getSchema(@PathParam("id") int id, @QueryParam("token") String token);

   @GET
   @Path("idByUri/{uri}")
   int idByUri(@PathParam("uri") String uri);

   @POST
   @Consumes(MediaType.APPLICATION_JSON)
   @Produces(MediaType.APPLICATION_JSON)
   Integer add(Schema schema);

   @GET
   SchemaQueryResult list(@QueryParam("limit") Integer limit,
                          @QueryParam("page") Integer page,
                          @QueryParam("sort") String sort,
                          @QueryParam("direction") SortDirection direction);


   @GET
   @Path("descriptors")
   @Produces(MediaType.APPLICATION_JSON)
   List<SchemaDescriptor> descriptors(@QueryParam("id") List<Integer> ids);

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
                     @Parameter(required = true) @QueryParam("access") int access);

   @DELETE
   @Path("{id}")
   void delete(@PathParam("id") int id);

   @GET
   @Path("findUsages")
   @Produces(MediaType.APPLICATION_JSON)
   List<LabelLocation> findUsages(@Parameter(required = true) @QueryParam("label") String label);

   @GET
   @Path("{schemaId}/transformers")
   @Produces(MediaType.APPLICATION_JSON)
   List<Transformer> listTransformers(@PathParam("schemaId") int schemaId);

   @POST
   @Path("{schemaId}/transformers")
   @Consumes(MediaType.APPLICATION_JSON)
   @Produces(MediaType.APPLICATION_JSON)
   int addOrUpdateTransformer(@PathParam("schemaId") int schemaId,
                              @RequestBody(required = true) Transformer transformer);

   @DELETE
   @Path("{schemaId}/transformers/{transformerId}")
   void deleteTransformer(@PathParam("schemaId") int schemaId, @PathParam("transformerId") int transformerId);

   @GET
   @Path("{schemaId}/labels")
   @Produces(MediaType.APPLICATION_JSON)
   List<Label> labels(@PathParam("schemaId") int schemaId);

   @POST
   @Path("{schemaId}/labels")
   @Consumes(MediaType.APPLICATION_JSON)
   Integer addOrUpdateLabel(@PathParam("schemaId") int schemaId, @RequestBody(required = true) Label label);

   @DELETE
   @Path("{schemaId}/labels/{labelId}")
   void deleteLabel(@PathParam("schemaId") int schemaId, @PathParam("labelId") int labelId);

   @GET
   @Path("allLabels")
   @Produces(MediaType.APPLICATION_JSON)
   Collection<LabelInfo> allLabels(@QueryParam("name") String name);

   @GET
   @Path("allTransformers")
   @Produces(MediaType.APPLICATION_JSON)
   List<TransformerInfo> allTransformers();

   @GET
   @Path("{id}/export")
   @Produces(MediaType.APPLICATION_JSON)
   @APIResponseSchema(value = String.class,
           responseDescription = "A JSON representation of the Schema object",
           responseCode = "200")
   String exportSchema(@PathParam("id") int id);

   @POST
   @Path("import")
   @Consumes(MediaType.APPLICATION_JSON)
   @RequestBody(content = @Content( mediaType = MediaType.APPLICATION_JSON,
           schema = @org.eclipse.microprofile.openapi.annotations.media.Schema(
                   type = SchemaType.STRING, implementation = String.class)) )
   void importSchema(String config);

   class SchemaQueryResult {
      @NotNull
      public List<Schema> schemas;
      @JsonProperty(required = true)
      public long count;

      public SchemaQueryResult(List<Schema> schemas, long count) {
         this.schemas = schemas;
         this.count = count;
      }
   }

   @org.eclipse.microprofile.openapi.annotations.media.Schema(name = "LabelLocation", type = SchemaType.OBJECT)
   abstract class LabelLocation {
      public final String type;
      public int testId;
      public String testName;

      public LabelLocation(String type, int testId, String testName) {
         this.type = type;
         this.testId = testId;
         this.testName = testName;
      }
   }

   @org.eclipse.microprofile.openapi.annotations.media.Schema(
           name = "LabelInFingerprint",
           type = SchemaType.OBJECT,
           allOf = {LabelLocation.class}
   )
   class LabelInFingerprint extends LabelLocation {
      public LabelInFingerprint(int testId, String testName) {
         super("FINGERPRINT", testId, testName);
      }
   }

   @org.eclipse.microprofile.openapi.annotations.media.Schema(
           name = "LabelInRule",
           type = SchemaType.OBJECT,
           allOf = {LabelLocation.class}
   )
   class LabelInRule extends LabelLocation {
      public int ruleId;
      public String ruleName;

      public LabelInRule(int testId, String testName, int ruleId, String ruleName) {
         super("MISSINGDATA_RULE", testId, testName);
         this.ruleId = ruleId;
         this.ruleName = ruleName;
      }
   }

   @org.eclipse.microprofile.openapi.annotations.media.Schema(
           name = "LabelInVariable",
           type = SchemaType.OBJECT,
           allOf = {LabelLocation.class}
   )
   class LabelInVariable extends LabelLocation {
      public int variableId;
      public String variableName;

      public LabelInVariable(int testId, String testName, int variableId, String variableName) {
         super("VARIABLE", testId, testName);
         this.variableId = variableId;
         this.variableName = variableName;
      }
   }

   @org.eclipse.microprofile.openapi.annotations.media.Schema(
           name = "LabelInView",
           type = SchemaType.OBJECT,
           allOf = {LabelLocation.class}
   )
   class LabelInView extends LabelLocation {
      public int viewId;
      public String viewName;
      public int componentId;
      public String header;

      public LabelInView(int testId, String testName, int viewId, String viewName, int componentId, String header) {
         super("VIEW", testId, testName);
         this.viewId = viewId;
         this.componentId = componentId;
         this.viewName = viewName;
         this.header = header;
      }
   }

   @org.eclipse.microprofile.openapi.annotations.media.Schema(
           name = "LabelInReport",
           type = SchemaType.OBJECT,
           allOf = {LabelLocation.class}
   )
   class LabelInReport extends LabelLocation {
      public int configId;
      public String title;
      public String where; // component, filter, category, series, label
      public String name; // only set for component

      public LabelInReport(int testId, String testName, int configId, String title, String where, String name) {
         super("REPORT", testId, testName);
         this.configId = configId;
         this.title = title;
         this.where = where;
         this.name = name;
      }
   }

   class TransformerInfo {
      @JsonProperty(required = true)
      public int schemaId;
      @NotNull
      public String schemaUri;
      @NotNull
      public String schemaName;
      @JsonProperty(required = true)
      public int transformerId;
      @NotNull
      public String transformerName;
   }

   class SchemaDescriptor {
      @JsonProperty(required = true)
      public int id;
      @NotNull
      public String name;
      @NotNull
      public String uri;

      public SchemaDescriptor() {}

      public SchemaDescriptor(int id, String name, String uri) {
         this.id = id;
         this.name = name;
         this.uri = uri;
      }
   }

   // this roughly matches run_schemas table

   class SchemaUsage extends SchemaDescriptor {
      // 0 is data, 1 is metadata. DataSets always use 0
      @JsonProperty(required = true)
      public int source;

      @JsonProperty(required = true)
      public int type;

      public String key;

      @JsonProperty(required = true)
      public boolean hasJsonSchema;
   }

   class LabelInfo {
      @NotNull
      public String name;
      @JsonProperty(required = true)
      public boolean metrics;
      @JsonProperty(required = true)
      public boolean filtering;
      @NotNull
      public List<SchemaDescriptor> schemas = new ArrayList<>();
      public LabelInfo() {}
      public LabelInfo(String name) {
         this.name = name;
      }
   }
}
