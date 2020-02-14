package io.hyperfoil.tools.repo.api;

import io.agroal.api.AgroalDataSource;
import io.hyperfoil.tools.repo.entity.json.Access;
import io.hyperfoil.tools.repo.entity.json.Schema;
import io.hyperfoil.tools.repo.entity.json.SchemaExtractor;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;

@Path("api/schema")
public class SchemaService {
   private static final Logger log = Logger.getLogger(SchemaService.class);

   private static final String UPDATE_TOKEN = "UPDATE schema SET token = ? WHERE id = ?";
   private static final String CHANGE_ACCESS = "UPDATE schema SET owner = ?, access = ? WHERE id = ?";

   private static final JsonSchemaFactory JSON_SCHEMA_FACTORY = new JsonSchemaFactory.Builder()
         .defaultMetaSchemaURI(JsonMetaSchema.getV4().getUri())
         .addMetaSchema(JsonMetaSchema.getV4())
         .addMetaSchema(JsonMetaSchema.getV6())
         .addMetaSchema(JsonMetaSchema.getV7())
         .addMetaSchema(JsonMetaSchema.getV201909()).build();


   @Inject
   EntityManager em;

   @Inject
   SqlService sqlService;

   @Inject
   AgroalDataSource dataSource;

   @Inject
   SecurityIdentity identity;

   @PermitAll
   @GET
   @Path("{id}")
   @Produces(MediaType.APPLICATION_JSON)
   public Schema getSchema(@PathParam("id") int id, @QueryParam("token") String token){
      try (CloseMe h1 = sqlService.withRoles(em, identity);
           CloseMe h2 = sqlService.withToken(em, token)) {
         return Schema.find("id", id).firstResult();
      }
   }

   @RolesAllowed(Roles.TESTER)
   @POST
   @Transactional
   public Response add(Schema schema){
      try (CloseMe h = sqlService.withRoles(em, identity)) {
         Schema byName = Schema.find("name", schema.name).firstResult();
         if (byName != null) {
            if (Objects.equals(schema.id, byName.id)) {
               em.merge(schema);
            } else {
               Response.serverError().entity("Name already used");
            }
         } else {
            schema.id = null; //remove the id so we don't override an existing entry
            em.persist(schema);
         }
         em.flush();//manually flush to validate constraints
         return Response.ok(schema.id).build();
      }
   }

   public List<Schema> all(){
      return list(null,null,"name", Sort.Direction.Ascending);
   }

   @PermitAll
   @GET
   public List<Schema> list(@QueryParam("limit") Integer limit,
                            @QueryParam("page") Integer page,
                            @QueryParam("sort") String sort,
                            @QueryParam("direction") @DefaultValue("Ascending") Sort.Direction direction){
      try (CloseMe h = sqlService.withRoles(em, identity)) {
         if (sort == null || sort.isEmpty()) {
            sort = "name";
         }
         if (limit != null && page != null) {
            return Schema.findAll(Sort.by(sort).direction(direction)).page(Page.of(page, limit)).list();
         } else {
            return Schema.listAll(Sort.by(sort).direction(direction));
         }
      }
   }

   @RolesAllowed("tester")
   @POST
   @Path("{id}/resetToken")
   public Response resetToken(@PathParam("id") Integer id) {
      return updateToken(id, Tokens.generateToken());
   }

   @RolesAllowed("tester")
   @POST
   @Path("{id}/dropToken")
   public Response dropToken(@PathParam("id") Integer id) {
      return updateToken(id, null);
   }

   private Response updateToken(Integer id, String token) {
      try (Connection connection = dataSource.getConnection();
           CloseMeJdbc h = sqlService.withRoles(connection, identity);
           PreparedStatement statement = connection.prepareStatement(UPDATE_TOKEN)) {
         if (token != null) {
            statement.setString(1, token);
         } else {
            statement.setNull(1, Types.VARCHAR);
         }
         statement.setInt(2, id);
         if (statement.executeUpdate() != 1) {
            return Response.serverError().entity("Token reset failed (missing permissions?)").build();
         } else {
            return (token != null ? Response.ok(token) : Response.noContent()).build();
         }
      } catch (SQLException e) {
         log.error("GET /id/resetToken failed", e);
         return Response.serverError().entity("Token reset failed").build();
      }
   }

