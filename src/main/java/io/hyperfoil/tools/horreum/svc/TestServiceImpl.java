package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.api.TestService;
import io.hyperfoil.tools.horreum.entity.json.*;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.eventbus.EventBus;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.Transactional;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.hibernate.Hibernate;
import org.hibernate.transform.Transformers;
import org.jboss.logging.Logger;

public class TestServiceImpl implements TestService {
   private static final Logger log = Logger.getLogger(TestServiceImpl.class);

   private static final String UPDATE_NOTIFICATIONS = "UPDATE test SET notificationsenabled = ? WHERE id = ?";
   private static final String CHANGE_ACCESS = "UPDATE test SET owner = ?, access = ? WHERE id = ?";
   private static final String TRASH_RUNS = "UPDATE run SET trashed = true WHERE testid = ?";

   @Inject
   EntityManager em;

   @Inject
   EventBus eventBus;

   @Inject
   SqlServiceImpl sqlService;

   @Inject
   SecurityIdentity identity;

   @Context
   HttpServletResponse response;

   @Override
   @RolesAllowed(Roles.TESTER)
   @Transactional
   public void delete(Integer id){
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)){
         Test test = Test.findById(id);
         if (test == null) {
            throw ServiceException.notFound("No test with id " + id);
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

   @Override
   @PermitAll
   public Test get(Integer id, String token){
      try (@SuppressWarnings("unused") CloseMe h1 = sqlService.withRoles(em, identity);
           @SuppressWarnings("unused") CloseMe h2 = sqlService.withToken(em, token)) {
         Test test = Test.find("id", id).firstResult();
         if (test == null) {
            throw ServiceException.notFound("No test with id " + id);
         }
         Hibernate.initialize(test.tokens);
         return test;
      }
   }

   @Override
   public Test getByNameOrId(String input){
      if (input.matches("-?\\d+")) {
         int id = Integer.parseInt(input);
         return Test.find("name = ?1 or id = ?2", input, id).firstResult();
      } else {
         return Test.find("name", input).firstResult();
      }
   }

   @Override
   @RolesAllowed(Roles.TESTER)
   @Transactional
   public Test add(Test test){
      if (!identity.hasRole(test.owner)) {
         throw ServiceException.forbidden("This user does not have the " + test.owner + " role!");
      }
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         addAuthenticated(test);
         return test;
      }
   }

   void addAuthenticated(Test test) {
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
            throw ServiceException.forbidden("This user does not have the " + existing.owner + " role!");
         }
         // We're not updating view using this method
         if (test.defaultView == null) {
            test.defaultView = existing.defaultView;
         }
         test.tokens = existing.tokens;
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
         try {
            em.flush();
         } catch (PersistenceException e) {
            if (e.getCause() instanceof org.hibernate.exception.ConstraintViolationException) {
               throw new ServiceException(Response.Status.CONFLICT, "Could not persist test due to another test.");
            } else {
               throw new WebApplicationException(e, Response.serverError().build());
            }
         }
         eventBus.publish(Test.EVENT_NEW, test);
         response.setStatus(Response.Status.CREATED.getStatusCode());
      }
   }

   @Override
   @PermitAll
   public List<Test> list(String roles, Integer limit, Integer page, String sort, Sort.Direction direction){
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         PanacheQuery<Test> query;
         Set<String> actualRoles = null;
         if (Roles.hasRolesParam(roles)) {
            if (roles.equals("__my")) {
               if (!identity.isAnonymous()) {
                  actualRoles = identity.getRoles();
               }
            } else {
               actualRoles = new HashSet<>(Arrays.asList(roles.split(";")));
            }
         }

         Sort sortOptions = Sort.by(sort).direction(direction);
         if (actualRoles == null) {
            query = Test.findAll(sortOptions);
         } else {
            query = Test.find("owner IN ?1", sortOptions, actualRoles);
         }
         if (limit != null && page != null) {
            query.page(Page.of(page, limit));
         }
         return query.list();
      }
   }

   @Override
   @PermitAll
   public List<TestSummary> summary(String roles) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         StringBuilder sql = new StringBuilder();
         sql.append("SELECT test.id,test.name,test.description,count(run.id) AS count,test.owner,test.access ");
         sql.append("FROM test LEFT JOIN run ON run.testid = test.id AND (run.trashed = false OR run.trashed IS NULL)");
         Roles.addRolesSql(identity, "test", sql, roles, 1, " WHERE");
         sql.append(" GROUP BY test.id");
         Query query = em.createNativeQuery(sql.toString());
         Roles.addRolesParam(identity, query, 1, roles);
         SqlServiceImpl.setResultTransformer(query, Transformers.aliasToBean(TestSummary.class));
         //noinspection unchecked
         return query.getResultList();
      }
   }

   @Override
   @RolesAllowed("tester")
   @Transactional
   public Integer addToken(Integer testId, TestToken token) {
      if (token.hasUpload() && !token.hasRead()) {
         throw ServiceException.badRequest("Upload permission requires read permission as well.");
      }
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         Test test = Test.findById(testId);
         if (test == null) {
            throw ServiceException.notFound("Test not found");
         }
         token.test = test;
         test.tokens.add(token);
         test.persistAndFlush();
      }
      return token.id;
   }

   @Override
   @RolesAllowed("tester")
   public Collection<TestToken> tokens(Integer testId) {
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         Test t = Test.findById(testId);
         return t.tokens;
      }
   }

   @Override
   @RolesAllowed("tester")
   @Transactional
   public void dropToken(Integer testId, Integer tokenId) {
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         Test test = Test.findById(testId);
         if (test == null) {
            throw ServiceException.notFound("Test not found.");
         }
         test.tokens.removeIf(t -> Objects.equals(t.id, tokenId));
         test.persist();
      }
   }

   @Override
   @RolesAllowed("tester")
   // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
   public void updateAccess(Integer id,
                            String owner,
                            Access access) {
      try (@SuppressWarnings("unused") CloseMe h = sqlService.withRoles(em, identity)) {
         Query query = em.createNativeQuery(CHANGE_ACCESS);
         query.setParameter(1, owner);
         query.setParameter(2, access.ordinal());
         query.setParameter(3, id);
         if (query.executeUpdate() != 1) {
            throw ServiceException.serverError("Access change failed (missing permissions?)");
         }
      }
   }

   @Override
   @RolesAllowed("tester")
   @Transactional
   public void updateView(Integer testId, View view) {
      if (testId == null || testId <= 0) {
         throw ServiceException.badRequest("Missing test id");
      }
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         Test test = Test.findById(testId);
         if (test == null) {
            throw ServiceException.notFound("Test not found");
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
         em.flush();
      } catch (PersistenceException e) {
         log.error("Failed to persist updated view", e);
         throw ServiceException.badRequest("Failed to persist the view. It is possible that some schema extractors used in this view do not use valid JSON paths.");
      }
   }

   @Override
   @RolesAllowed("tester")
   public void updateAccess(Integer id,
                            boolean enabled) {
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         Query query = em.createNativeQuery(UPDATE_NOTIFICATIONS)
               .setParameter(1, enabled)
               .setParameter(2, id);
         if (query.executeUpdate() != 1) {
            throw ServiceException.serverError("Access change failed (missing permissions?)");
         }
      }
   }

   @Override
   @RolesAllowed("tester")
   public void updateHook(Integer testId, Hook hook) {
      if (testId == null || testId <= 0) {
         throw ServiceException.badRequest("Missing test id");
      }
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         Test test = Test.findById(testId);
         if (test == null) {
            throw ServiceException.notFound("Test not found.");
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
   }

   @Override
   @PermitAll
   public List<Json> tags(Integer testId, Boolean trashed) {
      if (testId == null) {
         throw ServiceException.badRequest("Missing param 'test'");
      }
      try (@SuppressWarnings("unused") CloseMe closeMe = sqlService.withRoles(em, identity)) {
         StringBuilder sql = new StringBuilder("SELECT tags::::text FROM run LEFT JOIN run_tags ON run_tags.runid = run.id WHERE run.testid = ?");
         if (trashed == null || !trashed) {
            sql.append(" AND NOT run.trashed");
         }
         sql.append(" GROUP BY tags");
         Query tagComboQuery = em.createNativeQuery(sql.toString());
         @SuppressWarnings("unchecked") List<String> tagList = tagComboQuery.setParameter(1, testId).getResultList();
         ArrayList<Json> result = new ArrayList<>(tagList.size());
         for (String tags : tagList) {
            result.add(Json.fromString(tags));
         }
         return result;
      }
   }
}
