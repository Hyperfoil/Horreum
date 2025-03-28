package io.hyperfoil.tools.horreum.svc;

import static com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
import static io.hyperfoil.tools.horreum.entity.data.SchemaDAO.QUERY_1ST_LEVEL_BY_RUNID_TRANSFORMERID_SCHEMA_ID;
import static io.hyperfoil.tools.horreum.entity.data.SchemaDAO.QUERY_2ND_LEVEL_BY_RUNID_TRANSFORMERID_SCHEMA_ID;
import static io.hyperfoil.tools.horreum.entity.data.SchemaDAO.QUERY_TRANSFORMER_TARGETS;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import jakarta.persistence.TransactionRequiredException;
import jakarta.persistence.Tuple;
import jakarta.transaction.InvalidTransactionException;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.Dataset;
import io.hyperfoil.tools.horreum.api.data.ExportedLabelValues;
import io.hyperfoil.tools.horreum.api.data.JsonpathValidation;
import io.hyperfoil.tools.horreum.api.data.Run;
import io.hyperfoil.tools.horreum.api.data.ValidationError;
import io.hyperfoil.tools.horreum.api.services.RunService;
import io.hyperfoil.tools.horreum.api.services.SchemaService;
import io.hyperfoil.tools.horreum.api.services.TestService;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.datastore.Datastore;
import io.hyperfoil.tools.horreum.datastore.DatastoreResolver;
import io.hyperfoil.tools.horreum.datastore.DatastoreResponse;
import io.hyperfoil.tools.horreum.entity.PersistentLogDAO;
import io.hyperfoil.tools.horreum.entity.alerting.DataPointDAO;
import io.hyperfoil.tools.horreum.entity.alerting.TransformationLogDAO;
import io.hyperfoil.tools.horreum.entity.data.DatasetDAO;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import io.hyperfoil.tools.horreum.entity.data.SchemaDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.entity.data.TransformerDAO;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.hyperfoil.tools.horreum.mapper.DatasetMapper;
import io.hyperfoil.tools.horreum.mapper.RunMapper;
import io.hyperfoil.tools.horreum.server.RoleManager;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import io.quarkus.runtime.Startup;
import io.quarkus.security.identity.SecurityIdentity;

@ApplicationScoped
@Startup
public class RunServiceImpl implements RunService {
    private static final Logger log = Logger.getLogger(RunServiceImpl.class);

    //@formatter:off
    private static final String FIND_AUTOCOMPLETE = """
        SELECT * FROM (
        SELECT DISTINCT jsonb_object_keys(q) AS key
        FROM run, jsonb_path_query(run.data, ? ::jsonpath) q
        WHERE jsonb_typeof(q) = 'object') AS keys
        WHERE keys.key LIKE CONCAT(?, '%');
    """;
    private static final String FIND_RUNS_WITH_URI = """
        SELECT id, testid
        FROM run
        WHERE NOT trashed
        AND (data->>'$schema' = ?1
        OR (CASE
           WHEN jsonb_typeof(data) = 'object' THEN ?1 IN (SELECT values.value->>'$schema' FROM jsonb_each(data) as values)
           WHEN jsonb_typeof(data) = 'array' THEN ?1 IN (SELECT jsonb_array_elements(data)->>'$schema')
           ELSE false
           END)
        OR (metadata IS NOT NULL AND ?1 IN (SELECT jsonb_array_elements(metadata)->>'$schema'))
        )
    """;

    private static final String UPDATE_DATASET_SCHEMAS = """
        WITH uris AS (
            SELECT jsonb_array_elements(ds.data)->>'$schema' AS uri FROM dataset ds WHERE ds.id = ?1
        ), indexed as (
            SELECT uri, row_number() over () - 1 as index FROM uris
        ) INSERT INTO dataset_schemas(dataset_id, uri, index, schema_id)
            SELECT ?1 as dataset_id, indexed.uri, indexed.index, schema.id FROM indexed JOIN schema ON schema.uri = indexed.uri;
    """;
    //@formatter:on
    private static final String[] CONDITION_SELECT_TERMINAL = { "==", "!=", "<>", "<", "<=", ">", ">=", " " };
    private static final String CHANGE_ACCESS = "UPDATE run SET owner = ?, access = ? WHERE id = ?";
    private static final String SCHEMA_USAGE = "COALESCE(jsonb_agg(jsonb_build_object(" +
            "'id', schema.id, 'uri', rs.uri, 'name', schema.name, 'source', rs.source, " +
            "'type', rs.type, 'key', rs.key, 'hasJsonSchema', schema.schema IS NOT NULL)), '[]')";

    @Inject
    EntityManager em;

    @Inject
    SecurityIdentity identity;

    @Inject
    RoleManager roleManager;

    @Inject
    TransactionManager tm;

    @Inject
    SqlServiceImpl sqlService;

    @Inject
    TestServiceImpl testService;

    @Inject
    LabelValuesService labelValuesService;

    @Inject
    ObjectMapper mapper;

    @Inject
    ServiceMediator mediator;
    @Inject
    DatastoreResolver backendResolver;

    @Inject
    Session session;

    private final ConcurrentHashMap<Integer, TestService.RecalculationStatus> transformations = new ConcurrentHashMap<>();

    @Transactional
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    void onTestDeleted(int testId) {
        log.debugf("Trashing runs for test (%d)", testId);
        ScrollableResults<Integer> results = session.createNativeQuery("SELECT id FROM run WHERE testid = ?1", Integer.class)
                .setParameter(1, testId)
                .setReadOnly(true)
                .setFetchSize(100)
                .scroll(ScrollMode.FORWARD_ONLY);
        while (results.next()) {
            int id = results.get();
            trashDueToTestDeleted(id);
        }
    }

    // plain trash does not have the right privileges and @RolesAllowed would cause ContextNotActiveException
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    void trashDueToTestDeleted(int id) {
        trashInternal(id, true);
    }

    // We cannot run this without a transaction (to avoid timeout) because we have not request going on
    // and EM has to bind its lifecycle either to current request or transaction.
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    @TransactionConfiguration(timeout = 3600) // 1 hour, this may run a long time
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    void onNewOrUpdatedSchema(int schemaId) {
        SchemaDAO schema = SchemaDAO.findById(schemaId);
        if (schema == null) {
            log.errorf("Cannot process schema add/update: cannot load schema %d", schemaId);
            return;
        }
        clearRunAndDatasetSchemas(schemaId);
        findRunsWithUri(schema.uri, (runId, testId) -> {
            log.debugf("Recalculate Datasets for run %d - schema %d (%s) changed", runId, schema.id, schema.uri);
            onNewOrUpdatedSchemaForRun(runId, schema.id);
        });
    }

    void findRunsWithUri(String uri, BiConsumer<Integer, Integer> consumer) {
        try (ScrollableResults<RunFromUri> results = session.createNativeQuery(FIND_RUNS_WITH_URI, Tuple.class)
                .setParameter(1, uri)
                .setTupleTransformer((tuple, aliases) -> {
                    RunFromUri r = new RunFromUri();
                    r.id = (int) tuple[0];
                    r.testId = (int) tuple[1];
                    return r;
                })
                .setFetchSize(100)
                .scroll(ScrollMode.FORWARD_ONLY)) {
            while (results.next()) {
                RunFromUri r = results.get();
                consumer.accept(r.id, r.testId);
            }
        }
    }

    /**
     * Keep the run_schemas table up to date with the associated schemas
     * If `recalculate` is true, trigger the run recalculation as well.
     * This is not required when creating a new run as the datasets will be
     * created automatically by the process, the recalculation is required when updating
     * the Schema
     * @param runId id of the run
     * @param schemaId id of the schema
     */
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    void onNewOrUpdatedSchemaForRun(int runId, int schemaId) {
        updateRunSchemas(runId);

        // clear validation error tables by schemaId
        em.createNativeQuery("DELETE FROM dataset_validationerrors WHERE schema_id = ?1")
                .setParameter(1, schemaId).executeUpdate();
        em.createNativeQuery("DELETE FROM run_validationerrors WHERE schema_id = ?1")
                .setParameter(1, schemaId).executeUpdate();

        Util.registerTxSynchronization(tm, txStatus -> mediator.queueRunRecalculation(runId));
    }

