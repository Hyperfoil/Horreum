package io.hyperfoil.tools.horreum.svc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Tuple;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.ExportedLabelValues;
import io.hyperfoil.tools.horreum.api.data.Fingerprints;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.api.data.TestExport;
import io.hyperfoil.tools.horreum.api.data.datastore.DatastoreType;
import io.hyperfoil.tools.horreum.api.services.TestService;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.entity.alerting.WatchDAO;
import io.hyperfoil.tools.horreum.entity.backend.DatastoreConfigDAO;
import io.hyperfoil.tools.horreum.entity.data.DatasetDAO;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.entity.data.TransformerDAO;
import io.hyperfoil.tools.horreum.entity.data.ViewDAO;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.hyperfoil.tools.horreum.mapper.DatasourceMapper;
import io.hyperfoil.tools.horreum.mapper.TestMapper;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;

@ApplicationScoped
public class TestServiceImpl implements TestService {
    private static final Logger log = Logger.getLogger(TestServiceImpl.class);

    private static final String FILTER_BY_NAME_FIELD = "name";

    protected static final String WILDCARD = "*";
    //using find and replace because  ASC or DESC cannot be set with a parameter
    //@formatter:off
    private static final String CHECK_TEST_EXISTS_BY_ID_QUERY = "SELECT EXISTS(SELECT 1 FROM test WHERE id = ?1)";
    protected static final String LABEL_VALUES_SUMMARY_QUERY = """
         SELECT DISTINCT COALESCE(jsonb_object_agg(label.name, lv.value), '{}'::jsonb) AS values
                  FROM dataset
                  INNER JOIN label_values lv ON dataset.id = lv.dataset_id
                  INNER JOIN label ON label.id = lv.label_id
                  WHERE dataset.testid = :testId AND label.filtering
                  GROUP BY dataset.id, runId
         """;
    //@formatter:on

    @Inject
    @Util.FailUnknownProperties
    ObjectMapper mapper;

    @Inject
    EntityManager em;

    @Inject
    SecurityIdentity identity;

    @Inject
    ServiceMediator mediator;

    @Inject
    LabelValuesService labelValuesService;

    @Inject
    TransactionManager tm;

    private final ConcurrentHashMap<Integer, RecalculationStatus> recalculations = new ConcurrentHashMap<>();

    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    @Override
    public void delete(int id) {
        TestDAO test = TestDAO.findById(id);
        if (test == null) {
            throw ServiceException.notFound("No test with id " + id);
        } else if (!identity.getRoles().contains(test.owner)) {
            throw ServiceException.forbidden("You are not an owner of test " + id);
        }
        log.debugf("Deleting test %s (%d)", test.name, test.id);
        mediator.deleteTest(test.id);
        test.delete();
        if (mediator.testMode())
            Util.registerTxSynchronization(tm,
                    txStatus -> mediator.publishEvent(AsyncEventChannels.TEST_DELETED, test.id, TestMapper.from(test)));
    }

    @Override
    @WithRoles
    @PermitAll
    public Test get(int id) {
        TestDAO test = TestDAO.find("id", id).firstResult();
        if (test == null) {
            throw ServiceException.notFound("No test with name " + id);
        }
        return TestMapper.from(test);
    }

    @Override
    public Test getByNameOrId(String input) {
        TestDAO test = null;
        if (input.matches("-?\\d+")) {
            int id = Integer.parseInt(input);
            // there could be some issue if name is numeric and corresponds to another test id
            test = TestDAO.find("name = ?1 or id = ?2", input, id).firstResult();
        }
        if (test == null) {
            test = TestDAO.find("name", input).firstResult();
        }
        if (test == null) {
            throw ServiceException.notFound("No test with name or id " + input);
        }
        return TestMapper.from(test);
    }

