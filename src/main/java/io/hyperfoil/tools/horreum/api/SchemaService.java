package io.hyperfoil.tools.horreum.api;

import io.agroal.api.AgroalDataSource;
import io.hyperfoil.tools.horreum.entity.json.Access;
import io.hyperfoil.tools.horreum.entity.json.Schema;
import io.hyperfoil.tools.horreum.entity.json.SchemaExtractor;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
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
import javax.ws.rs.core.Response;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.uri.URIFactory;
import com.networknt.schema.uri.URIFetcher;
import com.networknt.schema.uri.URLFactory;

@Path("api/schema")
public class SchemaService {
   private static final Logger log = Logger.getLogger(SchemaService.class);

   private static final String UPDATE_TOKEN = "UPDATE schema SET token = ? WHERE id = ?";
   private static final String CHANGE_ACCESS = "UPDATE schema SET owner = ?, access = ? WHERE id = ?";
   private static final String FETCH_SCHEMAS_RECURSIVE = "WITH RECURSIVE refs(uri) AS (" +
         "SELECT ? UNION ALL " +
         "SELECT substring(jsonb_path_query(schema, '$.**.\"$ref\" ? (! (@ starts with \"#\"))')#>>'{}' from '[^#]*') as uri " +
            "FROM refs INNER JOIN schema on refs.uri = schema.uri) " +
         "SELECT schema.* FROM schema INNER JOIN refs ON schema.uri = refs.uri";

   private static final JsonSchemaFactory JSON_SCHEMA_FACTORY = new JsonSchemaFactory.Builder()
         .defaultMetaSchemaURI(JsonMetaSchema.getV4().getUri())
         .addMetaSchema(JsonMetaSchema.getV4())
         .addMetaSchema(JsonMetaSchema.getV6())
         .addMetaSchema(JsonMetaSchema.getV7())
         .addMetaSchema(JsonMetaSchema.getV201909()).build();
   private static final URIFactory URN_FACTORY = new URIFactory() {
      @Override
      public URI create(String uri) {
         return URI.create(uri);
      }

      @Override
      public URI create(URI baseURI, String segment) {
         throw new UnsupportedOperationException();
      }
   };
   private static final String[] ALL_URNS = Stream.concat(
         URLFactory.SUPPORTED_SCHEMES.stream(), Stream.of("urn")
   ).toArray(String[]::new);


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
               return Response.serverError().entity("Name already used").build();
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
                                @QueryParam("access") int access) {
      if (access < Access.PUBLIC.ordinal() || access > Access.PRIVATE.ordinal()) {
         return Response.status(Response.Status.BAD_REQUEST).entity("Access not within bounds").build();
      }
      try (Connection connection = dataSource.getConnection();
           CloseMeJdbc h = sqlService.withRoles(connection, identity);
           PreparedStatement statement = connection.prepareStatement(CHANGE_ACCESS)) {
         statement.setString(1, owner);
         statement.setInt(2, access);
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

   @PermitAll
   @POST
   @Path("validate")
   @Consumes(MediaType.APPLICATION_JSON)
   public Json validate(Json data, @QueryParam("schema") String schemaUri) {
      if (schemaUri == null || schemaUri.isEmpty()) {
         return null;
      }
      Query fetchSchemas = em.createNativeQuery(FETCH_SCHEMAS_RECURSIVE, Schema.class);
      fetchSchemas.setParameter(1, schemaUri);
      Map<String, Schema> schemas = ((Stream<Schema>) fetchSchemas.getResultStream())
            .collect(Collectors.toMap(s -> s.uri, Function.identity()));
      Schema rootSchema = schemas.get(schemaUri);
      if (rootSchema == null || rootSchema.schema == null) {
         return null;
      }
      Set<ValidationMessage> errors;
      try {
         URIFetcher uriFetcher = uri -> new ByteArrayInputStream(schemas.get(uri.toString()).schema.toString().getBytes(StandardCharsets.UTF_8));

         JsonSchemaFactory factory = JsonSchemaFactory.builder(JSON_SCHEMA_FACTORY)
               .uriFactory(URN_FACTORY, "urn")
               .uriFetcher(uriFetcher, ALL_URNS).build();

         JsonNode jsonData = Json.toJsonNode(data);
         JsonNode jsonSchema = Json.toJsonNode(rootSchema.schema);
         errors = factory.getSchema(jsonSchema).validate(jsonData);
      } catch (Exception e) {
         // Do not let messed up schemas fail the upload
         log.warn("Schema validation failed", e);
         return null;
      }
      Json rtrn = new Json();
      errors.forEach(validationMessage -> {
         Json entry = new Json();
         entry.set("message", validationMessage.getMessage());
         entry.set("code", validationMessage.getCode());
         entry.set("path", validationMessage.getPath());
         if (validationMessage.getArguments() != null) {
            entry.set("arguments", new Json(true));
            for (String arg : validationMessage.getArguments()) {
               entry.getJson("arguments").add(arg);
            }
         }
         if (validationMessage.getDetails() != null) {
            entry.set("details", new Json(false));
            validationMessage.getDetails().forEach((k, v) -> {
               entry.getJson("details").set(k, v);
            });
         }
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

      if ((accessor == null || accessor.isEmpty()) && newName != null && !newName.isEmpty()) {
         accessor = newName;
      }
      if (accessor == null || accessor.isEmpty() || schema == null || jsonpath == null) {
         return Response.status(Response.Status.BAD_REQUEST).build();
      }
      if (jsonpath.startsWith("$")) {
         jsonpath = jsonpath.substring(1);
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
   @RolesAllowed("tester")
   @DELETE
   @Path("{id}")
   @Transactional
   public Response delete(@PathParam("id") Integer id){
      Schema schema = Schema.find("id", id).firstResult();
      if (schema == null){
         return Response.status(Response.Status.NOT_FOUND).build();
      } else {
         SchemaExtractor.delete("schema_id", id);
         schema.delete();
         return Response.noContent().build();
      }
   }
}