    @Transactional
    void updateRunSchemas(int runId) {
        em.createNativeQuery("SELECT update_run_schemas(?1)::text").setParameter(1, runId).getSingleResult();
    }

    @Transactional
    public void updateDatasetSchemas(int datasetId) {
        em.createNativeQuery(UPDATE_DATASET_SCHEMAS).setParameter(1, datasetId).executeUpdate();
    }

    @Transactional
    void clearRunAndDatasetSchemas(int schemaId) {
        // clear old run and dataset schemas associations
        em.createNativeQuery("DELETE FROM run_schemas WHERE schemaid = ?1")
                .setParameter(1, schemaId).executeUpdate();
        em.createNativeQuery("DELETE FROM dataset_schemas WHERE schema_id = ?1")
                .setParameter(1, schemaId).executeUpdate();
    }

    @PermitAll
    @WithRoles
    @Override
    public RunExtended getRun(int id) {

        RunExtended runExtended = null;

        String extendedData = (String) Util.runQuery(em, "SELECT (to_jsonb(run) || jsonb_build_object(" +
                "'schemas', (SELECT " + SCHEMA_USAGE
                + " FROM run_schemas rs JOIN schema ON rs.schemaid = schema.id WHERE runid = run.id), " +
                "'testname', (SELECT name FROM test WHERE test.id = run.testid), " +
                "'datasets', (SELECT jsonb_agg(id ORDER BY id) FROM dataset WHERE runid = run.id), " +
                "'validationErrors', (SELECT jsonb_agg(jsonb_build_object('schemaId', schema_id, 'error', error)) FROM run_validationerrors WHERE run_id = ?1)"
                +
                "))::text FROM run WHERE id = ?1", id);
        try {
            runExtended = mapper.readValue(extendedData, RunExtended.class);
        } catch (JsonProcessingException e) {
            throw ServiceException.serverError("Could not retrieve extended run");
        }

        return runExtended;
    }

    @WithRoles
    @Override
    public RunSummary getRunSummary(int id) {
        try {
            Query query = em.createNativeQuery("SELECT run.id, run.start, run.stop, run.testid, " +
                    "run.owner, run.access, run.trashed, run.description, run.metadata IS NOT NULL as has_metadata, "
                    +
                    "(SELECT name FROM test WHERE test.id = run.testid) as testname, " +
                    "(SELECT " + SCHEMA_USAGE
                    + " FROM run_schemas rs JOIN schema ON schema.id = rs.schemaid WHERE rs.runid = run.id) as schemas, " +
                    "(SELECT json_agg(id ORDER BY id) FROM dataset WHERE runid = run.id) as datasets, " +
                    "(SELECT jsonb_agg(jsonb_build_object('schemaId', schema_id, 'error', error)) AS errors FROM run_validationerrors WHERE run_id = ?1 GROUP BY run_id) AS validationErrors "
                    +
                    "FROM run where id = ?1").setParameter(1, id);
            initTypes(query);
            return createSummary((Object[]) query.getSingleResult());
        } catch (NoResultException e) {
            throw ServiceException.notFound("Run " + id + " not found");
        }
    }

    @PermitAll
    @WithRoles
    @Override
    public Object getData(int id, String schemaUri) {
        if (schemaUri == null || schemaUri.isEmpty()) {
            return Util.runQuery(em, "SELECT data#>>'{}' from run where id = ?", id);
        } else {
            String sqlQuery = "SELECT (CASE " +
                    "WHEN rs.type = 0 THEN run.data " +
                    "WHEN rs.type = 1 THEN run.data->rs.key " +
                    "ELSE run.data->(rs.key::integer) " +
                    "END)#>>'{}' FROM run JOIN run_schemas rs ON rs.runid = run.id WHERE id = ?1 AND rs.source = 0 AND rs.uri = ?2";
            return Util.runQuery(em, sqlQuery, id, schemaUri);
        }
    }

    //this is nearly identical to TestServiceImpl.labelValues (except the return object)
    //this reads from the dataset table but provides data specific to the run...
    @Override
    public List<ExportedLabelValues> labelValues(int runId, String filter, String sort, String direction, int limit, int page,
            List<String> include, List<String> exclude, boolean multiFilter) {
        Run run = getRun(runId);
        if (run == null) {
            throw ServiceException.notFound("Cannot find run " + runId);
        }

        try {
            return labelValuesService.labelValuesByRun(runId, filter, sort, direction, limit,
                    page, include, exclude, multiFilter);
        } catch (IllegalArgumentException e) {
            throw ServiceException.badRequest(e.getMessage());
        }
    }

    @PermitAll
    @WithRoles
    @Override
    public JsonNode getMetadata(int id, String schemaUri) {
        String result;
        if (schemaUri == null || schemaUri.isEmpty()) {
            result = (String) Util.runQuery(em, "SELECT coalesce((metadata#>>'{}')::jsonb, '{}'::jsonb) from run where id = ?",
                    id);
        } else {
            String sqlQuery = "SELECT run.metadata->(rs.key::integer)#>>'{}' FROM run " +
                    "JOIN run_schemas rs ON rs.runid = run.id WHERE id = ?1 AND rs.source = 1 AND rs.uri = ?2";
            result = (String) Util.runQuery(em, sqlQuery, id, schemaUri);
        }
        try {

            return Util.OBJECT_MAPPER.readTree(result);
        } catch (JsonProcessingException e) {
            throw ServiceException.serverError(e.getMessage());
        }
    }

    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    @Override
    // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
    public void updateAccess(int id, String owner, Access access) {
        int updatedRecords = RunDAO.update("owner = ?1, access = ?2 WHERE id = ?3", owner, access, id);
        if (updatedRecords != 1) {
            throw ServiceException.serverError("Access change failed (missing permissions?)");
        }

        // propagate the same change to all datasets belonging to the run
        DatasetDAO.update("owner = ?1, access = ?2 WHERE run.id = ?3", owner, access, id);
    }

    @RolesAllowed(Roles.UPLOADER)
    @WithRoles
    @Override
    public List<Integer> add(String testNameOrId, String owner, Access access, Run run) {
        if (owner != null) {
            run.owner = owner;
        }
        if (access != null) {
            run.access = access;
        }
        log.debugf("About to add new run to test %s using owner", testNameOrId, owner);
        if (testNameOrId == null || testNameOrId.isEmpty()) {
            if (run.testid == null || run.testid == 0) {
                throw ServiceException.badRequest("No test name or id provided");
            } else
                testNameOrId = run.testid.toString();
        }

        TestDAO test = testService.ensureTestExists(testNameOrId);
        run.testid = test.id;
        RunPersistence runPersistence = addAuthenticated(RunMapper.to(run), test);
        runPersistence.getDatasetIds().forEach(dsId -> {
            DatasetDAO ds = DatasetDAO.findById(dsId);
            if (ds != null) {
                queueDatasetProcessing(ds, false);
            } else {
                Log.warnf("Dataset with id %d not found, cannot process it", dsId);
            }
        });
        return Collections.singletonList(runPersistence.runId);
    }

    @Override
    public Response addRunFromData(String start, String stop, String test, String owner, Access access, String schemaUri,
            String description, String data) {
        return addRunFromData(start, stop, test, owner, access, schemaUri, description, data, null);
    }