    /**
     * Checks whether the provided id belongs to an existing test and if the user can access it the security check is performed
     * by triggering the RLS at database level
     *
     * @param id test ID
     */
    @WithRoles
    @Transactional
    protected boolean checkTestExists(int id) {
        return (Boolean) em.createNativeQuery(CHECK_TEST_EXISTS_BY_ID_QUERY, Boolean.class)
                .setParameter(1, id)
                .getSingleResult();
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    public TestDAO ensureTestExists(String testNameOrId) {
        TestDAO test;// = TestMapper.to(getByNameOrId(input)); //why does getByNameOrId not work to create the DAO?
        if (testNameOrId.matches("-?\\d+")) {
            int id = Integer.parseInt(testNameOrId);
            test = TestDAO.find("name = ?1 or id = ?2", testNameOrId, id).firstResult();
        } else {
            test = TestDAO.find("name", testNameOrId).firstResult();
        }
        if (test != null) {// we won't return the whole entity with any data
            TestDAO detached = new TestDAO();
            detached.id = test.id;
            detached.owner = test.owner;
            detached.name = testNameOrId;
            detached.backendConfig = test.backendConfig;
            if (Roles.hasRoleWithSuffix(identity, test.owner, "-uploader")) {
                return detached;
            }
            log.debugf("Failed to retrieve test %s as this user (%s = %s) is not uploader for %s",
                    testNameOrId, identity.getPrincipal().getName(), identity.getRoles(), test.owner);
        } else {
            log.debugf("Failed to retrieve test %s - could not find it in the database", testNameOrId);
        }
        // we need to be vague about the test existence
        throw ServiceException.badRequest("Cannot upload to test " + testNameOrId);
    }

    @Override
    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    public Test add(Test dto) {
        if (!identity.hasRole(dto.owner)) {
            throw ServiceException.forbidden("This user does not have the " + dto.owner + " role!");
        }
        if (dto.name == null || dto.name.isBlank())
            throw ServiceException.badRequest("Test name can not be empty");
        log.debugf("Creating new test: %s", dto.toString());
        return TestMapper.from(addAuthenticated(dto));
    }

    TestDAO addAuthenticated(Test dto) {
        TestDAO existing = TestDAO.find("id", dto.id).firstResult();
        if (existing == null)
            dto.clearIds();
        TestDAO test = TestMapper.to(dto);
        if (test.notificationsEnabled == null) {
            test.notificationsEnabled = true;
        }
        test.folder = normalizeFolderName(test.folder);
        checkWildcardFolder(test.folder);
        if (test.transformers != null && !test.transformers.isEmpty())
            verifyTransformersBeforeAdd(test);
        if (existing != null) {
            test.ensureLinked();
            if (!identity.hasRole(existing.owner)) {
                throw ServiceException.forbidden("This user does not have the " + existing.owner + " role!");
            }
            // We're not updating views using this method
            boolean shouldRecalculateLables = false;
            if (!Objects.equals(test.fingerprintFilter, existing.fingerprintFilter) ||
                    !Objects.equals(test.fingerprintLabels, existing.fingerprintLabels))
                shouldRecalculateLables = true;

            test.views = existing.views;
            test = em.merge(test);
            if (shouldRecalculateLables)
                mediator.updateFingerprints(test.id);
        } else {
            // We need to persist the test before view in order for RLS to work
            if (test.views == null || test.views.isEmpty()) {
                test.views = Collections.singleton(new ViewDAO("Default", test));
            }
            try {
                test = em.merge(test);
                em.flush();
            } catch (PersistenceException e) {
                if (e instanceof org.hibernate.exception.ConstraintViolationException) {
                    throw new ServiceException(Response.Status.CONFLICT, "Could not persist test due to another test.");
                } else {
                    throw new WebApplicationException(e, Response.serverError().build());
                }
            }
            mediator.newTest(TestMapper.from(test));
            if (mediator.testMode()) {
                int testId = test.id;
                Test testDTO = TestMapper.from(test);
                Util.registerTxSynchronization(tm,
                        txStatus -> mediator.publishEvent(AsyncEventChannels.TEST_NEW, testId, testDTO));
            }
        }
        return test;
    }

    private void verifyTransformersBeforeAdd(TestDAO test) {
        List<TransformerDAO> tmp = new ArrayList<>();
        for (var t : test.transformers) {
            if (TransformerDAO.findById(t.id) == null) {
                TransformerDAO trans = TransformerDAO.find("targetSchemaUri", t.targetSchemaUri).firstResult();
                if (trans != null)
                    tmp.add(trans);
            } else
                tmp.add(t);
        }
        test.transformers = tmp;
    }

    @Override
    @PermitAll
    @WithRoles
    public TestQueryResult list(String roles, Integer limit, Integer page, String sort, SortDirection direction) {
        PanacheQuery<TestDAO> query;
        StringBuilder whereClause = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // configure roles
        Set<String> actualRoles = null;
        if (Roles.hasRolesParam(roles)) {
            if (roles.equals(Roles.MY_ROLES)) {
                if (!identity.isAnonymous()) {
                    actualRoles = identity.getRoles();
                }
            } else {
                actualRoles = new HashSet<>(Arrays.asList(roles.split(";")));
            }
        }

        if (actualRoles != null && !actualRoles.isEmpty()) {
            whereClause.append("owner IN :owner");
            params.put("owner", actualRoles);
        }

        // configure sorting
        Sort.Direction sortDirection = direction == null ? null : Sort.Direction.valueOf(direction.name());
        Sort sortOptions = sort != null ? Sort.by(sort).direction(sortDirection) : null;

        // create TestDAO query
        if (!whereClause.isEmpty()) {
            query = TestDAO.find(whereClause.toString(), sortOptions, params);
        } else {
            query = TestDAO.findAll(sortOptions);
        }

        // configure paging
        if (limit != null && page != null) {
            query.page(Page.of(page, limit));
        }
        return new TestQueryResult(query.list().stream().map(TestMapper::from).toList(), TestDAO.count());
    }

    @Override
    @PermitAll
    @WithRoles
    public TestListing summary(String roles, String folder, Integer limit, Integer page, SortDirection direction,
            String name) {
        folder = normalizeFolderName(folder);
        StringBuilder testSql = new StringBuilder();
        testSql.append(
                "WITH runs AS (SELECT testid, count(id) as count FROM run WHERE run.trashed = false OR run.trashed IS NULL GROUP BY testid), ");
        testSql.append("datasets AS (SELECT testid, count(id) as count FROM dataset GROUP BY testid) ");
        testSql.append(
                "SELECT test.id,test.name,test.folder,test.description, COALESCE(datasets.count, 0) AS datasets, COALESCE(runs.count, 0) AS runs,test.owner,test.access ");
        testSql.append("FROM test LEFT JOIN runs ON runs.testid = test.id LEFT JOIN datasets ON datasets.testid = test.id");
        boolean anyFolder = WILDCARD.equals(folder);
        if (anyFolder) {
            Roles.addRolesSql(identity, "test", testSql, roles, 1, " WHERE");
        } else {
            testSql.append(" WHERE COALESCE(folder, '') = COALESCE((?1)::text, '')");
            Roles.addRolesSql(identity, "test", testSql, roles, 2, " AND");
        }

        // configure search by
        if (name != null) {
            testSql.append(testSql.toString().contains("WHERE") ? " AND " : " WHERE ")
                    .append("LOWER(")
                    .append(FILTER_BY_NAME_FIELD)
                    .append(") LIKE :searchValue");
        }

        // page set to 0 means return all results, no limits nor ordering
        if (limit > 0 && page > 0) {
            Util.addPaging(testSql, limit, page, "test.name", direction);
        }

        org.hibernate.query.Query<TestSummary> testQuery = em.unwrap(Session.class)
                .createNativeQuery(testSql.toString(), Tuple.class)
                .setTupleTransformer((tuples, aliases) -> new TestSummary((int) tuples[0], (String) tuples[1],
                        (String) tuples[2], (String) tuples[3],
                        (Number) tuples[4], (Number) tuples[5], (String) tuples[6], Access.fromInt((int) tuples[7])));
        if (anyFolder) {
            Roles.addRolesParam(identity, testQuery, 1, roles);
        } else {
            testQuery.setParameter(1, folder);
            Roles.addRolesParam(identity, testQuery, 2, roles);
        }

        if (name != null) {
            testQuery.setParameter("searchValue", "%" + name.toLowerCase() + "%");
        }

        List<TestSummary> summaryList = testQuery.getResultList();
        if (!identity.isAnonymous()) {
            List<Integer> testIdSet = new ArrayList<>();
            Map<Integer, Set<String>> subscriptionMap = new HashMap<>();

            summaryList.forEach(summary -> testIdSet.add(summary.id));
            List<WatchDAO> subscriptions = em.createNativeQuery("SELECT * FROM watch w WHERE w.testid IN (?1)", WatchDAO.class)
                    .setParameter(1, testIdSet).getResultList();
            String username = identity.getPrincipal().getName();
            Set<String> teams = identity.getRoles().stream().filter(role -> role.endsWith("-team")).collect(Collectors.toSet());

            subscriptions.forEach(subscription -> {
                Set<String> subscriptionSet = subscriptionMap.computeIfAbsent(subscription.test.id, k -> new HashSet<>());
                if (subscription.users.contains(username)) {
                    subscriptionSet.add(username);
                }
                if (subscription.optout.contains(username)) {
                    subscriptionSet.add("!" + username);
                }
                subscription.teams.forEach(team -> {
                    if (teams.contains(team)) {
                        subscriptionSet.add(team);
                    }
                });
            });

            summaryList.forEach(
                    summary -> summary.watching = subscriptionMap.computeIfAbsent(summary.id, k -> Collections.emptySet()));
        }

        if (folder == null) {
            folder = "";
        }
        TestListing listing = new TestListing();
        listing.tests = summaryList;
        StringBuilder countQuery = new StringBuilder();
        List<String> ordinals = new ArrayList<>();
        if (anyFolder) {
            Roles.addRoles(identity, countQuery, roles, false, ordinals);
        } else {
            ordinals.add(folder);
            countQuery.append(" COALESCE(folder, '') IN (?1) ");
            Roles.addRoles(identity, countQuery, roles, true, ordinals);
        }
        listing.count = TestDAO.count(countQuery.toString(), ordinals.toArray(new Object[] {}));
        return listing;
    }

    private static String normalizeFolderName(String folder) {
        if (folder == null) {
            return null;
        }
        // cleanup the string from leading and trailing spaces before additional cleanups
        folder = folder.trim();
        if (folder.endsWith("/")) {
            folder = folder.substring(0, folder.length() - 1).trim();
        }

        if (folder.isEmpty()) {
            folder = null;
        }
        return folder;
    }

    /**
     * This will return all distinct folder based on the provided roles plus the root folder which is represented by a null
     * object in the returned list
     *
     * @param roles user roles
     * @return list of distinct strings (the folders)
     */
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
        // this represents the root folder
        folders.add(0, null);
        return folders;
    }

