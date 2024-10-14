package io.hyperfoil.tools.horreum.svc;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.api.alerting.Change;
import io.hyperfoil.tools.horreum.api.alerting.DataPoint;
import io.hyperfoil.tools.horreum.api.data.*;
import io.hyperfoil.tools.horreum.api.services.ExperimentService;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.entity.data.ActionDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.events.DatasetChanges;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.vertx.core.Vertx;

@ApplicationScoped
public class ServiceMediator {
    private static final Logger log = Logger.getLogger(ServiceMediator.class);

    @Inject
    TestServiceImpl testService;

    @Inject
    AlertingServiceImpl alertingService;

    @Inject
    RunServiceImpl runService;

    @Inject
    ReportServiceImpl reportService;

    @Inject
    ExperimentServiceImpl experimentService;

    @Inject
    LogServiceImpl logService;

    @Inject
    SubscriptionServiceImpl subscriptionService;

    @Inject
    ActionServiceImpl actionService;

    @Inject
    NotificationServiceImpl notificationService;

    @Inject
    DatasetServiceImpl datasetService;

    @Inject
    EventAggregator aggregator;

    @Inject
    Vertx vertx;

    @Inject
    SchemaServiceImpl schemaService;

    @Inject
    SecurityIdentity identity;

    @Inject
    @ConfigProperty(name = "horreum.test-mode", defaultValue = "false")
    Boolean testMode;

    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 10000)
    @Channel("dataset-event-out")
    Emitter<Dataset.EventNew> dataSetEmitter;

    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 10000)
    @Channel("run-recalc-out")
    Emitter<Integer> runEmitter;

    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 10000)
    @Channel("schema-sync-out")
    Emitter<Integer> schemaEmitter;

    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 10000)
    @Channel("run-upload-out")
    Emitter<RunUpload> runUploadEmitter;

    private Map<AsyncEventChannels, Map<Integer, BlockingQueue<Object>>> events = new ConcurrentHashMap<>();

    private final ReentrantLock lock = new ReentrantLock();

    public ServiceMediator() {
    }

    void executeBlocking(Runnable runnable) {
        Util.executeBlocking(vertx, runnable);
    }

    boolean testMode() {
        return testMode;
    }

    @Transactional
    void newTest(Test test) {
        actionService.onNewTest(test);
    }

    @Transactional
    void deleteTest(int testId) {
        // runService will call mediator.propagatedDatasetDelete which needs
        // to be completed before we call the other services
        runService.onTestDeleted(testId);
        actionService.onTestDelete(testId);
        alertingService.onTestDeleted(testId);
        experimentService.onTestDeleted(testId);
        logService.onTestDelete(testId);
        reportService.onTestDelete(testId);
        subscriptionService.onTestDelete(testId);
    }

    @Transactional
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    void newRun(Run run) {
        actionService.onNewRun(run);
        alertingService.removeExpected(run);
    }

    @Transactional
    void propagatedDatasetDelete(int datasetId) {
        //make sure to delete the entities that has a reference on dataset first
        alertingService.onDatasetDeleted(datasetId);
        datasetService.deleteDataset(datasetId);
    }

    @Transactional
    void updateLabels(Dataset.LabelsUpdatedEvent event) {
        alertingService.onLabelsUpdated(event);
    }

    void newDataset(Dataset.EventNew eventNew) {
        datasetService.onNewDataset(eventNew);
    }

    @Transactional
    void newChange(Change.Event event) {
        actionService.onNewChange(event);
        aggregator.onNewChange(event);
    }

    @Incoming("dataset-event-in")
    @Blocking(ordered = false, value = "horreum.dataset.pool")
    @ActivateRequestContext
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    public void processDatasetEvents(Dataset.EventNew newEvent) {
        newDataset(newEvent);
        validateDataset(newEvent.datasetId);
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    void queueDatasetEvents(Dataset.EventNew event) {
        dataSetEmitter.send(event);
    }

    @Incoming("run-recalc-in")
    @Blocking(ordered = false, value = "horreum.run.pool")
    @ActivateRequestContext
    public void processRunRecalculation(int runId) {
        runService.transform(runId, true);
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    void queueRunRecalculation(int runId) {
        runEmitter.send(runId);
    }

    @Incoming("schema-sync-in")
    @Blocking(ordered = false, value = "horreum.schema.pool")
    @ActivateRequestContext
    public void processSchemaSync(int schemaId) {
        runService.onNewOrUpdatedSchema(schemaId);
    }

    @Incoming("run-upload-in")
    @Blocking(ordered = false, value = "horreum.run.pool")
    @ActivateRequestContext
    public void processRunUpload(RunUpload runUpload) {
        log.debugf("Run Upload: %d", runUpload.testId);
        runService.persistRun(runUpload);

    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    void queueSchemaSync(int schemaId) {
        schemaEmitter.send(schemaId);
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public void queueRunUpload(String start, String stop, String test, String owner, Access access, String token,
            String schemaUri, String description, JsonNode metadata, JsonNode jsonNode, TestDAO testEntity) {
        RunUpload upload = new RunUpload(start, stop, test, owner, access, token, schemaUri, description, metadata, jsonNode,
                testEntity.id, identity.getRoles());
        runUploadEmitter.send(upload);
    }

    void dataPointsProcessed(DataPoint.DatasetProcessedEvent event) {
        experimentService.onDatapointsCreated(event);
    }

    void missingValuesDataset(MissingValuesEvent event) {
        notificationService.onMissingValues(event);
    }

    void newDatasetChanges(DatasetChanges changes) {
        notificationService.onNewChanges(changes);
    }

    int transform(int runId, boolean isRecalculation) {
        return runService.transform(runId, isRecalculation);
    }

    void withSharedLock(Runnable runnable) {
        lock.lock();
        try {
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

    void newExperimentResult(ExperimentService.ExperimentResult result) {
        actionService.onNewExperimentResult(result);
    }

    void validate(Action dto) {
        actionService.validate(dto);
    }

    void merge(ActionDAO dao) {
        actionService.merge(dao);
    }

    void exportTest(TestExport test) {
        alertingService.exportTest(test);
        actionService.exportTest(test);
        experimentService.exportTest(test);
        subscriptionService.exportSubscriptions(test);
    }

    @Transactional
    void importTestToAll(TestExport test) {
        if (test.variables != null)
            alertingService.importVariables(test);
        if (test.missingDataRules != null)
            alertingService.importMissingDataRules(test);
        if (test.actions != null)
            actionService.importTest(test);
        if (test.experiments != null && !test.experiments.isEmpty())
            experimentService.importTest(test);
        if (test.subscriptions != null)
            subscriptionService.importSubscriptions(test);
    }

    public void updateFingerprints(int testId) {
        datasetService.updateFingerprints(testId);
    }

    public void validateRun(Integer runId) {
        schemaService.validateRunData(runId, null);
    }

    public void validateDataset(Integer datasetId) {
        schemaService.validateDatasetData(datasetId, null);
    }

    public void validateSchema(int schemaId) {
        schemaService.revalidateAll(schemaId);
    }

    public <T> void publishEvent(AsyncEventChannels channel, int testId, T payload) {
        if (testMode) {
            log.debugf("Publishing test %d on %s: %s", testId, channel, payload);
            //        eventBus.publish(channel.name(), new MessageBus.Message(BigInteger.ZERO.longValue(), testId, 0, payload));
            events.putIfAbsent(channel, new HashMap<>());
            BlockingQueue<Object> queue = events.get(channel).computeIfAbsent(testId, k -> new LinkedBlockingQueue<>());
            queue.add(payload);
        } else {
            //no-op
        }
    }

    public <T> BlockingQueue<T> getEventQueue(AsyncEventChannels channel, Integer id) {
        if (testMode) {
            events.putIfAbsent(channel, new HashMap<>());
            BlockingQueue<?> queue = events.get(channel).computeIfAbsent(id, k -> new LinkedBlockingQueue<>());
            return (BlockingQueue<T>) queue;
        } else {
            return null;
        }
    }

    static class RunUpload {
        public String start;
        public String stop;
        public String test;
        public String owner;
        public Access access;
        public String token;
        public String schemaUri;
        public String description;
        public JsonNode metaData;
        public JsonNode payload;
        public Integer testId;
        public Set<String> roles;

        public RunUpload() {
        }

        public RunUpload(String start, String stop, String test, String owner,
                Access access, String token, String schemaUri, String description,
                JsonNode metaData, JsonNode payload, Integer testId,
                Set<String> roles) {
            this.start = start;
            this.stop = stop;
            this.test = test;
            this.owner = owner;
            this.access = access;
            this.token = token;
            this.schemaUri = schemaUri;
            this.description = description;
            this.metaData = metaData;
            this.payload = payload;
            this.testId = testId;
            this.roles = roles;
        }
    }

}