    @Override
    public Response addRunFromData(String start, String stop, String test, String owner, Access access, String schemaUri,
            String description, FileUpload data, FileUpload metadata) {
        if (data == null) {
            log.debugf("Failed to upload for test %s with description %s because of missing data.", test, description);
            throw ServiceException.badRequest("No data!");
        } else if (!MediaType.APPLICATION_JSON.equals(data.contentType())) {
            log.debugf("Failed to upload for test %s with description %s because of wrong data content type: %s.", test,
                    description, data.contentType());
            throw ServiceException
                    .badRequest("Part 'data' must use content-type: application/json, currently: " + data.contentType());
        }
        if (metadata != null && !MediaType.APPLICATION_JSON.equals(metadata.contentType())) {
            log.debugf("Failed to upload for test %s with description %s because of wrong metadata content type: %s.", test,
                    description, metadata.contentType());
            throw ServiceException.badRequest(
                    "Part 'metadata' must use content-type: application/json, currently: " + metadata.contentType());
        }
        JsonNode dataNode;
        JsonNode metadataNode = null;
        try {
            dataNode = Util.OBJECT_MAPPER.readTree(data.uploadedFile().toFile());
            if (metadata != null) {
                metadataNode = Util.OBJECT_MAPPER.readTree(metadata.uploadedFile().toFile());
                if (metadataNode.isArray()) {
                    for (JsonNode item : metadataNode) {
                        if (!item.isObject()) {
                            log.debugf(
                                    "Failed to upload for test %s with description %s because of wrong item in metadata: %s.",
                                    test, description, item);
                            throw ServiceException.badRequest("One of metadata elements is not an object!");
                        } else if (!item.has("$schema")) {
                            log.debugf(
                                    "Failed to upload for test %s with description %s because of missing schema in metadata: %s.",
                                    test, description, item);
                            throw ServiceException.badRequest("One of metadata elements is missing a schema!");
                        }
                    }
                } else if (metadataNode.isObject()) {
                    if (!metadataNode.has("$schema")) {
                        log.debugf("Failed to upload for test %s with description %s because of missing schema in metadata.",
                                test, description);
                        throw ServiceException.badRequest("Metadata is missing schema!");
                    }
                    metadataNode = instance.arrayNode().add(metadataNode);
                }
            }
        } catch (IOException e) {
            log.error("Failed to read data/metadata from upload file", e);
            throw ServiceException.badRequest("Provided data/metadata can't be read (JSON encoding problem?)");
        }
        return addRunFromData(start, stop, test, owner, access, schemaUri, description, dataNode.toString(), metadataNode);
    }

    /**
     * Processes and persists a run or multiple runs based on the provided data and metadata. It performs the following steps: -
     * Validates and parses the input data string into a JSON structure. - Resolves the appropriate datastore to handle the run
     * processing. - Handles single or multiple runs based on the datastore's response type. - Persists runs and their
     * associated datasets in the database. - Queues dataset recalculation tasks for further processing.
     *
     * If the response, in the case of datastore, contains more than 10 runs, the processing of the entire run is offloaded to
     * an asynchronous queue. For fewer runs, processing occurs synchronously.
     *
     * @param start the start time for the run
     * @param stop the stop time for the run
     * @param test the name or identifier of the test
     * @param owner the owner of the run
     * @param access the access level for the run
     * @param schemaUri the URI of the schema used for validation
     * @param description a description of the run
     * @param stringData the raw string data to be processed
     * @param metadata additional metadata associated with the run
     * @return a Response indicating the result of the operation, including accepted or rejected status
     * @throws ServiceException if validation or data processing fails
     */
    @RolesAllowed(Roles.UPLOADER)
    @WithRoles
    Response addRunFromData(String start, String stop, String test,
            String owner, Access access,
            String schemaUri, String description,
            String stringData, JsonNode metadata) {
        if (stringData == null) {
            log.debugf("Failed to upload for test %s with description %s because of missing data.", test, description);
            throw ServiceException.badRequest("No data!");
        }
        JsonNode data = null;
        try {
            data = Util.OBJECT_MAPPER.readValue(stringData, JsonNode.class);
        } catch (JsonProcessingException e) {
            throw ServiceException.badRequest("Could not map incoming data to JsonNode: " + e.getMessage());
        }

        Object foundTest = findIfNotSet(test, data);
        String testNameOrId = foundTest == null ? null : foundTest.toString().trim();
        if (testNameOrId == null || testNameOrId.isEmpty()) {
            log.debugf("Failed to upload for test %s with description %s as the test cannot be identified.", test, description);
            throw ServiceException.badRequest("Cannot identify test name.");
        }

        TestDAO testEntity = testService.ensureTestExists(testNameOrId);

        Datastore datastore = backendResolver.getDatastore(testEntity.backendConfig.type);

        DatastoreResponse response = datastore.handleRun(data, metadata, testEntity.backendConfig,
                Optional.ofNullable(schemaUri));

        List<RunPersistence> runs = new ArrayList<>();
        if (datastore.uploadType() == Datastore.UploadType.MUILTI
                && response.payload instanceof ArrayNode) {

            if (response.payload.isEmpty()) {
                // user is trying to upload NO runs
                return Response.status(Response.Status.NO_CONTENT).entity("Datastore query returned no results").build();
            }

            // TODO: can we store the run/datasets and process datasets recalculation async regardless of the number of runs?
            //if we return more than 10 results, offload to async queue to process - this might take a LOOONG time
            if (response.payload.size() > 10) {
                Log.infof("Received more than 10 runs, processing them asynchronously");
                response.payload.forEach(jsonNode -> {
                    mediator.queueRunUpload(start, stop, test, owner, access, schemaUri, description, null, jsonNode,
                            testEntity);
                });
            } else { //process synchronously
                response.payload.forEach(jsonNode -> runs
                        .add(getPersistRun(start, stop, test, owner, access, schemaUri, description, metadata, jsonNode,
                                testEntity)));
            }
        } else {
            runs.add(getPersistRun(start, stop, test, owner, access, schemaUri, description, metadata, response.payload,
                    testEntity));
        }

        if (!runs.isEmpty()) {
            runs.stream().flatMap(runPersistence -> runPersistence.getDatasetIds().stream()).forEach(dsId -> {
                DatasetDAO ds = DatasetDAO.findById(dsId);
                if (ds != null) {
                    queueDatasetProcessing(ds, false);
                } else {
                    Log.warnf("Dataset with id %d not found, cannot process it", dsId);
                }
            });
        }
        // if the request is accepted return 202 with all generated run ids
        // if no run ids, means all run upload have been queued up (datastore scenario)
        return Response.status(Response.Status.ACCEPTED)
                .entity(runs.stream().map(val -> Integer.toString(val.runId)).collect(Collectors.joining(",")))
                .build();
    }

    @Transactional
    void persistRun(ServiceMediator.RunUpload runUpload) {
        runUpload.roles.add("horreum.system");
        roleManager.setRoles(String.join(",", runUpload.roles));
        TestDAO testEntity = TestDAO.findById(runUpload.testId);
        if (testEntity == null) {
            log.errorf("Could not find Test (%d) for Run Upload", runUpload.testId);
            return;
        }
        try {
            RunPersistence run = getPersistRun(runUpload.start, runUpload.stop, runUpload.test,
                    runUpload.owner, runUpload.access, runUpload.schemaUri,
                    runUpload.description, runUpload.metaData, runUpload.payload, testEntity);

            if (run.getRunId() == null) {
                log.errorf("Could not persist Run for Test:  %d", testEntity.name);
            }
        } catch (ServiceException serviceException) {
            log.errorf("Could not persist Run for Test:  %d", testEntity.name, serviceException);

        }
    }