    @Override
    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
    public void updateAccess(int testId, String owner, Access access) {
        TestDAO test = (TestDAO) TestDAO.findByIdOptional(testId)
                .orElseThrow(() -> ServiceException.notFound("Test not found"));

        test.owner = owner;
        test.access = access;
        try {
            // need persistAndFlush otherwise we won't catch SQLGrammarException
            test.persistAndFlush();
        } catch (Exception e) {
            throw ServiceException.serverError("Access change failed (missing permissions?)");
        }
    }

    @Override
    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    public void updateNotifications(int testId, boolean enabled) {
        TestDAO test = (TestDAO) TestDAO.findByIdOptional(testId)
                .orElseThrow(() -> ServiceException.notFound("Test not found"));

        test.notificationsEnabled = enabled;
        try {
            // need persistAndFlush otherwise we won't catch SQLGrammarException
            test.persistAndFlush();
        } catch (Exception e) {
            throw ServiceException.serverError("Notification change failed (missing permissions?)");
        }
    }

    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    @Override
    public void updateFolder(int id, String folder) {
        // normalize the folder before checking the wildcard
        String normalizedFolder = normalizeFolderName(folder);
        checkWildcardFolder(normalizedFolder);
        TestDAO test = getTestForUpdate(id);
        test.folder = normalizedFolder;
        test.persist();
    }

