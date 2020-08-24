package io.hyperfoil.tools.horreum.api;

import io.agroal.api.AgroalDataSource;
import io.hyperfoil.tools.horreum.entity.json.Access;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.entity.json.View;
import io.hyperfoil.tools.horreum.entity.json.ViewComponent;
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
import java.util.Collections;
import java.util.List;

import org.jboss.logging.Logger;

@Path("/api/test")
@Consumes({MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public class TestService {
   private static final Logger log = Logger.getLogger(TestService.class);

   private static final String SUMMARY = "select test.id,test.name,test.description,count(run.id) as count," +
         "test.owner,test.token,test.access from test left join run on run.testid = test.id group by test.id";
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
   @Transactional
   public void delete(@PathParam("id") Integer id){
      try (CloseMe h = sqlService.withRoles(em, identity)){
         View.find("test_id", id).stream().forEach(view -> {
            ViewComponent.delete("view_id", ((View) view).id);
            view.delete();
         });
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
      if (input.matches("-?\\d+")) {
         int id = Integer.parseInt(input);
         return Test.find("name = ?1 or id = ?2", input, id).firstResult();
      } else {
         return Test.find("name", input).firstResult();
      }
   }

   public Test getByName(String name){
      Test existing = Test.find("name", name).firstResult();
      return existing;
   }

   @RolesAllowed(Roles.TESTER)
   @POST
   @Transactional
   public Response add(Test test){
      if (!identity.hasRole(test.owner)) {
         return Response.status(Response.Status.FORBIDDEN).entity("This user does not have the " + test.owner + " role!").build();
      }
      try (CloseMe h = sqlService.withRoles(em, identity)) {
         if (test == null) {
            return Response.serverError().entity("test is null").build();
         }
         Response response = addAuthenticated(test);
         return response == null ? Response.ok(test).build() : response;
      }
   }

   Response addAuthenticated(Test test) {
      Test existing = Test.find("name", test.name).firstResult();
      if (test.id <= 0) {
         test.id = null;
      }
      test.ensureLinked();
      if (existing != null) {
         if (!identity.hasRole(existing.owner)) {
            return Response.status(Response.Status.FORBIDDEN).entity("This user does not have the " + existing.owner + " role!").build();
         }
         // We're not updating view using this method
         if (test.defaultView == null) {
            test.defaultView = existing.defaultView;
         }
         test.copyIds(existing);
         em.merge(test);
      } else {
         em.persist(test);
         if (test.defaultView != null) {
            em.persist(test.defaultView);
         } else {
            View view = new View();
            view.name = "default";
            view.components = Collections.emptyList();
            view.test = test;
            em.persist(view);
            test.defaultView = view;
         }
         eventBus.publish(Test.EVENT_NEW, test);
      }
      return null;
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

   @RolesAllowed("tester")
   @POST
   @Path("{testId}/view")
   public Response updateView(@PathParam("testId") Integer testId, View view) {
      if (testId == null || testId <= 0) {
         return Response.status(Response.Status.BAD_REQUEST).entity("Missing test id").build();
      }
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         Test test = Test.findById(testId);
         if (test == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
         }
         view.ensureLinked();
         view.test = test;
         if (test.defaultView != null) {
            view.copyIds(test.defaultView);
         }
         if (view.id == null) {
            em.persist(view);
         } else {
            test.defaultView = em.merge(view);
         }
         test.persist();
      }
      return Response.noContent().build();
   }
}