    private RunPersistence getPersistRun(String start, String stop, String test, String owner, Access access,
            String schemaUri, String description, JsonNode metadata, JsonNode data, TestDAO testEntity) {
        Object foundStart = findIfNotSet(start, data);
        Object foundStop = findIfNotSet(stop, data);
        Object foundDescription = findIfNotSet(description, data);

        Instant startInstant = Util.toInstant(foundStart);
        Instant stopInstant = Util.toInstant(foundStop);

        if (startInstant == null) {
            log.debugf("Failed to upload for test %s with description %s; cannot parse start time %s (%s)", test, description,
                    foundStart, start);
            throw ServiceException.badRequest("Cannot parse start time from " + foundStart + " (" + start + ")");
        } else if (stopInstant == null) {
            log.debugf("Failed to upload for test %s with description %s; cannot parse start time %s (%s)", test, description,
                    foundStop, stop);
            throw ServiceException.badRequest("Cannot parse stop time from " + foundStop + " (" + stop + ")");
        }

        if (schemaUri != null && !schemaUri.isEmpty()) {
            if (data.isObject()) {
                ((ObjectNode) data).put("$schema", schemaUri);
            } else if (data.isArray()) {
                data.forEach(node -> {
                    if (node.isObject() && !node.hasNonNull("$schema")) {
                        ((ObjectNode) node).put("$schema", schemaUri);
                    }
                });
            }
        }

        log.debugf("Creating new run for test %s(%d) with description %s", testEntity.name, testEntity.id, foundDescription);

        RunDAO run = new RunDAO();
        run.testid = testEntity.id;
        run.start = startInstant;
        run.stop = stopInstant;
        run.description = foundDescription != null ? foundDescription.toString() : null;
        run.data = data;
        run.metadata = metadata;
        run.owner = owner;
        run.access = access;

        return addAuthenticated(run, testEntity);
    }

    private Object findIfNotSet(String value, JsonNode data) {
        if (value != null && !value.isEmpty()) {
            if (value.startsWith("$.")) {
                return Util.findJsonPath(data, value);
            } else {
                return value;
            }
        } else {
            return null;
        }
    }

    /**
     * Adds a new authenticated run to the database with appropriate ownership and access settings. This method performs the
     * following tasks: - Ensures the run's ID is reset and metadata is correctly handled. - Determines the owner of the run,
     * defaulting to a specific uploader role if no owner is provided. - Validates ownership permissions against the user's
     * roles. - Persists or updates the run in the database and handles related datasets.
     *
     * @param run the RunDAO object containing the run details
     * @param test the TestDAO object containing the test details
     * @return a RunPersistence object containing the persisted run ID and dataset IDs
     * @throws ServiceException if validation fails or persistence encounters an error
     */
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    public RunPersistence addAuthenticated(RunDAO run, TestDAO test) {
        // Id will be always generated anew
        run.id = null;
        //if run.metadata is null on the client, it will be converted to a NullNode, not null...
        if (run.metadata != null && run.metadata.isNull())
            run.metadata = null;

        if (run.owner == null) {
            List<String> uploaders = identity.getRoles().stream().filter(role -> role.endsWith("-uploader"))
                    .collect(Collectors.toList());
            if (uploaders.size() != 1) {
                log.debugf("Failed to upload for test %s: no owner, available uploaders: %s", test.name, uploaders);
                throw ServiceException.badRequest(
                        "Missing owner and cannot select single default owners; this user has these uploader roles: "
                                + uploaders);
            }
            String uploader = uploaders.get(0);
            run.owner = uploader.substring(0, uploader.length() - 9) + "-team";
        } else if (!Objects.equals(test.owner, run.owner) && !identity.getRoles().contains(run.owner)) {
            log.debugf("Failed to upload for test %s: requested owner %s, available roles: %s", test.name, run.owner,
                    identity.getRoles());
            throw ServiceException.badRequest("This user does not have permissions to upload run for owner=" + run.owner);
        }
        if (run.access == null) {
            run.access = Access.PRIVATE;
        }
        log.debugf("Uploading with owner=%s and access=%s", run.owner, run.access);

        try {
            if (run.id == null) {
                em.persist(run);
            } else {
                trashConnectedDatasets(run.id, run.testid);
                em.merge(run);
            }
            em.flush();
        } catch (Exception e) {
            log.error("Failed to persist run.", e);
            throw ServiceException.serverError("Failed to persist run");
        }
        log.debugf("Upload flushed, run ID %d", run.id);

        updateRunSchemas(run.id);
        mediator.newRun(RunMapper.from(run));
        List<Integer> datasetIds = transform(run.id, false);
        if (mediator.testMode())
            Util.registerTxSynchronization(tm,
                    txStatus -> mediator.publishEvent(AsyncEventChannels.RUN_NEW, test.id, RunMapper.from(run)));

        return new RunPersistence(run.id, datasetIds);
    }

    @PermitAll
    @WithRoles
    @Override
    public List<String> autocomplete(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        String jsonpath = query.trim();
        String incomplete = "";
        if (jsonpath.endsWith(".")) {
            jsonpath = jsonpath.substring(0, jsonpath.length() - 1);
        } else {
            int lastDot = jsonpath.lastIndexOf('.');
            if (lastDot > 0) {
                incomplete = jsonpath.substring(lastDot + 1);
                jsonpath = jsonpath.substring(0, lastDot);
            } else {
                incomplete = jsonpath;
                jsonpath = "$.**";
            }
        }
        int conditionIndex = jsonpath.indexOf('@');
        if (conditionIndex >= 0) {
            int conditionSelectEnd = jsonpath.length();
            for (String terminal : CONDITION_SELECT_TERMINAL) {
                int ti = jsonpath.indexOf(terminal, conditionIndex + 1);
                if (ti >= 0) {
                    conditionSelectEnd = Math.min(conditionSelectEnd, ti);
                }
            }
            String conditionSelect = jsonpath.substring(conditionIndex + 1, conditionSelectEnd);
            int queryIndex = jsonpath.indexOf('?');
            if (queryIndex < 0) {
                // This is a shortcut query '@.foo...'
                jsonpath = "$.**" + conditionSelect;
            } else if (queryIndex > conditionIndex) {
                // Too complex query with multiple conditions
                return Collections.emptyList();
            } else {
                while (queryIndex > 0 && Character.isWhitespace(jsonpath.charAt(queryIndex - 1))) {
                    --queryIndex;
                }
                jsonpath = jsonpath.substring(0, queryIndex) + conditionSelect;
            }
        }
        if (!jsonpath.startsWith("$")) {
            jsonpath = "$.**." + jsonpath;
        }
        try {
            NativeQuery<String> findAutocomplete = session.createNativeQuery(FIND_AUTOCOMPLETE, String.class);
            findAutocomplete.setParameter(1, jsonpath);
            findAutocomplete.setParameter(2, incomplete);
            List<String> results = findAutocomplete.getResultList();
            return results.stream().map(option -> option.matches("^[a-zA-Z0-9_-]*$") ? option : "\"" + option + "\"")
                    .collect(Collectors.toList());
        } catch (PersistenceException e) {
            throw ServiceException.badRequest("Failed processing query '" + query + "':\n" + e.getLocalizedMessage());
        }
    }