    @WithRoles
    @SuppressWarnings("unchecked")
    @Override
    public List<Fingerprints> listFingerprints(int testId) {
        if (!checkTestExists(testId)) {
            throw ServiceException.serverError("Cannot find test " + testId);
        }
        return Fingerprints.parse(em.createNativeQuery("""
                SELECT DISTINCT fingerprint
                FROM fingerprint fp
                JOIN dataset ON dataset.id = dataset_id
                WHERE dataset.testid = ?1
                """)
                .setParameter(1, testId)
                .unwrap(NativeQuery.class).addScalar("fingerprint", JsonBinaryType.INSTANCE)
                .getResultList());
    }

    /**
     * returns true if the jsonpath input appears to be a predicate jsonpath (always returns true or false) versus a filtering
     * path (returns null or a value)
     *
     * @param input
     * @return
     */
    private boolean isPredicate(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        if (input.matches("\\?.*?@.*?[><]|==")) {// ? (@ [comp]
            return true;
        }
        return false;
    }

    // returns a map of label name to a set of possible label types
    private Map<String, Set<String>> getValueTypes(int testId) {
        Map<String, Set<String>> rtrn = new HashMap<>();
        List<Object[]> rows = em.createNativeQuery(
                """
                        select l.name,jsonb_agg(distinct jsonb_typeof(lv.value))
                        from dataset d
                        left join label_values lv on d.id = lv.dataset_id
                        left join label l on l.id = lv.label_id
                        where d.testid = :testId and l.name is not null group by l.name
                        """, Object[].class)
                .setParameter("testId", testId)
                .unwrap(NativeQuery.class)
                .addScalar("name", StandardBasicTypes.STRING)
                .addScalar("values", JsonBinaryType.INSTANCE)
                .getResultList();

        for (Object[] row : rows) {
            String name = (String) row[0];
            Set<String> set = new HashSet<>();
            ArrayNode types = (ArrayNode) row[1];
            types.forEach(v -> set.add(v.textValue()));
            rtrn.put(name, set);
        }
        return rtrn;
    }

