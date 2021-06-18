package io.hyperfoil.tools.horreum.api;

import io.hyperfoil.tools.horreum.entity.converter.JsonResultTransformer;
import io.hyperfoil.tools.horreum.entity.json.*;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.eventbus.EventBus;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;

@Path("/api/test")
@Consumes({MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public class TestService {
   //@formatter:off
   private static final String SUMMARY =
         "SELECT test.id,test.name,test.description,count(run.id) AS count,test.owner,test.access " +
         "FROM test LEFT JOIN run ON run.testid = test.id " +
         "WHERE run.trashed = false OR run.trashed IS NULL " +
         "GROUP BY test.id";
   //@formatter:on
   private static final String UPDATE_NOTIFICATIONS = "UPDATE test SET notificationsenabled = ? WHERE id = ?";
   private static final String CHANGE_ACCESS = "UPDATE test SET owner = ?, access = ? WHERE id = ?";
   private static final String TRASH_RUNS = "UPDATE run SET trashed = true WHERE testid = ?";


   @Inject
   EntityManager em;

   @Inject
   EventBus eventBus;

   @Inject
   SqlService sqlService;

   @Inject
   SecurityIdentity identity;

   @RolesAllowed(Roles.TESTER)
   @DELETE
   @Path("{id}")
   @Transactional
   public void delete(@PathParam("id") Integer id){
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)){
         Test test = Test.findById(id);
         if (test == null) {
            throw new WebApplicationException("No test with id " + id, 404);
         }
         test.defaultView = null;
         em.merge(test);
         View.find("test_id", id).stream().forEach(view -> {
            ViewComponent.delete("view_id", ((View) view).id);
            view.delete();
         });
         test.delete();
         em.createNativeQuery(TRASH_RUNS).setParameter(1, test.id).executeUpdate();
      }
   }

   @PermitAll
   @GET
   @Path("{id}")
   public Test get(@PathParam("id") Integer id, @QueryParam("token") String token){
      try (@SuppressWarnings("unused") CloseMe h1 = sqlService.withRoles(em, identity);
           @SuppressWarnings("unused") CloseMe h2 = sqlService.withToken(em, token)) {
         Test test = Test.find("id", id).firstResult();
         if (test == null) {
            throw new WebApplicationException(404);
         }
         Hibernate.initialize(test.tokens);
         return test;
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

   @RolesAllowed(Roles.TESTER)
   @POST
   @Transactional
   public Response add(Test test){
      if (!identity.hasRole(test.owner)) {
         return Response.status(Response.Status.FORBIDDEN).entity("This user does not have the " + test.owner + " role!").build();
      }
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         Response response = addAuthenticated(test);
         return response == null ? Response.ok(test).build() : response;
      }
   }

   Response addAuthenticated(Test test) {
      Test existing = Test.find("id", test.id).firstResult();
      if (test.id != null && test.id <= 0) {
         test.id = null;
      }
      if (test.notificationsEnabled == null) {
         test.notificationsEnabled = true;
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
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
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
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         Query query = em.createNativeQuery(SUMMARY);
         SqlService.setResultTransformer(query, JsonResultTransformer.INSTANCE);
         return Response.ok(query.getResultList()).build();
      }
   }

   @RolesAllowed("tester")
   @POST
   @Path("{id}/addToken")
   @Transactional
   public Response addToken(@PathParam("id") Integer testId, TestToken token) {
      if (token.hasUpload() && !token.hasRead()) {
         return Response.status(400).entity("Upload permission requires read permission as well.").build();
      }
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         Test test = Test.findById(testId);
         if (test == null) {
            return Response.status(404).build();
         }
         token.test = test;
         test.tokens.add(token);
         test.persistAndFlush();
      }
      return Response.ok(token.id).build();
   }

   @RolesAllowed("tester")
   @GET
   @Path("{id}/tokens")
   public Collection<TestToken> tokens(@PathParam("id") Integer testId) {
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         Test t = Test.findById(testId);
         return t.tokens;
      }
   }

   @RolesAllowed("tester")
   @POST
   @Path("{id}/revokeToken/{tokenId}")
   @Transactional
   public Response dropToken(@PathParam("id") Integer testId, @PathParam("tokenId") Integer tokenId) {
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         Test test = Test.findById(testId);
         if (test == null) {
            return Response.status(404).build();
         }
         test.tokens.removeIf(t -> Objects.equals(t.id, tokenId));
         test.persist();
      }
      return Response.noContent().build();
   }

   @RolesAllowed("tester")
   @POST
   @Path("{id}/updateAccess")
   // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
   public Response updateAccess(@PathParam("id") Integer id,
                                @QueryParam("owner") String owner,
                                @QueryParam("access") Access access) {
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         Query query = em.createNativeQuery(CHANGE_ACCESS);
         query.setParameter(1, owner);
         query.setParameter(2, access.ordinal());
         query.setParameter(3, id);
         if (query.executeUpdate() != 1) {
            return Response.serverError().entity("Access change failed (missing permissions?)").build();
         } else {
            return Response.accepted().build();
         }
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

   @RolesAllowed("tester")
   @POST
   @Consumes // any
   @Path("{id}/notifications")
   public Response updateAccess(@PathParam("id") Integer id,
                                @QueryParam("enabled") boolean enabled) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         Query query = em.createNativeQuery(UPDATE_NOTIFICATIONS)
               .setParameter(1, enabled)
               .setParameter(2, id);
         if (query.executeUpdate() != 1) {
            return Response.serverError().entity("Access change failed (missing permissions?)").build();
         } else {
            return Response.status(Response.Status.NO_CONTENT).build();
         }
      }
   }

   @RolesAllowed("tester")
   @POST
   @Path("{testId}/hook")
   public Response updateHook(@PathParam("testId") Integer testId, Hook hook) {
      if (testId == null || testId <= 0) {
         return Response.status(Response.Status.BAD_REQUEST).entity("Missing test id").build();
      }
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         Test test = Test.findById(testId);
         if (test == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
         }
         hook.target = testId;

         if (hook.id == null) {
            em.persist(hook);
         } else {
            if (!hook.active) {
               Hook toDelete = em.find(Hook.class, hook.id);
               em.remove(toDelete);
            } else {
               em.merge(hook);
            }
         }
         test.persist();
      }
      return Response.noContent().build();
   }
}