    @PermitAll
    @WithRoles
    @Override
    public RunsSummary listAllRuns(String query, boolean matchAll, String roles, boolean trashed,
            Integer limit, Integer page, String sort, SortDirection direction) {
        StringBuilder sql = new StringBuilder("SELECT run.id, run.start, run.stop, run.testId, ")
                .append("run.owner, run.access, run.trashed, run.description, ")
                .append("run.metadata IS NOT NULL AS has_metadata, test.name AS testname, ")
                .append("'[]'::jsonb AS schemas, '[]'::jsonb AS datasets, '[]'::jsonb AS validationErrors ")
                .append("FROM run JOIN test ON test.id = run.testId WHERE ");
        String[] queryParts;
        boolean whereStarted = false;
        if (query == null || query.isEmpty()) {
            queryParts = new String[0];
        } else {
            query = query.trim();
            if (query.startsWith("$") || query.startsWith("@")) {
                queryParts = new String[] { query };
            } else {
                queryParts = query.split("([ \t\n,]+)|\\bOR\\b");
            }
            sql.append("(");
            for (int i = 0; i < queryParts.length; ++i) {
                if (i != 0) {
                    sql.append(matchAll ? " AND " : " OR ");
                }
                sql.append("jsonb_path_exists(data, ?").append(i + 1).append(" ::jsonpath)");
                if (queryParts[i].startsWith("$")) {
                    // no change
                } else if (queryParts[i].startsWith("@")) {
                    queryParts[i] = "$.** ? (" + queryParts[i] + ")";
                } else {
                    queryParts[i] = "$.**." + queryParts[i];
                }
            }
            sql.append(")");
            whereStarted = true;
        }

        whereStarted = Roles.addRolesSql(identity, "run", sql, roles, queryParts.length + 1, whereStarted ? " AND" : null)
                || whereStarted;
        if (!trashed) {
            if (whereStarted) {
                sql.append(" AND ");
            }
            sql.append(" trashed = false ");
        }
        Util.addPaging(sql, limit, page, sort, direction);

        NativeQuery<Object[]> sqlQuery = session.createNativeQuery(sql.toString(), Object[].class);
        for (int i = 0; i < queryParts.length; ++i) {
            sqlQuery.setParameter(i + 1, queryParts[i]);
        }

        Roles.addRolesParam(identity, sqlQuery, queryParts.length + 1, roles);

        try {
            List<Object[]> runs = sqlQuery.getResultList();

            RunsSummary summary = new RunsSummary();
            // TODO: total does not consider the query but evaluating all the expressions would be expensive
            summary.total = trashed ? RunDAO.count() : RunDAO.count("trashed = false");
            summary.runs = runs.stream().map(this::createSummary).collect(Collectors.toList());
            return summary;
        } catch (PersistenceException pe) {
            // In case of an error PostgreSQL won't let us execute another query in the same transaction
            try {
                Transaction old = tm.suspend();
                try {
                    for (String jsonpath : queryParts) {
                        JsonpathValidation result = sqlService.testJsonPathInternal(jsonpath);
                        if (!result.valid) {
                            throw new WebApplicationException(Response.status(400).entity(result).build());
                        }
                    }
                } finally {
                    tm.resume(old);
                }
            } catch (InvalidTransactionException | SystemException e) {
                // ignore
            }
            throw new WebApplicationException(pe, 500);
        }
    }

    private void initTypes(Query query) {
        query.unwrap(NativeQuery.class)
                .addScalar("id", StandardBasicTypes.INTEGER)
                .addScalar("start", StandardBasicTypes.INSTANT)
                .addScalar("stop", StandardBasicTypes.INSTANT)
                .addScalar("testid", StandardBasicTypes.INTEGER)
                .addScalar("owner", StandardBasicTypes.TEXT)
                .addScalar("access", StandardBasicTypes.INTEGER)
                .addScalar("trashed", StandardBasicTypes.BOOLEAN)
                .addScalar("description", StandardBasicTypes.TEXT)
                .addScalar("has_metadata", StandardBasicTypes.BOOLEAN)
                .addScalar("testname", StandardBasicTypes.TEXT)
                .addScalar("schemas", JsonBinaryType.INSTANCE)
                .addScalar("datasets", JsonBinaryType.INSTANCE)
                .addScalar("validationErrors", JsonBinaryType.INSTANCE);
    }

    private RunSummary createSummary(Object[] row) {

        RunSummary run = new RunSummary();
        run.id = (int) row[0];
        if (row[1] != null)
            run.start = ((Instant) row[1]);
        if (row[2] != null)
            run.stop = ((Instant) row[2]);
        run.testid = (int) row[3];
        run.owner = (String) row[4];
        run.access = Access.fromInt((int) row[5]);
        run.trashed = (boolean) row[6];
        run.description = (String) row[7];
        run.hasMetadata = (boolean) row[8];
        run.testname = (String) row[9];

        //if we send over an empty JsonNode object it will be a NullNode, that can be cast to a string
        if (row[10] != null && !(row[10] instanceof String)) {
            run.schemas = Util.OBJECT_MAPPER.convertValue(row[10], new TypeReference<List<SchemaService.SchemaUsage>>() {
            });
        }
        //if we send over an empty JsonNode object it will be a NullNode, that can be cast to a string
        if (row[11] != null && !(row[11] instanceof String)) {
            try {
                run.datasets = Util.OBJECT_MAPPER.treeToValue(((ArrayNode) row[11]), Integer[].class);
            } catch (JsonProcessingException e) {
                log.warnf("Could not map datasets to array");
            }
        }
        //if we send over an empty JsonNode object it will be a NullNode, that can be cast to a string
        if (row[12] != null && !(row[12] instanceof String)) {
            try {
                run.validationErrors = Util.OBJECT_MAPPER.treeToValue(((ArrayNode) row[12]), ValidationError[].class);
            } catch (JsonProcessingException e) {
                log.warnf("Could not map validation errors to array");
            }
        }
        return run;
    }

    @PermitAll
    @WithRoles
    @Override
    public RunCount runCount(int testId) {
        RunCount counts = new RunCount();
        counts.total = RunDAO.count("testid = ?1", testId);
        counts.active = RunDAO.count("testid = ?1 AND trashed = false", testId);
        counts.trashed = counts.total - counts.active;
        return counts;
    }

    @PermitAll
    @WithRoles
    @Override
    public RunsSummary listTestRuns(int testId, boolean trashed,
            Integer limit, Integer page, String sort, SortDirection direction) {
        StringBuilder sql = new StringBuilder("WITH schema_agg AS (")
                .append("    SELECT " + SCHEMA_USAGE + " AS schemas, rs.runid ")
                .append("        FROM run_schemas rs JOIN schema ON schema.id = rs.schemaid WHERE rs.testid = ?1 GROUP BY rs.runid")
                .append("), dataset_agg AS (")
                .append("    SELECT runid, jsonb_agg(id ORDER BY id) as datasets FROM dataset WHERE testid = ?1 GROUP BY runid")
                .append("), validation AS (")
                .append("    SELECT run_id, jsonb_agg(jsonb_build_object('schemaId', schema_id, 'error', error)) AS errors FROM run_validationerrors GROUP BY run_id")
                .append(") SELECT run.id, run.start, run.stop, run.testid, run.owner, run.access, run.trashed, run.description, ")
                .append("run.metadata IS NOT NULL AS has_metadata, test.name AS testname, ")
                .append("schema_agg.schemas AS schemas, ")
                .append("COALESCE(dataset_agg.datasets, '[]') AS datasets, ")
                .append("COALESCE(validation.errors, '[]') AS validationErrors FROM run ")
                .append("LEFT JOIN schema_agg ON schema_agg.runid = run.id ")
                .append("LEFT JOIN dataset_agg ON dataset_agg.runid = run.id ")
                .append("LEFT JOIN validation ON validation.run_id = run.id ")
                .append("JOIN test ON test.id = run.testid ")
                .append("WHERE run.testid = ?1 ");
        if (!trashed) {
            sql.append(" AND NOT run.trashed ");
        }
        Util.addOrderBy(sql, sort, direction);
        Util.addLimitOffset(sql, limit, page);
        TestDAO test = TestDAO.find("id", testId).firstResult();
        if (test == null) {
            throw ServiceException.notFound("Cannot find test ID " + testId);
        }
        NativeQuery<Object[]> query = session.createNativeQuery(sql.toString(), Object[].class);
        query.setParameter(1, testId);
        initTypes(query);
        List<Object[]> resultList = query.getResultList();
        RunsSummary summary = new RunsSummary();
        summary.total = trashed ? RunDAO.count("testid = ?1", testId) : RunDAO.count("testid = ?1 AND trashed = false", testId);
        summary.runs = resultList.stream().map(this::createSummary).collect(Collectors.toList());
        return summary;
    }

