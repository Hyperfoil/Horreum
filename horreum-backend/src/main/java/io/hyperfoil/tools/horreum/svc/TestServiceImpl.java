package io.hyperfoil.tools.horreum.svc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.*;
import io.hyperfoil.tools.horreum.api.data.datastore.Datastore;
import io.hyperfoil.tools.horreum.api.data.datastore.DatastoreType;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.bus.MessageBusChannels;
import io.hyperfoil.tools.horreum.entity.alerting.WatchDAO;
import io.hyperfoil.tools.horreum.entity.backend.DatastoreConfigDAO;
import io.hyperfoil.tools.horreum.entity.data.*;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.hyperfoil.tools.horreum.mapper.DatasourceMapper;
import io.hyperfoil.tools.horreum.mapper.TestMapper;
import io.hyperfoil.tools.horreum.mapper.TestTokenMapper;
import io.hyperfoil.tools.horreum.api.services.TestService;
import io.hyperfoil.tools.horreum.bus.MessageBus;
import io.hyperfoil.tools.horreum.server.EncryptionManager;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.hyperfoil.tools.horreum.server.WithToken;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TestServiceImpl implements TestService {
   private static final Logger log = Logger.getLogger(TestServiceImpl.class);

   private static final String UPDATE_NOTIFICATIONS = "UPDATE test SET notificationsenabled = ? WHERE id = ?";
   private static final String CHANGE_ACCESS = "UPDATE test SET owner = ?, access = ? WHERE id = ?";
   //using find and replace because  ASC or DESC cannot be set with a parameter
   //@formatter:off
   protected static final String LABEL_VALUES_FILTER_CONTAINS_JSON = "where combined.values @> :filter";
   //a solution does exist! https://github.com/spring-projects/spring-data-jpa/issues/2551
   //use @\\?\\? to turn into a @? in the query
   protected static final String LABEL_VALUES_FILTER_MATCHES_NOT_NULL = "where combined.values @\\?\\? CAST( :filter as jsonpath)"; //"jsonb_path_match(combined.values,CAST( :filter as jsonpath))";
   //unused atm because we need to either try both PREDICATE and matching jsonpath or differentiate before sending to the DB
   protected static final String LABEL_VALUES_FILTER_MATCHES_PREDICATE = "where combined.values @@ CAST( :filter as jsonpath)";
   protected static final String LABEL_VALUES_SORT = "";//""jsonb_path_query(combined.values,CAST( :orderBy as jsonpath))";
   protected static final String LABEL_VALUES_QUERY = """
         WITH
         combined as (
         SELECT DISTINCT COALESCE(jsonb_object_agg(label.name, lv.value) FILTER (WHERE label.name IS NOT NULL), '{}'::::jsonb) AS values, runId, dataset.id AS datasetId
                  FROM dataset
                  LEFT JOIN label_values lv ON dataset.id = lv.dataset_id
                  LEFT JOIN label ON label.id = lv.label_id
                  WHERE dataset.testid = :testId
                     AND (label.id IS NULL OR (:filteringLabels AND label.filtering) OR (:metricLabels AND label.metrics))
                  GROUP BY dataset.id, runId
         ) select * from combined FILTER_PLACEHOLDER order by jsonb_path_query(combined.values,CAST( :orderBy as jsonpath)) ASC, combined.runId ASC limit :limit offset :offset
         """;
   /**
    * used to check if an input can be cast to a target type in the db and return any error messages
    * Will return a row of all nulls if the input can be cast to the target type.
    */
   //tried pg_input_is_valid but it just returns boolean, no messages
   protected static final String CHECK_CAST = "select * from pg_input_error_info(:input,:target)";

   //@formatter:on
   @Inject
   ObjectMapper mapper;

   @Inject
   EntityManager em;

   @Inject
   MessageBus messageBus;

   @Inject
   SecurityIdentity identity;

   @Inject
   EncryptionManager encryptionManager;

   @Inject
   ServiceMediator mediator;

   @Inject
   TransactionManager tm;

   private final ConcurrentHashMap<Integer, RecalculationStatus> recalculations = new ConcurrentHashMap<>();

   @RolesAllowed(Roles.TESTER)
   @WithRoles
   @Transactional
   @Override
   public void delete(int id){
      TestDAO test = TestDAO.findById(id);
      if (test == null) {
         throw ServiceException.notFound("No test with id " + id);
      } else if (!identity.getRoles().contains(test.owner)) {
         throw ServiceException.forbidden("You are not an owner of test " + id);
      }
      log.debugf("Deleting test %s (%d)", test.name, test.id);
      mediator.deleteTest(test.id);
      test.delete();
      if(mediator.testMode())
         Util.registerTxSynchronization(tm, txStatus -> messageBus.publish(MessageBusChannels.TEST_DELETED, test.id, TestMapper.from(test)));;
   }

   @Override
   @WithToken
   @WithRoles
   @PermitAll
   public Test get(int id, String token){
      TestDAO test = TestDAO.find("id", id).firstResult();
      if (test == null) {
         throw ServiceException.notFound("No test with name " + id);
      }
      return TestMapper.from(test);
   }

   @Override
   public Test getByNameOrId(String input){
      TestDAO test = null;
      if (input.matches("-?\\d+")) {
         int id = Integer.parseInt(input);
         test =  TestDAO.find("name = ?1 or id = ?2", input, id).firstResult();
      }
      if (test == null) {//intellij is wrong, test is not always null here
         test =  TestDAO.find("name", input).firstResult();
      }
      if (test == null) {
         throw ServiceException.notFound("No test with name or id " + input);
      }
      return TestMapper.from(test);
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   public TestDAO ensureTestExists(String input, String token){
      TestDAO test;// = TestMapper.to(getByNameOrId(input)); //why does getByNameOrId not work to create the DAO?
      if (input.matches("-?\\d+")) {
         int id = Integer.parseInt(input);
         test = TestDAO.find("name = ?1 or id = ?2", input, id).firstResult();
      } else {
         test = TestDAO.find("name", input).firstResult();
      }
      if (test != null) {// we won't return the whole entity with any data
         TestDAO detached = new TestDAO();
         detached.id = test.id;
         detached.owner = test.owner;
         detached.name = input;
         detached.backendConfig = test.backendConfig;
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
      throw ServiceException.badRequest("Cannot upload to test " + input);
   }

   @Override
   @RolesAllowed(Roles.TESTER)
   @WithRoles
   @Transactional
   public Test add(Test dto){
      if (!identity.hasRole(dto.owner)) {
         throw ServiceException.forbidden("This user does not have the " + dto.owner + " role!");
      }
      if(dto.name == null || dto.name.isBlank())
         throw ServiceException.badRequest("Test name can not be empty");
      log.debugf("Creating new test: %s", dto.toString());
      TestDAO test = addAuthenticated(dto);
      Hibernate.initialize(test.tokens);
      return TestMapper.from(test);
   }

   TestDAO addAuthenticated(Test dto) {
      TestDAO existing = TestDAO.find("id", dto.id).firstResult();
      if(existing == null)
         dto.id = null;
      TestDAO test = TestMapper.to(dto);
      if (test.notificationsEnabled == null) {
         test.notificationsEnabled = true;
      }
      test.folder = normalizeFolderName(test.folder);
      if ("*".equals(test.folder)) {
         throw new IllegalArgumentException("Illegal folder name '*': this is used as wildcard.");
      }
      if(test.transformers != null && !test.transformers.isEmpty())
         verifyTransformersBeforeAdd(test);
      if (existing != null) {
         test.ensureLinked();
         if (!identity.hasRole(existing.owner)) {
            throw ServiceException.forbidden("This user does not have the " + existing.owner + " role!");
         }
         // We're not updating views using this method
         boolean shouldRecalculateLables = false;
         if(!Objects.equals(test.fingerprintFilter,existing.fingerprintFilter) ||
                 !Objects.equals(test.fingerprintLabels,existing.fingerprintLabels))
            shouldRecalculateLables = true;

         test.views = existing.views;
         test.tokens = existing.tokens;
         em.merge(test);
         if(shouldRecalculateLables)
           mediator.updateFingerprints(test.id);
      }
      else {
         // We need to persist the test before view in order for RLS to work
         if (test.views == null || test.views.isEmpty()) {
            test.views = Collections.singleton(new ViewDAO("Default", test));
         }
         try {
            em.merge(test);
            em.flush();
         } catch (PersistenceException e) {
            if (e.getCause() instanceof org.hibernate.exception.ConstraintViolationException) {
               throw new ServiceException(Response.Status.CONFLICT, "Could not persist test due to another test.");
            } else {
               throw new WebApplicationException(e, Response.serverError().build());
            }
         }
         mediator.newTest(TestMapper.from(test));
         if(mediator.testMode())
            Util.registerTxSynchronization(tm, txStatus -> messageBus.publish(MessageBusChannels.TEST_NEW, test.id, TestMapper.from(test)));
      }
      return test;
   }
   private void verifyTransformersBeforeAdd(TestDAO test) {
      List<TransformerDAO> tmp = new ArrayList<>();
      for(var t : test.transformers) {
         if(TransformerDAO.findById(t.id) == null) {
            TransformerDAO trans = TransformerDAO.find("targetSchemaUri", t.targetSchemaUri).firstResult();
            if(trans != null)
               tmp.add(trans);
         }
         else
            tmp.add(t);
      }
      test.transformers = tmp;
   }

   @Override
   @PermitAll
   @WithRoles
   public TestQueryResult list(String roles, Integer limit, Integer page, String sort,
                               @DefaultValue("Ascending") SortDirection direction){
      PanacheQuery<TestDAO> query;
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
         query = TestDAO.findAll(sortOptions);
      } else {
         query = TestDAO.find("owner IN ?1", sortOptions, actualRoles);
      }
      if (limit != null && page != null) {
         query.page(Page.of(page, limit));
      }
      return new TestQueryResult( query.list().stream().map(TestMapper::from).collect(Collectors.toList()), TestDAO.count() ) ;
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
      org.hibernate.query.Query<TestSummary> testQuery = em.unwrap(Session.class).createNativeQuery(testSql.toString(), Tuple.class)
              .setTupleTransformer((tuples, aliases) ->
                      new TestSummary((int) tuples[0], (String) tuples[1], (String) tuples[2], (String) tuples[3],
                              (Number) tuples[4], (Number) tuples[5], (String) tuples[6], Access.fromInt((int) tuples[7])));
      if (anyFolder) {
         Roles.addRolesParam(identity, testQuery, 1, roles);
      } else {
         testQuery.setParameter(1, folder);
         Roles.addRolesParam(identity, testQuery, 2, roles);
      }
      List<TestSummary> summaryList = testQuery.getResultList();

      if ( ! identity.isAnonymous() ) {
         List<Integer> testIdSet = new ArrayList<>();
         Map<Integer, Set<String>> subscriptionMap = new HashMap<>();

         summaryList.stream().forEach( summary -> testIdSet.add(summary.id));
         List<WatchDAO> subscriptions = em.createNativeQuery("SELECT * FROM watch w WHERE w.testid IN (?1)", WatchDAO.class).setParameter(1, testIdSet).getResultList();
         String username = identity.getPrincipal().getName();
         Set<String> teams = identity.getRoles().stream().filter(role -> role.endsWith("-team")).collect(Collectors.toSet());

         subscriptions.stream().forEach( subscription -> {
             Set<String> subscriptionSet = subscriptionMap.computeIfAbsent(subscription.test.id, k -> new HashSet<>());
             if (subscription.users.contains(username)) {
               subscriptionSet.add(username);
             }
            if (subscription.optout.contains(username)) {
               subscriptionSet.add("!" + username);
            }
            subscription.teams.stream().forEach( team -> {
                 if (teams.contains(team)) {
                      subscriptionSet.add(team);
                 }
             });
         });

          summaryList.forEach(summary -> summary.watching = subscriptionMap.computeIfAbsent(summary.id, k -> Collections.emptySet()));
      }


      TestListing listing = new TestListing();
      listing.tests = summaryList;
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
      NativeQuery<String> query = em.unwrap(Session.class).createNativeQuery(sql.toString(), String.class);
      Roles.addRolesParam(identity, query, 1, roles);
      Set<String> result = new HashSet<>();
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
   public int addToken(int testId, TestToken dto) {
      if (dto.hasUpload() && !dto.hasRead()) {
         throw ServiceException.badRequest("Upload permission requires read permission as well.");
      }
      TestDAO test = getTestForUpdate(testId);
      TestTokenDAO token = TestTokenMapper.to(dto);
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
      TestDAO t = TestDAO.findById(testId);
      Hibernate.initialize(t.tokens);
      return t.tokens.stream().map(TestTokenMapper::from).collect(Collectors.toList());
   }

   @Override
   @RolesAllowed("tester")
   @WithRoles
   @Transactional
   public void dropToken(int testId, int tokenId) {
      TestDAO test = getTestForUpdate(testId);
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
      try {
         if (query.executeUpdate() != 1) {
            throw ServiceException.serverError("Access change failed (missing permissions?)");
         }
      } catch ( UnauthorizedException ue) {
         throw ServiceException.forbidden("Changing access of Test " + id + " is not permitted. You are not defined as a `tester` for the `"+ owner + "` team. Please update your permissions and try again.");
      }
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
      TestDAO test = getTestForUpdate(id);
      test.folder = normalizeFolderName(folder);
      test.persist();
   }

   @WithRoles
   @SuppressWarnings("unchecked")
   @Override
   public List<Fingerprints> listFingerprints(int testId) {
      Test test = get(testId,null);
      if(test == null){
         throw ServiceException.serverError("Cannot find test "+testId);
      }
      return Fingerprints.parse( em.createNativeQuery("""
            SELECT DISTINCT fingerprint
            FROM fingerprint fp
            JOIN dataset ON dataset.id = dataset_id
            WHERE dataset.testid = ?1
            """)
            .setParameter(1, test.id)
            .unwrap(NativeQuery.class).addScalar("fingerprint", JsonBinaryType.INSTANCE)
            .getResultList());
   }


   /**
    * returns null if no filtering, otherwise returns an object for filtering
    * @param input
    * @return
    */
   private Object getFilterObject(String input){
      if (input == null || input.isBlank()){//not a valid filter
         return null;
      }
      JsonNode filterJson = null;
      try {
          filterJson = new ObjectMapper().readTree(input);
      } catch (JsonProcessingException e) {
          //TODO what to do with this error
      }
      if(filterJson!=null && filterJson.getNodeType() == JsonNodeType.OBJECT) {
         return filterJson;
      }else{
         //TODO validate the jsonpath?
         return input;
      }
   }

   //TODO filter should be a json object not a string but that probably requires lots of work for the api

   /**
    * Wrapper around the result from checking the cast attempt in postgres
    * @param ok true of no errors were found
    * @param message the error message or an empty string if it does not exist
    * @param detail the error details or an empty string if it does not exist
    * @param hint the error hint or an empty string if it does not exist
    */

   public record CheckResult (boolean ok, String message, String detail, String hint) {}
   /**
    * returns true (in the CheckResult) if the input can be cast to the target type in psql, otherwise it is false and details are included
    * @param input
    * @param target
    * @param em
    * @return
    */
   private CheckResult castCheck(String input, String target, EntityManager em){
      List<Object[]> results = em.createNativeQuery(CHECK_CAST).setParameter("input",input).setParameter("target",target)
              .unwrap(NativeQuery.class)
              .addScalar("message",String.class)
              .addScalar("detail",String.class)
             .addScalar("hint",String.class)
               .addScalar("sql_error_code",String.class)
              .getResultList();
      //no results or null result row or no message means it passed. no result and 0 lengh result should not happen but being defensive
      return results.isEmpty() || results.get(0).length == 0 || results.get(0)[0] == null?
              new CheckResult(true,"","","") :
              new CheckResult(
                      false,
                      results.get(0)[0] == null ? "" : results.get(0)[0].toString(),
                      results.get(0)[1] == null ? "" : results.get(0)[1].toString(),
                      results.get(0)[2] == null ? "" : results.get(0)[2].toString()
              );
   }

   /**
    * returns true if the jsonpath input appears to be a predicate jsonpath (always returns true or false) versus a filtering path (returns null or a value)
    * @param input
    * @return
    */
   private boolean isPredicate(String input){
      if(input==null || input.isEmpty()){
         return false;
      }
      if(input.matches("\\?.*?@.*?[><]|==")){// ? (@ [comp]
         return true;
      }
      return false;
   }

   @WithRoles
   @Override
   public List<ExportedLabelValues> listLabelValues(int testId, String filter, boolean filtering, boolean metrics, String sort, String direction, int limit, int page) {
      Test test = get(testId,null);
      if(test == null){
         throw ServiceException.serverError("Cannot find test "+testId);
      }

      Object filterObject = getFilterObject(filter);

      String filterSql = "";
      if(filterObject instanceof JsonNode && ((JsonNode)filterObject).getNodeType() == JsonNodeType.OBJECT){
         filterSql = LABEL_VALUES_FILTER_CONTAINS_JSON;
      }else {
         CheckResult jsonpathResult = castCheck(filter,"jsonpath",em);
         if(jsonpathResult.ok()){
            filterSql = LABEL_VALUES_FILTER_MATCHES_NOT_NULL;
         }else{
            //an attempt to see if the user was trying to provide json
            if(filter!=null && filter.startsWith("{") || filter.endsWith("}")){
               CheckResult jsonbResult = castCheck(filter,"jsonb",em);
               if(!jsonbResult.ok()){
                  //we expect this error, what do we do with it?
               }else{
                  //this would be a surprise and quite a problem :)
               }
            }else{
               //what do we do about the invalid jsonpath?
            }
         }
      }
      if(filterSql.isBlank() && filter != null && !filter.isBlank()){
         //TODO there was an error with the filter, do we return that info to the user?
      }

          String sql = LABEL_VALUES_QUERY
                 .replaceAll("ASC", direction.equalsIgnoreCase("ascending") ? "ASC" : "DESC")
                  .replace("FILTER_PLACEHOLDER",filterSql);
           NativeQuery query =  ((NativeQuery) em.createNativeQuery(sql))
             .setParameter("testId", test.id)
             .setParameter("filteringLabels", filtering)
             .setParameter("metricLabels", metrics)
             ;
           if(!filterSql.isEmpty()){
              if(LABEL_VALUES_FILTER_CONTAINS_JSON.equals(filterSql)){
                 query.setParameter("filter",filterObject,JsonBinaryType.INSTANCE);
              }else{
                 query.setParameter("filter",filter);
              }
           }
             query
                     .setParameter("orderBy", "$")//order
                     .setParameter("limit",Integer.MAX_VALUE)//limit
                     .setParameter("offset",0)//offset
                     .unwrap(NativeQuery.class)
              .addScalar("values", JsonBinaryType.INSTANCE)
              .addScalar("runId",Integer.class)
              .addScalar("datasetId",Integer.class);
           return ExportedLabelValues.parse( query.getResultList() );



   }

   @WithRoles
   @Transactional
   @Override
   public void updateTransformers(int testId, List<Integer> transformerIds) {
      if (transformerIds == null) {
         throw ServiceException.badRequest("Null transformer IDs");
      }
      TestDAO test = getTestForUpdate(testId);
      test.transformers.clear();
      test.transformers.addAll(TransformerDAO.list("id IN ?1", transformerIds));
      test.persistAndFlush();
   }

   @Override
   @WithRoles
   @Transactional
   public void recalculateDatasets(int testId) {
      TestDAO test = getTestForUpdate(testId);
      RecalculationStatus status = new RecalculationStatus(RunDAO.count("testid = ?1 AND trashed = false", testId));
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
         log.debugf("Deleted %d datasets for trashed runs in test %s (%d)", deleted, test.name, (Object)testId);
      }

      ScrollableResults results = em.createNativeQuery("SELECT id FROM run WHERE testid = ?1 AND NOT trashed ORDER BY start")
            .setParameter(1, testId)
            .unwrap(NativeQuery.class).setReadOnly(true).setFetchSize(100)
            .scroll(ScrollMode.FORWARD_ONLY);
      while (results.next()) {
         int runId = (int) results.get();
         log.debugf("Recalculate Datasets for run %d - forcing recalculation for test %d (%s)", runId, testId, test.name);
         // transform will add proper roles anyway
//         messageBus.executeForTest(testId, () -> datasetService.withRecalculationLock(() -> {
//         mediator.executeBlocking(() -> mediator.transform(runId, true));
         mediator.executeBlocking(() -> mediator.withRecalculationLock(() -> {
            int newDatasets = 0;
            try {
               newDatasets = mediator.transform(runId, true);
//               mediator.queueRunRecalculation(runId);
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
      Test test = get(testId,null);
      if(test == null){
         throw ServiceException.serverError("Cannot find test "+testId);
      }
      RecalculationStatus status = recalculations.get(test.id);
      if (status == null) {
         status = new RecalculationStatus(RunDAO.count("testid = ?1 AND trashed = false", test.id));
         status.finished = status.totalRuns;
         status.datasets = DatasetDAO.count("testid", test.id);
      }
      return status;
   }

   @RolesAllowed({Roles.ADMIN, Roles.TESTER})
   @WithRoles
   @Transactional
   @Override
   public TestExport export(int testId) {
      TestDAO t = TestDAO.findById(testId);
      if (t == null) {
         throw ServiceException.notFound("Test " + testId + " was not found");
      }
      Test test = TestMapper.from(t);
      if(!test.tokens.isEmpty())
         test.tokens = null;
      TestExport testExport = new TestExport(test);
      //if we have non-postgres backend, we need to add the backendConfig to the export without sensitive data
      if(t.backendConfig != null && t.backendConfig.type != DatastoreType.POSTGRES) {
         testExport.datastore = DatasourceMapper.from(t.backendConfig);
         testExport.datastore.pruneSecrets();
      }
      mediator.exportTest(testExport);
      return testExport;
   }

   @RolesAllowed({Roles.ADMIN, Roles.TESTER})
   @WithRoles
   @Transactional
   @Override
   public void importTest(ObjectNode node) {
      TestExport newTest = Util.OBJECT_MAPPER.convertValue(node, TestExport.class);
      //need to add logic for datastore
      if(newTest.datastore != null) {
         //first check if datastore already exists
         boolean exists = DatastoreConfigDAO.findById(newTest.datastore.id) != null;
         DatastoreConfigDAO datastore = DatasourceMapper.to(newTest.datastore);
         datastore.persist();
         if(!exists) {
            newTest.datastore.id = datastore.id;
         }

      }
      Test t = add(newTest);

      if(!Objects.equals(t.id, newTest.id)) {
         newTest.id = t.id;
         newTest.updateRefs();
         mediator.importTestToAll(newTest);
      }
   }

   protected TestDAO getTestForUpdate(int testId) {
      TestDAO test = TestDAO.findById(testId);
      if (test == null) {
         throw ServiceException.notFound("Test " + testId + " was not found");
      }
      if (!identity.hasRole(test.owner) || !identity.hasRole(test.owner.substring(0, test.owner.length() - 4) + "tester")) {
         throw ServiceException.forbidden("This user is not an owner/tester for " + test.owner);
      }
      return test;
   }
}