    @Transactional
    @WithRoles
    @Override
    public List<ExportedLabelValues> labelValues(int testId, String filter, String before, String after, boolean filtering,
            boolean metrics, String sort, String direction, Integer limit, int page, List<String> include, List<String> exclude,
            boolean multiFilter) {
        if (!checkTestExists(testId)) {
            throw ServiceException.notFound("Cannot find test " + testId);
        }

        try {
            return labelValuesService.labelValuesByTest(testId, filter, before, after, filtering, metrics, sort, direction,
                    limit,
                    page, include, exclude, multiFilter);
        } catch (IllegalArgumentException e) {
            throw ServiceException.badRequest(e.getMessage());
        }
    }

    @Transactional
    @WithRoles
    @Override
    public List<ObjectNode> filteringLabelValues(
            @PathParam("id") int testId) {

        TestDAO.findByIdOptional(testId).orElseThrow(() -> ServiceException.serverError("Cannot find test " + testId));

        NativeQuery<ObjectNode> query = ((NativeQuery<ObjectNode>) em.createNativeQuery(LABEL_VALUES_SUMMARY_QUERY))
                .setParameter("testId", testId);

        query
                .unwrap(NativeQuery.class)
                .addScalar("values", JsonBinaryType.INSTANCE);
        List<ObjectNode> filters = query.getResultList();

        return filters != null ? filters : new ArrayList<>();
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
        if (prev != null) {
            log.infof("Recalculation for test %d (%s) already in progress", testId, test.name);
            return;
        }

        long deleted = em
                .createNativeQuery(
                        "DELETE FROM dataset USING run WHERE run.id = dataset.runid AND run.trashed AND dataset.testid = ?1")
                .setParameter(1, testId).executeUpdate();
        if (deleted > 0) {
            log.debugf("Deleted %d datasets for trashed runs in test %s (%d)", deleted, test.name, (Object) testId);
        }

        log.infof("Recalculating datasets for test %d (%s)", testId, test.name);
        try (ScrollableResults<Integer> results = em
                .createNativeQuery("SELECT id FROM run WHERE testid = ?1 AND NOT trashed ORDER BY start")
                .setParameter(1, testId)
                .unwrap(NativeQuery.class).setReadOnly(true).setFetchSize(100)
                .scroll(ScrollMode.FORWARD_ONLY)) {
            while (results.next()) {
                int runId = (int) results.get();
                log.debugf("Recalculate Datasets for run %d - forcing recalculation for test %d (%s)", runId, testId,
                        test.name);

                mediator.executeBlocking(() -> {
                    int newDatasets = 0;
                    try {
                        newDatasets = mediator.transform(runId, true);
                    } finally {
                        synchronized (status) {
                            status.finished++;
                            status.datasets += newDatasets;
                            if (status.finished == status.totalRuns) {
                                log.infof("Datasets recalculation for test %d (%s) completed", testId, test.name);
                                recalculations.remove(testId, status);
                            }
                        }
                    }
                });
            }
        }
    }