    @PermitAll
    @WithRoles
    @Override
    public RunsSummary listBySchema(String uri, Integer limit, Integer page, String sort, SortDirection direction) {
        if (uri == null || uri.isEmpty()) {
            throw ServiceException.badRequest("No `uri` query parameter given.");
        }
        StringBuilder sql = new StringBuilder("SELECT run.id, run.start, run.stop, run.testId, ")
                .append("run.owner, run.access, run.trashed, run.description, ")
                .append("run.metadata IS NOT NULL AS has_metadata, test.name AS testname, ")
                .append("'[]'::jsonb AS schemas, '[]'::jsonb AS datasets, '[]'::jsonb AS validationErrors ")
                .append("FROM run_schemas rs JOIN run ON rs.runid = run.id JOIN test ON rs.testid = test.id ")
                .append("WHERE uri = ? AND NOT run.trashed");
        Util.addPaging(sql, limit, page, sort, direction);
        NativeQuery<Object[]> query = session.createNativeQuery(sql.toString(), Object[].class);
        query.setParameter(1, uri);
        initTypes(query);

        List<Object[]> runs = query.getResultList();

        RunsSummary summary = new RunsSummary();
        summary.runs = runs.stream().map(this::createSummary).collect(Collectors.toList());
        summary.total = SchemaDAO.count("uri", uri);
        return summary;
    }

    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    @Override
    public void trash(int id, Boolean isTrashed) {
        trashInternal(id, isTrashed == null || isTrashed);
    }

    private void trashInternal(int id, boolean trashed) {
        RunDAO run = RunDAO.findById(id);
        if (run == null) {
            throw ServiceException.notFound("Run not found: " + id);
        }
        if (run.trashed == trashed && trashed) {
            log.infof("The run %s has already been trashed, doing nothing.", id);
            return;
        }
        if (trashed) {
            trashConnectedDatasets(run.id, run.testid);
            run.trashed = true;
            run.persist();
            if (mediator.testMode())
                Util.registerTxSynchronization(tm,
                        txStatus -> mediator.publishEvent(AsyncEventChannels.RUN_TRASHED, run.testid, id));
        }
        // if the run was trashed because of a deleted test we need to ensure that the test actually exist
        // before we try to recalculate the dataset
        else {
            if (TestDAO.findById(run.testid) != null) {
                run.trashed = false;
                run.persistAndFlush();
                transform(id, true);
                updateRunSchemas(run.id);
            } else
                throw ServiceException.badRequest("Not possible to un-trash a run that's not referenced to a Test");
        }
    }

    private void trashConnectedDatasets(int runId, int testId) {
        //Make sure to remove run_schemas as we've trashed the run
        em.createNativeQuery("DELETE FROM run_schemas WHERE runid = ?1").setParameter(1, runId).executeUpdate();
        List<DatasetDAO> datasets = DatasetDAO.list("run.id", runId);
        log.debugf("Trashing run %d (test %d, %d datasets)", runId, testId, datasets.size());
        for (var dataset : datasets) {
            mediator.propagatedDatasetDelete(dataset.id);
        }
    }

    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    @Override
    public void updateDescription(int id, String description) {
        // FIXME: fetchival stringifies the body into JSON string :-/
        RunDAO run = RunDAO.findById(id);
        if (run == null) {
            throw ServiceException.notFound("Run not found: " + id);
        }
        run.description = description;
        // propagate the same change to all datasets belonging to the run
        DatasetDAO.update("description = ?1 WHERE run.id = ?2", description, run.id);
    }

    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    @Override
    public Map<Integer, String> updateSchema(int id, String path, String schemaUri) {
        // FIXME: fetchival stringifies the body into JSON string :-/
        RunDAO run = RunDAO.findById(id);
        if (run == null) {
            throw ServiceException.notFound("Run not found: " + id);
        }
        String uri = Util.destringify(schemaUri);
        Optional<SchemaDAO> schemaOptional = SchemaDAO.find("uri", uri).firstResultOptional();
        if (schemaOptional.isEmpty()) {
            throw ServiceException.notFound("Schema not found: " + uri);
        }

        // Triggering dirty property on Run
        JsonNode updated = run.data.deepCopy();
        JsonNode item;
        if (updated.isObject()) {
            item = path == null ? updated : updated.path(path);
        } else if (updated.isArray()) {
            if (path == null) {
                throw ServiceException.badRequest("Cannot update root schema in an array.");
            }
            item = updated.get(Integer.parseInt(path));
        } else {
            throw ServiceException.serverError("Cannot update run data with path " + path);
        }
        if (item.isObject()) {
            if (uri != null && !uri.isEmpty()) {
                ((ObjectNode) item).set("$schema", new TextNode(uri));
            } else {
                ((ObjectNode) item).remove("$schema");
            }
        } else {
            throw ServiceException.badRequest(
                    "Cannot update schema at " + (path == null ? "<root>" : path) + " as the target is not an object");
        }
        run.data = updated;
        trashConnectedDatasets(run.id, run.testid);
        run.persist();
        onNewOrUpdatedSchemaForRun(run.id, schemaOptional.get().id);
        Map<Integer, String> schemas = session
                .createNativeQuery("SELECT schemaid AS key, uri AS value FROM run_schemas WHERE runid = ? ORDER BY schemaid",
                        Tuple.class)
                .setParameter(1, run.id)
                .getResultStream()
                .distinct()
                .collect(
                        Collectors.toMap(
                                tuple -> (Integer) tuple.get("key"),
                                tuple -> ((String) tuple.get("value"))));

        em.flush();
        return schemas;
    }

    @WithRoles
    @Transactional
    @Override
    public List<Integer> recalculateDatasets(int runId) {
        log.infof("Transforming run id %d", runId);
        return transform(runId, true);
    }

    @RolesAllowed(Roles.ADMIN)
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    @Override
    public void recalculateAll(String fromStr, String toStr) {
        Instant from = Util.toInstant(fromStr);
        Instant to = Util.toInstant(toStr);

        if (from == null || to == null) {
            throw ServiceException.badRequest("Time range is required");
        } else if (to.isBefore(from)) {
            throw ServiceException.badRequest("Time range is invalid (from > to)");
        }
        long deleted = em.createNativeQuery(
                "DELETE FROM dataset USING run WHERE run.id = dataset.runid AND run.trashed AND run.start BETWEEN ?1 AND ?2")
                .setParameter(1, from).setParameter(2, to).executeUpdate();
        if (deleted > 0) {
            log.debugf("Deleted %d datasets for trashed runs between %s and %s", deleted, from, to);
        }

        ScrollableResults<Recalculate> results = session
                .createNativeQuery("SELECT id, testid FROM run WHERE start BETWEEN ?1 AND ?2 AND NOT trashed ORDER BY start",
                        Recalculate.class)
                .setParameter(1, from).setParameter(2, to)
                .setTupleTransformer((tuples, aliases) -> {
                    Recalculate r = new Recalculate();
                    r.runId = (int) tuples[0];
                    r.testId = (int) tuples[1];
                    return r;
                })
                .setReadOnly(true).setFetchSize(100)
                .scroll(ScrollMode.FORWARD_ONLY);
        while (results.next()) {
            Recalculate r = results.get();
            log.debugf("Recalculate Datasets for run %d - forcing recalculation of all between %s and %s", r.runId, from, to);
            // transform will add proper roles anyway
            //         messageBus.executeForTest(r.testId, () -> datasetService.withRecalculationLock(() -> transform(r.runId, true)));
            Util.registerTxSynchronization(tm, txStatus -> mediator.queueRunRecalculation(r.runId));
        }
    }

