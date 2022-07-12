package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.TestService;
import io.hyperfoil.tools.horreum.entity.json.*;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.hyperfoil.tools.horreum.server.WithToken;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.hibernate.Hibernate;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.Transformers;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType;
import org.jboss.resteasy.reactive.RestResponse;

public class TestServiceImpl implements TestService {
   private static final Logger log = Logger.getLogger(TestServiceImpl.class);

   private static final String UPDATE_NOTIFICATIONS = "UPDATE test SET notificationsenabled = ? WHERE id = ?";
   private static final String CHANGE_ACCESS = "UPDATE test SET owner = ?, access = ? WHERE id = ?";
   private static final String TRASH_RUNS = "UPDATE run SET trashed = true WHERE testid = ?";
   //@formatter:off
   protected static final String LABEL_VALUES_QUERY =
         "SELECT DISTINCT COALESCE(jsonb_object_agg(label.name, lv.value) FILTER (WHERE label.name IS NOT NULL), '{}'::::jsonb) AS values FROM dataset " +
         "LEFT JOIN label_values lv ON dataset.id = lv.dataset_id " +
         "LEFT JOIN label ON label.id = lv.label_id " +
         "WHERE dataset.testid = ?1 AND (label.id IS NULL OR (?2 AND label.filtering) OR (?3 AND label.metrics))" +
         "GROUP BY dataset_id";
   //@formatter:on

   @Inject
   EntityManager em;

   @Inject
   TransactionManager tm;

   @Inject
   EventBus eventBus;

   @Inject
   SecurityIdentity identity;

   @Inject
   Vertx vertx;

   @Inject
   RunServiceImpl runService;

   @Inject
   DatasetServiceImpl datasetService;

   @Inject
   HookServiceImpl hookService;

   private final ConcurrentHashMap<Integer, RecalculationStatus> recalculations = new ConcurrentHashMap<>();

   @RolesAllowed(Roles.TESTER)
   @WithRoles
   @Transactional
   @Override
   public void delete(int id){
      Test test = Test.findById(id);
      if (test == null) {
         throw ServiceException.notFound("No test with id " + id);
      } else if (!identity.getRoles().contains(test.owner)) {
         throw ServiceException.forbidden("You are not an owner of test " + id);
      }
      test.delete();
      em.createNativeQuery(TRASH_RUNS).setParameter(1, test.id).executeUpdate();
      em.createNativeQuery("DELETE FROM transformationlog WHERE testid = ?1").setParameter(1, test.id);
      Util.publishLater(tm, eventBus, Test.EVENT_DELETED, test);
   }

   @Override
   @WithToken
   @WithRoles
   @PermitAll
   public Test get(int id, String token){
      Test test = Test.find("id", id).firstResult();
      if (test == null) {
         throw ServiceException.notFound("No test with id " + id);
      }
      Hibernate.initialize(test.tokens);
      return test;
   }