    @Override
    @WithRoles
    public RecalculationStatus getRecalculationStatus(int testId) {
        if (!checkTestExists(testId)) {
            throw ServiceException.serverError("Cannot find test " + testId);
        }
        RecalculationStatus status = recalculations.get(testId);
        if (status == null) {
            status = new RecalculationStatus(RunDAO.count("testid = ?1 AND trashed = false", testId));
            status.finished = status.totalRuns;
            status.datasets = DatasetDAO.count("testid", testId);
        }
        return status;
    }

    @RolesAllowed({ Roles.ADMIN, Roles.TESTER })
    @WithRoles
    @Transactional
    @Override
    public TestExport export(int testId) {
        TestDAO t = TestDAO.findById(testId);
        if (t == null) {
            throw ServiceException.notFound("Test " + testId + " was not found");
        }
        Test test = TestMapper.from(t);
        TestExport testExport = new TestExport(test);
        //if we have non-postgres backend, we need to add the backendConfig to the export without sensitive data
        if (t.backendConfig != null && t.backendConfig.type != DatastoreType.POSTGRES) {
            testExport.datastore = DatasourceMapper.from(t.backendConfig);
            testExport.datastore.pruneSecrets();
        }
        mediator.exportTest(testExport);
        return testExport;
    }

    @RolesAllowed({ Roles.ADMIN, Roles.TESTER })
    @WithRoles
    @Transactional
    @Override
    public void importTest(ObjectNode node) {

        TestExport newTest;

        try {
            newTest = mapper.convertValue(node, TestExport.class);
        } catch (IllegalArgumentException e) {
            throw ServiceException.badRequest("Failed to parse Test definition: " + e.getMessage());
        }

        //need to add logic for datastore
        if (newTest.datastore != null) {
            //first check if datastore already exists
            boolean exists = DatastoreConfigDAO.findById(newTest.datastore.id) != null;
            DatastoreConfigDAO datastore = DatasourceMapper.to(newTest.datastore);
            if (!exists)
                datastore.id = null;
            datastore.persist();
            if (!exists) {
                newTest.datastore.id = datastore.id;
            }

        }
        Test t = add(newTest);

        if (!Objects.equals(t.id, newTest.id)) {
            newTest.id = t.id;
            newTest.updateRefs();
        }
        mediator.importTestToAll(newTest);
    }

    protected TestDAO getTestForUpdate(int testId) {
        TestDAO test = TestDAO.findById(testId);
        if (test == null) {
            throw ServiceException.notFound("Test " + testId + " was not found");
        }
        if (!identity.hasRole(test.owner)
                || !identity.hasRole(test.owner.substring(0, test.owner.length() - 4) + Roles.TESTER)) {
            throw ServiceException.forbidden("This user is not an owner/tester for " + test.owner);
        }
        return test;
    }

    /**
     * Check if the provided folder matches the WILDCARD, if so throws an error
     *
     * @param folder string folder to check
     * @throws IllegalArgumentException if the folder matches the WILDCARD
     */
    protected void checkWildcardFolder(String folder) {
        if (WILDCARD.equals(folder)) {
            throw new IllegalArgumentException("Illegal folder name '" + WILDCARD + "': this is used as wildcard.");
        }
    }
}
