package io.hyperfoil.tools.horreum.api;

import java.util.Collection;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.ValidationMessage;

import io.hyperfoil.tools.horreum.entity.json.Schema;
import io.hyperfoil.tools.horreum.entity.json.SchemaExtractor;
import io.hyperfoil.tools.horreum.entity.json.Transformer;
import io.quarkus.panache.common.Sort;

@Path("api/schema")
public interface SchemaService {
   @GET
   @Path("{id}")
   @Produces(MediaType.APPLICATION_JSON)
   Schema getSchema(@PathParam("id") int id, @QueryParam("token") String token);

   @POST
   @Consumes(MediaType.APPLICATION_JSON)
   Integer add(Schema schema);

   @GET
   List<Schema> list(@QueryParam("limit") Integer limit,
                     @QueryParam("page") Integer page,
                     @QueryParam("sort") String sort,
                     @QueryParam("direction") @DefaultValue("Ascending") Sort.Direction direction);

   @POST
   @Produces(MediaType.TEXT_PLAIN)
   @Path("{id}/resetToken")
   String resetToken(@PathParam("id") Integer id);

   @POST
   @Produces(MediaType.TEXT_PLAIN)
   @Path("{id}/dropToken")
   String dropToken(@PathParam("id") Integer id);

   String updateToken(Integer id, String token);

   @POST
   @Path("{id}/updateAccess")
   @Consumes(MediaType.TEXT_PLAIN) //is POST the correct verb for this method as we are not uploading a new artefact?
   // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
   void updateAccess(@PathParam("id") Integer id,
                     @QueryParam("owner") String owner,
                     @QueryParam("access") int access);

   @POST
   @Path("validate")
   @Consumes(MediaType.APPLICATION_JSON)
   Collection<ValidationMessage> validate(JsonNode data, @QueryParam("schema") String schemaUri);

   @GET
   @Path("extractor")
   @Produces(MediaType.APPLICATION_JSON)
   List<SchemaExtractor> listExtractors(@QueryParam("schemaId") Integer schema, @QueryParam("accessor") String accessor);

   @POST
   @Path("extractor")
   @Consumes(MediaType.APPLICATION_JSON)
   SchemaExtractor addOrUpdateExtractor(ExtractorUpdate update);

   @GET
   @Path("extractor/{id}/deprecated")
   List<SchemaExtractor> findDeprecated(@PathParam("id") Integer extractorId);

   @DELETE
   @Path("{id}")
   void delete(@PathParam("id") Integer id);

   @GET
   @Path("findUsages")
   @Produces(MediaType.APPLICATION_JSON)
   List<AccessorLocation> findUsages(@QueryParam("accessor") String accessor);

   @GET
   @Path("{schemaId}/transformers")
   @Produces(MediaType.APPLICATION_JSON)
   List<Transformer> listTransformers(@PathParam("schemaId") Integer schemaId);

   @POST
   @Path("{schemaId}/transformers")
   @Consumes(MediaType.APPLICATION_JSON)
   Integer addOrUpdateTransformer(@PathParam("schemaId") Integer schemaId, Transformer transformer);

   class ExtractorUpdate {
      public int id;
      public String accessor;
      public String schema;
      public String jsonpath;
      public boolean deleted;
   }

   abstract class AccessorLocation {
      public final String type;
      public int testId;
      public String testName;

      public AccessorLocation(String type, int testId, String testName) {
         this.type = type;
         this.testId = testId;
         this.testName = testName;
      }
   }

   class AccessorInTags extends AccessorLocation {
      public AccessorInTags(int testId, String testName) {
         super("TAGS", testId, testName);
      }
   }

   class AccessorInVariable extends AccessorLocation {
      public int variableId;
      public String variableName;

      public AccessorInVariable(int testId, String testName, int variableId, String variableName) {
         super("VARIABLE", testId, testName);
         this.variableId = variableId;
         this.variableName = variableName;
      }
   }

   class AccessorInView extends AccessorLocation {
      public int viewId;
      public String viewName;
      public int componentId;
      public String header;

      public AccessorInView(int testId, String testName, int viewId, String viewName, int componentId, String header) {
         super("VIEW", testId, testName);
         this.viewId = viewId;
         this.componentId = componentId;
         this.viewName = viewName;
         this.header = header;
      }
   }

   class AccessorInReport extends AccessorLocation {
      public int configId;
      public String title;
      public String where; // component, filter, category, series, label
      public String name; // only set for component

      public AccessorInReport(int testId, String testName, int configId, String title, String where, String name) {
         super("REPORT", testId, testName);
         this.configId = configId;
         this.title = title;
         this.where = where;
         this.name = name;
      }
   }
}