   @Override
   public Test getByNameOrId(String input){
      Test test;
      if (input.matches("-?\\d+")) {
         int id = Integer.parseInt(input);
         test =  Test.find("name = ?1 or id = ?2", input, id).firstResult();
      } else {
         test =  Test.find("name", input).firstResult();
      }
      if (test == null) {
         throw ServiceException.notFound("No test with name " + input);
      }
      return test;
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   public Test ensureTestExists(String input, String token){
      Test test;
      if (input.matches("-?\\d+")) {
         int id = Integer.parseInt(input);
         test = Test.find("name = ?1 or id = ?2", input, id).firstResult();
      } else {
         test = Test.find("name", input).firstResult();
      }
      if (test != null) {// we won't return the whole entity with any data
         Test detached = new Test();
         detached.id = test.id;
         detached.owner = test.owner;
         detached.name = input;
         if (Roles.hasRoleWithSuffix(identity, test.owner, "-uploader")) {
            return detached;
         } else if (token != null && test.tokens.stream().anyMatch(tt -> tt.valueEquals(token) && tt.hasUpload())) {
            return detached;
         }
         log.debugf("Failed to retrieve test %s as this user (%s = %s) is not uploader for %s and token %s does not match",
               input, identity.getPrincipal().getName(), identity.getRoles(), test.owner, token);
      } else {
         log.debugf("Failed to retrieve test %s - could not find it in the database", input);
      }
      // we need to be vague about the test existence
      throw ServiceException.notFound("Cannot upload to test " + input);
   }

   @Override
   @RolesAllowed(Roles.TESTER)
   @WithRoles
   @Transactional
   public Test add(Test test){
      if (!identity.hasRole(test.owner)) {
         throw ServiceException.forbidden("This user does not have the " + test.owner + " role!");
      }
      addAuthenticated(test);
      Hibernate.initialize(test.tokens);
      return test;
   }

   void addAuthenticated(Test test) {
      Test existing = Test.find("id", test.id).firstResult();
      if (test.id != null && test.id <= 0) {
         test.id = null;
      }
      if (test.notificationsEnabled == null) {
         test.notificationsEnabled = true;
      }
      test.folder = normalizeFolderName(test.folder);
      if ("*".equals(test.folder)) {
         throw new IllegalArgumentException("Illegal folder name '*': this is used as wildcard.");
      }
      test.ensureLinked();
      if (existing != null) {
         if (!identity.hasRole(existing.owner)) {
            throw ServiceException.forbidden("This user does not have the " + existing.owner + " role!");
         }
         // We're not updating views using this method
         test.defaultView = existing.defaultView;
         test.views = existing.views;
         test.tokens = existing.tokens;
         em.merge(test);
      } else {
         if (test.defaultView == null) {
            test.defaultView = new View();
         }
         test.defaultView.id = null;
         test.defaultView.test = test;
         test.defaultView.name = "Default";
         if (test.defaultView.components == null) {
            test.defaultView.components = Collections.emptyList();
         }
         // We need to persist the test before view in order for RLS to work
         em.persist(test);
         em.persist(test.defaultView);
         if (test.views == null) {
            test.views = Collections.singleton(test.defaultView);
         } else {
            test.views.removeIf(v -> "Default".equalsIgnoreCase(v.name));
            test.views.add(test.defaultView);
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
         Util.publishLater(tm, eventBus, Test.EVENT_NEW, test);
      }
   }

   @Override
   @PermitAll
   @WithRoles
   public List<Test> list(String roles, Integer limit, Integer page, String sort, SortDirection direction){
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

      Sort.Direction sortDirection = direction == null ? null : Sort.Direction.valueOf(direction.name());
      Sort sortOptions = Sort.by(sort).direction(sortDirection);
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

   @Override
   @PermitAll
   @WithRoles
   public TestListing summary(String roles, String folder) {
      folder = normalizeFolderName(folder);
      StringBuilder testSql = new StringBuilder();
      // TODO: materialize the counts in a table for quicker lookup
      testSql.append("WITH runs AS (SELECT testid, count(id) as count FROM run WHERE run.trashed = false OR run.trashed IS NULL GROUP BY testid), ");
      testSql.append("datasets AS (SELECT testid, count(id) as count FROM dataset GROUP BY testid) ");
      testSql.append("SELECT test.id,test.name,test.folder,test.description, COALESCE(datasets.count, 0) AS datasets, COALESCE(runs.count, 0) AS runs,test.owner,test.access ");
      testSql.append("FROM test LEFT JOIN runs ON runs.testid = test.id LEFT JOIN datasets ON datasets.testid = test.id");
      boolean anyFolder = "*".equals(folder);
      if (anyFolder) {
         Roles.addRolesSql(identity, "test", testSql, roles, 1, " WHERE");
      } else {
         testSql.append(" WHERE COALESCE(folder, '') = COALESCE((?1)::::text, '')");
         Roles.addRolesSql(identity, "test", testSql, roles, 2, " AND");
      }
      testSql.append(" ORDER BY test.name");
      Query testQuery = em.createNativeQuery(testSql.toString());
      if (anyFolder) {
         Roles.addRolesParam(identity, testQuery, 1, roles);
      } else {
         testQuery.setParameter(1, folder);
         Roles.addRolesParam(identity, testQuery, 2, roles);
      }
      SqlServiceImpl.setResultTransformer(testQuery, Transformers.aliasToBean(TestSummary.class));

      TestListing listing = new TestListing();
      //noinspection unchecked
      listing.tests = testQuery.getResultList();
      return listing;
   }

   private static String normalizeFolderName(String folder) {
      if (folder == null) {
         return null;
      }
      if (folder.endsWith("/")) {
         folder = folder.substring(0, folder.length() - 1);
      }
      folder = folder.trim();
      if (folder.isEmpty()) {
         folder = null;
      }
      return folder;
   }

   @Override
   @PermitAll
   @WithRoles
   public List<String> folders(String roles) {
      StringBuilder sql = new StringBuilder("SELECT DISTINCT folder FROM test");
      Roles.addRolesSql(identity, "test", sql, roles, 1, " WHERE");
      Query query = em.createNativeQuery(sql.toString());
      Roles.addRolesParam(identity, query, 1, roles);
      Set<String> result = new HashSet<>();
      @SuppressWarnings("unchecked")
      List<String> folders = query.getResultList();
      for (String folder : folders) {
         if (folder == null || folder.isEmpty()) {
            continue;
         }
         int index = -1;
         for (;;) {
            index = folder.indexOf('/', index + 1);
            if (index >= 0) {
               result.add(folder.substring(0, index));
            } else {
               result.add(folder);
               break;
            }
         }
      }
      folders = new ArrayList<>(result);
      folders.sort(String::compareTo);
      folders.add(0, null);
      return folders;
   }

   @Override
   @RolesAllowed("tester")
   @WithRoles
   @Transactional
   public int addToken(int testId, TestToken token) {
      if (token.hasUpload() && !token.hasRead()) {
         throw ServiceException.badRequest("Upload permission requires read permission as well.");
      }
      Test test = getTestForUpdate(testId);
      token.id = null; // this is always a new token, ignore -1 in the request
      token.test = test;
      test.tokens.add(token);
      test.persistAndFlush();
      return token.id;
   }

   @Override
   @RolesAllowed("tester")
   @WithRoles
   public Collection<TestToken> tokens(int testId) {
      Test t = Test.findById(testId);
      Hibernate.initialize(t.tokens);
      return t.tokens;
   }

   @Override
   @RolesAllowed("tester")
   @WithRoles
   @Transactional
   public void dropToken(int testId, int tokenId) {
      Test test = getTestForUpdate(testId);
      test.tokens.removeIf(t -> Objects.equals(t.id, tokenId));
      test.persist();
   }

   @Override
   @RolesAllowed("tester")
   @WithRoles
   @Transactional
   // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
   public void updateAccess(int id,
                            String owner,
                            Access access) {
      Query query = em.createNativeQuery(CHANGE_ACCESS);
      query.setParameter(1, owner);
      query.setParameter(2, access.ordinal());
      query.setParameter(3, id);
      if (query.executeUpdate() != 1) {
         throw ServiceException.serverError("Access change failed (missing permissions?)");
      }
   }

   @Override
   @RolesAllowed("tester")
   @WithRoles
   @Transactional
   public int updateView(int testId, View view) {
      if (testId <= 0) {
         throw ServiceException.badRequest("Missing test id");
      }
      try {
         Test test = getTestForUpdate(testId);
         view.ensureLinked();
         view.test = test;
         if (view.id == null || view.id < 0) {
            view.id = null;
            view.persist();
         } else {
            view = em.merge(view);
            int viewId = view.id;
            test.views.removeIf(v -> v.id == viewId);
         }
         test.views.add(view);
         if ("Default".equalsIgnoreCase(view.name)) {
            test.defaultView = view;
         }
         test.persist();
         em.flush();
      } catch (PersistenceException e) {
         log.error("Failed to persist updated view", e);
         throw ServiceException.badRequest("Failed to persist the view.");
      }
      return view.id;
   }

   @Override
   @WithRoles
   @Transactional
   public void deleteView(int testId, int viewId) {
      Test test = getTestForUpdate(testId);
      if (test.defaultView.id == viewId) {
         throw ServiceException.badRequest("Cannot remove default view.");
      }
      if (!test.views.removeIf(v -> v.id == viewId)) {
         throw ServiceException.badRequest("Test does not contain this view!");
      }
      // the orphan removal doesn't work for some reason, we need to remove if manually
      View.deleteById(viewId);
      test.persist();
   }

   @Override
   @RolesAllowed("tester")
   @WithRoles
   @Transactional
   public void updateNotifications(int id,
                                   boolean enabled) {
      Query query = em.createNativeQuery(UPDATE_NOTIFICATIONS)
            .setParameter(1, enabled)
            .setParameter(2, id);
      if (query.executeUpdate() != 1) {
         throw ServiceException.serverError("Access change failed (missing permissions?)");
      }
   }

   @RolesAllowed("tester")
   @WithRoles
   @Transactional
   @Override
   public void updateFolder(int id, String folder) {
      if ("*".equals(folder)) {
         throw new IllegalArgumentException("Illegal folder name '*': this is used as wildcard.");
      }
      Test test = getTestForUpdate(id);
      test.folder = normalizeFolderName(folder);
      test.persist();
   }

   @Override
   @RolesAllowed("tester")
   @WithRoles
   @Transactional
   public Hook updateHook(int testId, Hook hook) {
      if (testId <= 0) {
         throw ServiceException.badRequest("Missing test id");
      }
      // just ensure the test exists
      getTestForUpdate(testId);
      hook.target = testId;

      hookService.checkPrefix(hook);
      if (hook.id == null) {
         hook.persist();
      } else {
         if (!hook.active) {
            Hook.deleteById(hook.id);
            return null;
         } else {
            hook = em.merge(hook);
         }
      }
      em.flush();
      return hook;
   }

   @WithRoles
   @SuppressWarnings("unchecked")
   @Override
   public List<JsonNode> listFingerprints(int testId) {
      return em.createNativeQuery(
            "SELECT DISTINCT fingerprint FROM fingerprint fp " +
            "JOIN dataset ON dataset.id = dataset_id WHERE dataset.testid = ?1")
            .setParameter(1, testId)
            .unwrap(NativeQuery.class).addScalar("fingerprint", JsonNodeBinaryType.INSTANCE)
            .getResultList();
   }

   @WithRoles
   @Override
   public List<JsonNode> listLabelValues(int testId, boolean filtering, boolean metrics) {
      //noinspection unchecked
      return em.createNativeQuery(LABEL_VALUES_QUERY)
            .setParameter(1, testId).setParameter(2, filtering).setParameter(3, metrics)
            .unwrap(NativeQuery.class)
            .addScalar("values", JsonNodeBinaryType.INSTANCE)
            .getResultList();
   }

   @WithRoles
   @Transactional
   @Override
   public void updateTransformers(int testId, List<Integer> transformerIds) {
      if (transformerIds == null) {
         throw ServiceException.badRequest("Null transformer IDs");
      }
      Test test = getTestForUpdate(testId);
      test.transformers.clear();
      test.transformers.addAll(Transformer.list("id IN ?1", transformerIds));
      test.persistAndFlush();
   }

   @WithRoles
   @Transactional
   @Override
   public void updateFingerprint(int testId, FingerprintUpdate update) {
      Test test = getTestForUpdate(testId);
      test.fingerprintLabels = update.labels.stream().reduce(JsonNodeFactory.instance.arrayNode(), ArrayNode::add, ArrayNode::addAll);
      // In case the filter is null we need to force the property to be dirty
      test.fingerprintFilter = "";
      test.fingerprintFilter = update.filter;
      test.persistAndFlush();
   }

   @Override
   @WithRoles
   @Transactional
   public void recalculateDatasets(int testId) {
      Test test = getTestForUpdate(testId);
      RecalculationStatus status = new RecalculationStatus(Run.count("testid = ?1 AND trashed = false", testId));
      // we don't have to care about races with new runs
      RecalculationStatus prev = recalculations.putIfAbsent(testId, status);
      while (prev != null) {
         log.debugf("Recalculation for test %d (%s) already in progress", testId, test.name);
         if (prev.timestamp < System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10)) {
            log.warnf("Recalculation for test %d (%s) from %s timed out after 10 minutes with %d/%d runs",
                  testId, test.name, Instant.ofEpochMilli(prev.timestamp), prev.finished, prev.totalRuns);
            if (recalculations.replace(testId, prev, status)) {
               log.debug("Continuing with recalculation.");
               break;
            } else {
               prev = recalculations.get(testId);
            }
         } else {
            return;
         }
      }
      long deleted = em.createNativeQuery("DELETE FROM dataset USING run WHERE run.id = dataset.runid AND run.trashed AND dataset.testid = ?1")
            .setParameter(1, testId).executeUpdate();
      if (deleted > 0) {
         log.infof("Deleted %d datasets for trashed runs in test %s (%d)", deleted, test.name, testId);
      }

      ScrollableResults results = em.createNativeQuery("SELECT id FROM run WHERE testid = ?1 AND NOT trashed ORDER BY start")
            .setParameter(1, testId)
            .unwrap(NativeQuery.class).setReadOnly(true).setFetchSize(100)
            .scroll(ScrollMode.FORWARD_ONLY);
      while (results.next()) {
         int runId = (int) results.get(0);
         log.infof("Recalculate DataSets for run %d - forcing recalculation for test %d (%s)", runId, testId, test.name);
         // transform will add proper roles anyway
         Util.executeBlocking(vertx, CachedSecurityIdentity.ANONYMOUS, () -> datasetService.withRecalculationLock(() -> {
            int newDatasets = 0;
            try {
               newDatasets = runService.transform(runId, true);
            } finally {
               synchronized (status) {
                  status.finished++;
                  status.datasets += newDatasets;
                  if (status.finished == status.totalRuns) {
                     recalculations.remove(testId, status);
                  }
               }
            }
         }));
      }
   }

   @Override
   @WithRoles
   public RecalculationStatus getRecalculationStatus(int testId) {
      RecalculationStatus status = recalculations.get(testId);
      if (status == null) {
         status = new RecalculationStatus(Run.count("testid = ?1 AND trashed = false", testId));
         status.finished = status.totalRuns;
         status.datasets = DataSet.count("testid", testId);
      }
      return status;
   }

   private Test getTestForUpdate(int testId) {
      Test test = Test.findById(testId);
      if (test == null) {
         throw ServiceException.notFound("Test " + testId + " was not found");
      }
      if (!identity.hasRole(test.owner) || !identity.hasRole(test.owner.substring(0, test.owner.length() - 4) + "tester")) {
         throw ServiceException.forbidden("This user is not an owner/tester for " + test.owner);
      }
      return test;
   }
}
