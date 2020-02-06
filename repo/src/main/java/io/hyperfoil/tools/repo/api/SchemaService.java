package io.hyperfoil.tools.repo.api;

import io.agroal.api.AgroalDataSource;
import io.hyperfoil.tools.repo.entity.json.Access;
import io.hyperfoil.tools.repo.entity.json.Schema;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
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

import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.jboss.logging.Logger;

@Path("api/schema")
public class SchemaService {
   private static final Logger log = Logger.getLogger(SchemaService.class);

   private static final String UPDATE_TOKEN = "UPDATE schema SET token = ? WHERE id = ?";
   private static final String CHANGE_ACCESS = "UPDATE schema SET owner = ?, access = ? WHERE id = ?";

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
   @Path("{name:.*}")
   @Produces(MediaType.APPLICATION_JSON)
   public Schema getSchema(@PathParam("name") String name, @QueryParam("token") String token){
      try (CloseMe h1 = sqlService.withRoles(em, identity);
           CloseMe h2 = sqlService.withToken(em, token)) {
         return Schema.find("name", name).firstResult();
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

   public String validate(Json data, String schemaUri) {
      if (schemaUri == null || schemaUri.isEmpty()) {
         return null;
      }
      Schema schema = Schema.find("uri", schemaUri).firstResult();
      try {
         SchemaLoader.load(Json.toJSONObject(schema.schema)).validate(Json.toJSONObject(data));
      } catch (ValidationException e) {
         return e.toJSON().toString(2);
      }
      return null;
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
