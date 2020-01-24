package io.hyperfoil.tools.repo.api;

import io.agroal.api.AgroalDataSource;
import io.hyperfoil.tools.repo.entity.json.Access;
import io.hyperfoil.tools.repo.entity.json.Test;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.eventbus.EventBus;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import org.jboss.logging.Logger;

@Path("/api/test")
@Consumes({MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public class TestService {
   private static final Logger log = Logger.getLogger(TestService.class);

   private static final String SUMMARY = "select test.id,test.name,test.description,count(run.id) as count," +
         "test.schema is not null as hasschema,test.view is not null as hasview,test.owner,test.token,test.access " +
         "from test left join run on run.testId = test.id group by test.id";
   private static final String UPDATE_TOKEN = "UPDATE test SET token = ? WHERE id = ?";
   private static final String CHANGE_ACCESS = "UPDATE test SET owner = ?, access = ? WHERE id = ?";


   @Inject
   EntityManager em;

   @Inject
   EventBus eventBus;

   @Inject
   SqlService sqlService;

   @Inject
   SecurityIdentity identity;

   @Inject
   AgroalDataSource dataSource;

   @RolesAllowed(Roles.TESTER)
   @DELETE
   @Path("{id}")
   public void delete(@PathParam("id") Integer id){
      try (CloseMe h = sqlService.withRoles(em, identity)){
         Test.find("id", id).firstResult().delete();
      }
   }

   @PermitAll
   @GET
   @Path("{id}")
   public Test get(@PathParam("id") Integer id, @QueryParam("token") String token){
      try (CloseMe h1 = sqlService.withRoles(em, identity);
           CloseMe h2 = sqlService.withToken(em, token)) {
         return Test.find("id", id).firstResult();
      }
   }


   public Test getByNameOrId(String input){
      Test foundTest = getByName(input);
      if(foundTest == null && input.matches("-?\\d+")){
         foundTest = get(Integer.parseInt(input), null);
      }
      return foundTest;
   }

   public Test getByName(String name){
      Test existing = Test.find("name", name).firstResult();
      return existing;
   }

   @PermitAll
   @GET
   @Path("{id}/schema")
   public Response getSchema(@PathParam("id") Integer id, @QueryParam("token") String token){
      try (CloseMe h1 = sqlService.withRoles(em, identity);
           CloseMe h2 = sqlService.withToken(em, token)) {
         Test t = Test.find("id", id).firstResult();
         if (t != null) {
            return Response.ok(t.schema).build();
         } else {
            return Response.noContent().build();
         }
      }
   }

   @RolesAllowed(Roles.TESTER)
   @POST
   @Path("{id}/schema")
   @Transactional
   public Response setSchema(@PathParam("id") Integer id, Json schema){
      try (CloseMe h = sqlService.withRoles(em, identity)) {
         Test t = Test.find("id", id).firstResult();
         if (t != null) {
            t.schema = schema;
            em.persist(t);
            return Response.ok().build();
         } else {
            return Response.noContent().build();
         }
      }
   }

   @PermitAll
   @GET
   @Path("{id}/view")
   public Response getView(@PathParam("id") Integer id, @QueryParam("token") String token){
      try (CloseMe h1 = sqlService.withRoles(em, identity);
           CloseMe h2 = sqlService.withToken(em, token)) {
         Test t = Test.find("id", id).firstResult();
         if (t != null) {
            return Response.ok(t.view).build();
         } else {
            return Response.noContent().build();
         }
      }
   }

   @RolesAllowed(Roles.TESTER)
   @POST
   @Path("{id}/view")
   @Transactional
   public Response setView(@PathParam("id") Integer id, Json view){
      try (CloseMe h = sqlService.withRoles(em, identity)) {
         Test t = Test.find("id", id).firstResult();
         if (t != null) {
            t.view = view;
            em.persist(t);
            return Response.ok().build();
         } else {
            return Response.noContent().build();
         }
      }
   }

   @RolesAllowed(Roles.TESTER)
   @POST
   @Transactional
   public Response add(Test test){
      try (CloseMe h = sqlService.withRoles(em, identity)) {
         System.out.println(AsciiArt.ANSI_RED + "add TEST " + AsciiArt.ANSI_RESET + test.id);
         System.out.println("view " + test.view);
         if (test == null) {
            return Response.serverError().entity("test is null").build();
         }
         Test existing = Test.find("name", test.name).firstResult();
         if (existing != null) {
            test.id = existing.id;
            em.merge(test);
         } else {
            em.persist(test);
            eventBus.publish("new/test", test);

         }
         //test.persistAndFlush();
         return Response.ok(test).build();
      }
   }

   public List<Test> all(){
      return list(null,null,"name", Sort.Direction.Ascending);
   }

   @PermitAll
   @GET
   public List<Test> list(@QueryParam("limit") Integer limit,
                          @QueryParam("page") Integer page,
                          @QueryParam("sort") @DefaultValue("name") String sort,
                          @QueryParam("direction") @DefaultValue("Ascending") Sort.Direction direction){
      try (CloseMe h = sqlService.withRoles(em, identity)) {
         if (limit != null && page != null) {
            return Test.findAll(Sort.by(sort).direction(direction)).page(Page.of(page, limit)).list();
         } else {
            return Test.listAll(Sort.by(sort).direction(direction));
         }
      }
   }

   @PermitAll
   @Path("summary")
   @GET
   public Response summary() {
      try (Connection connection = dataSource.getConnection();
           CloseMeJdbc h = sqlService.withRoles(connection, identity);
           PreparedStatement statement = connection.prepareStatement(SUMMARY)) {
         return Response.ok(SqlService.fromResultSet(statement.executeQuery())).build();
      } catch (SQLException e) {
         log.error("Failed to fetch summary", e);
         return Response.serverError().build();
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
}
