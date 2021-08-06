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

import com.networknt.schema.ValidationMessage;

import io.hyperfoil.tools.horreum.entity.json.Schema;
import io.hyperfoil.tools.horreum.entity.json.SchemaExtractor;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.panache.common.Sort;

@Path("api/schema")
public interface SchemaService {
   @GET
   @Path("{id}")
   @Produces(MediaType.APPLICATION_JSON)
   Schema getSchema(@PathParam("id") int id, @QueryParam("token") String token);

   @POST
   Integer add(Schema schema);

   List<Schema> all();

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
   // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
   void updateAccess(@PathParam("id") Integer id,
                     @QueryParam("owner") String owner,
                     @QueryParam("access") int access);

   @POST
   @Path("validate")
   @Consumes(MediaType.APPLICATION_JSON)
   Collection<ValidationMessage> validate(Json data, @QueryParam("schema") String schemaUri);

   @GET
   @Path("extractor")
   @Produces(MediaType.APPLICATION_JSON)
   List<SchemaExtractor> listExtractors(@QueryParam("schemaId") Integer schema);

   @POST
   @Path("extractor")
   @Consumes(MediaType.APPLICATION_JSON)
   void addOrUpdateExtractor(Json json);

   @DELETE
   @Path("{id}")
   void delete(@PathParam("id") Integer id);
}