    /**
     * Transforms the data for a given run by applying applicable schemas and transformers. It ensures any existing datasets for
     * the run are removed before creating new ones, handles timeouts for ongoing transformations, and creates datasets with the
     * transformed data. If the flag {isRecalculation} is set to true the label values recalculation is performed right away
     * synchronously otherwise it is completely skipped and let to the caller trigger it
     *
     * @param runId the ID of the run to transform
     * @param isRecalculation flag indicating if this is a recalculation
     * @return the list of datasets ids that have been created, or empty list if the run is invalid or not found or already
     * ongoing
     */
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    List<Integer> transform(int runId, boolean isRecalculation) {
        List<Integer> datasetIds = new ArrayList<>();
        if (runId < 1) {
            log.errorf("Transformation parameters error: run %s", runId);
            return datasetIds;
        }

        log.debugf("Transforming run ID %d, recalculation? %s", runId, Boolean.toString(isRecalculation));

        // check whether there is an ongoing transformation on the same runId
        TestService.RecalculationStatus status = new TestService.RecalculationStatus(1);
        TestService.RecalculationStatus prev = transformations.putIfAbsent(runId, status);
        // ensure the transformation is removed, with this approach we should be sure
        // it gets removed even if transaction-level exception occurs, e.g., timeout
        Util.registerTxSynchronization(tm, txStatus -> transformations.remove(runId, status));
        if (prev != null) {
            // there is an ongoing transformation that has recently been initiated
            log.warnf("Transformation for run %d already in progress", runId);
            return datasetIds;
        }

        // We need to make sure all old datasets are gone before creating new; otherwise we could
        // break the runid,ordinal uniqueness constraint
        for (DatasetDAO old : DatasetDAO.<DatasetDAO> list("run.id", runId)) {
            for (DataPointDAO dp : DataPointDAO.<DataPointDAO> list("dataset.id", old.getInfo().id)) {
                dp.delete();
            }
            mediator.propagatedDatasetDelete(old.id);
        }

        RunDAO run = RunDAO.findById(runId);
        if (run == null) {
            log.errorf("Cannot load run ID %d for transformation", runId);
            return datasetIds; // this is still empty
        }
        Map<Integer, JsonNode> transformerResults = new TreeMap<>();
        // naked nodes (those produced by implicit identity transformers) are all added to each dataset
        List<JsonNode> nakedNodes = new ArrayList<>();

        List<Object[]> relevantSchemas = unchecked(em.createNamedQuery(QUERY_TRANSFORMER_TARGETS)
                .setParameter(1, run.id)
                .unwrap(NativeQuery.class)
                .addScalar("type", StandardBasicTypes.INTEGER)
                .addScalar("key", StandardBasicTypes.TEXT)
                .addScalar("transformer_id", StandardBasicTypes.INTEGER)
                .addScalar("uri", StandardBasicTypes.TEXT)
                .addScalar("source", StandardBasicTypes.INTEGER)
                .getResultList());

        int schemasAndTransformers = relevantSchemas.size();
        for (Object[] relevantSchema : relevantSchemas) {
            int type = (int) relevantSchema[0];
            String key = (String) relevantSchema[1];
            Integer transformerId = (Integer) relevantSchema[2];
            String uri = (String) relevantSchema[3];
            Integer source = (Integer) relevantSchema[4];

            TransformerDAO t;
            if (transformerId != null) {
                t = TransformerDAO.findById(transformerId);
                if (t == null) {
                    log.errorf("Missing transformer with ID %d", transformerId);
                }
            } else {
                t = null;
            }
            if (t != null) {
                JsonNode root = JsonNodeFactory.instance.objectNode();
                JsonNode result;
                if (t.extractors != null && !t.extractors.isEmpty()) {
                    List<Object[]> extractedData;
                    try {
                        if (type == SchemaDAO.TYPE_1ST_LEVEL) {
                            // note: metadata always follow the 2nd level format
                            extractedData = unchecked(em.createNamedQuery(QUERY_1ST_LEVEL_BY_RUNID_TRANSFORMERID_SCHEMA_ID)
                                    .setParameter(1, run.id).setParameter(2, transformerId)
                                    .unwrap(NativeQuery.class)
                                    .addScalar("name", StandardBasicTypes.TEXT)
                                    .addScalar("value", JsonBinaryType.INSTANCE)
                                    .getResultList());
                        } else {
                            extractedData = unchecked(em.createNamedQuery(QUERY_2ND_LEVEL_BY_RUNID_TRANSFORMERID_SCHEMA_ID)
                                    .setParameter(1, run.id).setParameter(2, transformerId)
                                    .setParameter(3, type == SchemaDAO.TYPE_2ND_LEVEL ? key : Integer.parseInt(key))
                                    .setParameter(4, source)
                                    .unwrap(NativeQuery.class)
                                    .addScalar("name", StandardBasicTypes.TEXT)
                                    .addScalar("value", JsonBinaryType.INSTANCE)
                                    .getResultList());
                        }
                    } catch (PersistenceException e) {
                        logMessage(run, PersistentLogDAO.ERROR,
                                "Failed to extract data (JSONPath expression error?): " + Util.explainCauses(e));
                        findFailingExtractor(runId);
                        extractedData = Collections.emptyList();
                    }
                    addExtracted((ObjectNode) root, extractedData);
                }
                // In Horreum it's customary that when a single extractor is used we pass the result directly to the function
                // without wrapping it in an extra object.
                if (t.extractors != null && t.extractors.size() == 1) {
                    if (root.size() != 1) {
                        // missing results should be null nodes
                        log.errorf("Unexpected result for single extractor: %s", root.toPrettyString());
                    } else {
                        root = root.iterator().next();
                    }
                }
                logMessage(run, PersistentLogDAO.DEBUG,
                        "Run transformer %s/%s with input: <pre>%s</pre>, function: <pre>%s</pre>",
                        uri, t.name, limitLength(root.toPrettyString()), t.function);
                if (t.function != null && !t.function.isBlank()) {
                    result = Util.evaluateOnce(t.function, root, Util::convertToJson,
                            (code, e) -> logMessage(run, PersistentLogDAO.ERROR,
                                    "Evaluation of transformer %s/%s failed: '%s' Code: <pre>%s</pre>", uri, t.name,
                                    e.getMessage(), code),
                            output -> logMessage(run, PersistentLogDAO.DEBUG,
                                    "Output while running transformer %s/%s: <pre>%s</pre>", uri, t.name, output));
                    if (result == null) {
                        // this happens upon error
                        result = JsonNodeFactory.instance.nullNode();
                    }
                } else {
                    result = root;
                }
                if (t.targetSchemaUri != null) {
                    if (result.isObject()) {
                        putIfAbsent(run, t.targetSchemaUri, (ObjectNode) result);
                    } else if (result.isArray()) {
                        ArrayNode array = (ArrayNode) result;
                        for (JsonNode node : array) {
                            if (node.isObject()) {
                                putIfAbsent(run, t.targetSchemaUri, (ObjectNode) node);
                            }
                        }
                    } else {
                        result = instance.objectNode()
                                .put("$schema", t.targetSchemaUri).set("value", result);
                    }
                } else if (!result.isContainerNode() || (result.isObject() && !result.has("$schema")) ||
                        (result.isArray()
                                && StreamSupport.stream(result.spliterator(), false)
                                        .anyMatch(item -> !item.has("$schema")))) {
                    logMessage(run, PersistentLogDAO.WARN, "Dataset will contain element without a schema.");
                }
                JsonNode existing = transformerResults.get(transformerId);
                if (existing == null) {
                    transformerResults.put(transformerId, result);
                } else if (existing.isArray()) {
                    if (result.isArray()) {
                        ((ArrayNode) existing).addAll((ArrayNode) result);
                    } else {
                        ((ArrayNode) existing).add(result);
                    }
                } else {
                    if (result.isArray()) {
                        ((ArrayNode) result).insert(0, existing);
                        transformerResults.put(transformerId, result);
                    } else {
                        transformerResults.put(transformerId, instance.arrayNode().add(existing).add(result));
                    }
                }
            } else {
                JsonNode node;
                JsonNode sourceNode = source == 0 ? run.data : run.metadata;
                switch (type) {
                    case SchemaDAO.TYPE_1ST_LEVEL:
                        node = sourceNode;
                        break;
                    case SchemaDAO.TYPE_2ND_LEVEL:
                        node = sourceNode.path(key);
                        break;
                    case SchemaDAO.TYPE_ARRAY_ELEMENT:
                        node = sourceNode.path(Integer.parseInt(key));
                        break;
                    default:
                        throw new IllegalStateException("Unknown type " + type);
                }
                nakedNodes.add(node);
                logMessage(run, PersistentLogDAO.DEBUG,
                        "This test (%d) does not use any transformer for schema %s (key %s), passing as-is.", run.testid,
                        uri,
                        key);
            }
        }
        if (schemasAndTransformers > 0) {
            int max = transformerResults.values().stream().filter(JsonNode::isArray).mapToInt(JsonNode::size).max()
                    .orElse(1);

            for (int position = 0; position < max; position += 1) {
                ArrayNode all = instance.arrayNode(max + nakedNodes.size());
                for (var entry : transformerResults.entrySet()) {
                    JsonNode node = entry.getValue();
                    if (node.isObject()) {
                        all.add(node);
                    } else if (node.isArray()) {
                        if (position < node.size()) {
                            all.add(node.get(position));
                        } else {
                            String message = String.format(
                                    "Transformer %d produced an array of %d elements but other transformer " +
                                            "produced %d elements; dataset %d/%d might be missing some data.",
                                    entry.getKey(), node.size(), max, run.id, datasetIds.size());
                            logMessage(run, PersistentLogDAO.WARN, "%s", message);
                            log.warnf(message);
                        }
                    } else {
                        logMessage(run, PersistentLogDAO.WARN, "Unexpected result provided by one of the transformers: %s",
                                node);
                        log.warnf("Unexpected result provided by one of the transformers: %s", node);
                    }
                }
                nakedNodes.forEach(all::add);
                DatasetDAO ds = new DatasetDAO(run, datasetIds.size(), run.description, all);
                datasetIds.add(createDataset(ds, isRecalculation));
            }
        } else {
            logMessage(run, PersistentLogDAO.INFO, "No applicable schema, dataset will be empty.");
            DatasetDAO ds = new DatasetDAO(
                    run, 0, "Empty Dataset for run data without any schema.",
                    instance.arrayNode());
            datasetIds.add(createDataset(ds, isRecalculation));
        }
        mediator.validateRun(run.id);
        return datasetIds;
    }