   @RolesAllowed("tester")
   @POST
   @Path("{id}/updateAccess")
   // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
   public Response updateAccess(@PathParam("id") Integer id,
                                @QueryParam("owner") String owner,
                                @QueryParam("access") Access access) {
      try (Connection connection = dataSource.getConnection();
           CloseMeJdbc h = sqlService.withRoles(connection, identity);
           PreparedStatement statement = connection.prepareStatement(CHANGE_ACCESS)) {
         statement.setString(1, owner);
         statement.setInt(2, access.ordinal());
         statement.setInt(3, id);
         if (statement.executeUpdate() != 1) {
            return Response.serverError().entity("Access change failed (missing permissions?)").build();
         } else {
            return Response.accepted().build();
         }
      } catch (SQLException e) {
         log.error("GET /id/resetToken failed", e);
         return Response.serverError().entity("Access change failed").build();
      }
   }

   public Json validate(Json data, String schemaUri) {
      if (schemaUri == null || schemaUri.isEmpty()) {
         return null;
      }
      Schema schema = Schema.find("uri", schemaUri).firstResult();
      // TODO: inlined JsonValidator due to https://github.com/Hyperfoil/yaup/issues/23
      JsonNode jsonData = Json.toJsonNode(data);
      JsonNode jsonSchema = Json.toJsonNode(schema.schema);
      Set<ValidationMessage> errors = JSON_SCHEMA_FACTORY.getSchema(jsonSchema).validate(jsonData);
      Json rtrn = new Json();
      errors.forEach(validationMessage -> {
         Json entry = new Json();
         entry.set("message", validationMessage.getMessage());
         entry.set("code", validationMessage.getCode());
         entry.set("path", validationMessage.getPath());
         entry.set("arguemnts", new Json(true));
         for (String arg : validationMessage.getArguments()) {
            entry.getJson("arguments").add(arg);
         }
         entry.set("details", new Json(false));
         validationMessage.getDetails().forEach((k, v) -> {
            entry.getJson("details").set(k, v);
         });
         rtrn.add(entry);
      });
      return rtrn;
   }

   @PermitAll
   @GET
   @Path("extractor")
   @Produces(MediaType.APPLICATION_JSON)
   public List<SchemaExtractor> listExtractors(@QueryParam("schemaId") Integer schema) {
      try (CloseMe h = sqlService.withRoles(em, identity)) {
         if (schema == null) {
            return SchemaExtractor.<SchemaExtractor>findAll().stream().collect(Collectors.toList());
         } else {
            return SchemaExtractor.<SchemaExtractor>find("schema_id", schema).stream().collect(Collectors.toList());
         }
      }
   }


   @RolesAllowed("tester")
   @POST
   @Path("extractor")
   @Consumes(MediaType.APPLICATION_JSON)
   @Transactional
   public Response addOrUpdateExtractor(Json json) {
      if (json == null) {
         return Response.status(Response.Status.BAD_REQUEST).entity("No extractor").build();
      }
      String accessor = json.getString("accessor");
      String newName = json.getString("newName", accessor);
      String schema = json.getString("schema");
      String jsonpath = json.getString("jsonpath");
      boolean deleted = json.getBoolean("deleted");

      if (accessor == null && newName != null) {
         accessor = newName;
      }
      if (accessor == null || accessor.isEmpty() || schema == null || jsonpath == null || !jsonpath.startsWith("$.")) {
         return Response.status(Response.Status.BAD_REQUEST).build();
      }
      try (CloseMe h = sqlService.withRoles(em, identity)) {
         Schema persistedSchema = Schema.find("uri", schema).firstResult();
         if (persistedSchema == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing schema " + schema).build();
         }
         SchemaExtractor extractor = SchemaExtractor.find("schema_id = ?1 and accessor = ?2", persistedSchema.id, accessor).firstResult();
         boolean isNew = false;
         if (extractor == null) {
            extractor = new SchemaExtractor();
            isNew = true;
            if (deleted) {
               return Response.status(Response.Status.NOT_FOUND).build();
            }
         } else if (deleted) {
            em.remove(extractor);
            return Response.noContent().build();
         }
         extractor.accessor = newName;
         extractor.schema = persistedSchema;
         extractor.jsonpath = jsonpath;
         if (isNew) {
            em.persist(extractor);
         }
         return Response.noContent().build();
      }
   }


//I'm not sure being able to delete a schema is a good idea since we don't have reference tracking built into the table
//   @DELETE
//   @Path("{name:.*}")
//   public Response delete(@PathParam("name")String name){
//      Schema byName = Schema.find("name",name).firstResult();
//      if(byName == null){
//         return Response.noContent().build();
//      }else{
//         byName.delete();
//         return Response.ok(byName.id).build();
//      }
//   }
}