    /**
     * Persists a dataset, optionally triggers recalculation events, and validates the dataset. The recalculation is getting
     * triggered sync only if the {isRecalculation} is set to true otherwise it is completely skipped
     *
     * @param ds the DatasetDAO object to be persisted
     * @param isRecalculation whether the dataset is a result of recalculation
     * @return the ID of the persisted dataset
     */
    private Integer createDataset(DatasetDAO ds, boolean isRecalculation) {
        ds.persistAndFlush();
        // re-create the dataset_schemas associations
        updateDatasetSchemas(ds.id);

        if (isRecalculation) {
            try {
                Dataset.EventNew event = new Dataset.EventNew(DatasetMapper.from(ds), true);
                mediator.onNewDataset(event);
                if (mediator.testMode())
                    Util.registerTxSynchronization(tm,
                            txStatus -> mediator.publishEvent(AsyncEventChannels.DATASET_NEW, ds.testid,
                                    event));
            } catch (TransactionRequiredException tre) {
                log.error(
                        "Failed attempt to persist and send Dataset event during inactive Transaction. Likely due to prior error.",
                        tre);
            }
        }
        mediator.validateDataset(ds.id);
        return ds.id;
    }

    private String limitLength(String str) {
        return str.length() > 1024 ? str.substring(0, 1024) + "...(truncated)" : str;
    }

    private void queueDatasetProcessing(DatasetDAO ds, boolean isRecalculation) {
        mediator.queueDatasetEvents(new Dataset.EventNew(DatasetMapper.from(ds), isRecalculation));
        if (mediator.testMode())
            Util.registerTxSynchronization(tm, txStatus -> mediator.publishEvent(AsyncEventChannels.DATASET_NEW, ds.testid,
                    new Dataset.EventNew(DatasetMapper.from(ds), isRecalculation)));
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void logMessage(RunDAO run, int level, String format, Object... args) {
        String msg = args.length > 0 ? String.format(format, args) : format;
        new TransformationLogDAO(em.getReference(TestDAO.class, run.testid), run, level, msg).persist();
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void findFailingExtractor(int runId) {
        List<Object[]> extractors = session.createNativeQuery(
                "SELECT rs.uri, rs.type, rs.key, t.name, te.name AS extractor_name, te.jsonpath FROM run_schemas rs " +
                        "JOIN transformer t ON t.schema_id = rs.schemaid AND t.id IN (SELECT transformer_id FROM test_transformers WHERE test_id = rs.testid) "
                        +
                        "JOIN transformer_extractors te ON te.transformer_id = t.id " +
                        "WHERE rs.runid = ?1",
                Object[].class).setParameter(1, runId).getResultList();
        for (Object[] row : extractors) {
            try {
                int type = (int) row[1];
                // actual result of query is ignored
                if (type == SchemaDAO.TYPE_1ST_LEVEL) {
                    em.createNativeQuery(
                            "SELECT jsonb_path_query_first(data, (?1)::jsonpath)#>>'{}' FROM dataset WHERE id = ?2")
                            .setParameter(1, row[5]).setParameter(2, runId).getSingleResult();
                } else {
                    em.createNativeQuery(
                            "SELECT jsonb_path_query_first(data -> (?1), (?2)::jsonpath)#>>'{}' FROM dataset WHERE id = ?3")
                            .setParameter(1, type == SchemaDAO.TYPE_2ND_LEVEL ? row[2] : Integer.parseInt((String) row[2]))
                            .setParameter(2, row[5])
                            .setParameter(3, runId).getSingleResult();
                }
            } catch (PersistenceException e) {
                logMessage(em.getReference(RunDAO.class, runId), PersistentLogDAO.ERROR,
                        "There seems to be an error in schema <code>%s</code> transformer <code>%s</code>, extractor <code>%s</code>, JSONPath expression <code>%s</code>: %s",
                        row[0], row[3], row[4], row[5], Util.explainCauses(e));
                return;
            }
        }
        logMessage(em.getReference(RunDAO.class, runId), PersistentLogDAO.DEBUG,
                "We thought there's an error in one of the JSONPaths but independent validation did not find any problems.");
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> unchecked(@SuppressWarnings("rawtypes") List list) {
        return (List<Object[]>) list;
    }

    private void addExtracted(ObjectNode root, List<Object[]> resultSet) {
        for (Object[] labelValue : resultSet) {
            String name = (String) labelValue[0];
            JsonNode value = (JsonNode) labelValue[1];
            root.set(name, value);
        }
    }

    private void putIfAbsent(RunDAO run, String uri, ObjectNode node) {
        if (uri != null && !uri.isBlank() && node != null) {
            if (node.path("$schema").isMissingNode()) {
                node.put("$schema", uri);
            } else {
                logMessage(run, PersistentLogDAO.DEBUG, "<code>$schema</code> present (%s), not overriding with %s",
                        node.path("$schema").asText(), uri);
            }
        }
    }

    static class Recalculate {
        private int runId;
        private int testId;
    }

    static class RunFromUri {
        private int id;
        private int testId;
    }

    /**
     * Represents the result of persisting a run, including the run ID and associated dataset IDs. This class is used to
     * encapsulate the ID of the newly persisted run and the IDs of the datasets connected to the run, providing a structured
     * way to return this data.
     */
    public static class RunPersistence {
        private final Integer runId;
        private final List<Integer> datasetIds;

        public RunPersistence(Integer runId, List<Integer> dsIds) {
            this.runId = runId;
            this.datasetIds = dsIds;
        }

        public Integer getRunId() {
            return runId;
        }

        public List<Integer> getDatasetIds() {
            return datasetIds;
        }
    }
}
