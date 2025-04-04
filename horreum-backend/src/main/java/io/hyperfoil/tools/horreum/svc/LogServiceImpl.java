package io.hyperfoil.tools.horreum.svc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.hyperfoil.tools.horreum.api.alerting.DatasetLog;
import io.hyperfoil.tools.horreum.api.alerting.TransformationLog;
import io.hyperfoil.tools.horreum.api.data.ActionLog;
import io.hyperfoil.tools.horreum.api.internal.services.LogService;
import io.hyperfoil.tools.horreum.entity.ActionLogDAO;
import io.hyperfoil.tools.horreum.entity.alerting.DatasetLogDAO;
import io.hyperfoil.tools.horreum.entity.alerting.TransformationLogDAO;
import io.hyperfoil.tools.horreum.mapper.ActionLogMapper;
import io.hyperfoil.tools.horreum.mapper.DatasetLogMapper;
import io.hyperfoil.tools.horreum.mapper.TransformationLogMapper;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
@Startup
public class LogServiceImpl implements LogService {

    private static final Instant EPOCH_START = Instant.ofEpochMilli(0);
    private static final Instant FAR_FUTURE = Instant.ofEpochSecond(4 * (long) Integer.MAX_VALUE);

    @ConfigProperty(name = "horreum.transformationlog.max.lifespan")
    String transformationLogMaxLifespan;

    @Inject
    TimeService timeService;

    private Integer withDefault(Integer value, Integer defValue) {
        return value != null ? value : defValue;
    }

    @WithRoles
    @RolesAllowed(Roles.TESTER)
    @Override
    public List<DatasetLog> getDatasetLog(String source, int testId, int level, Integer datasetId, Integer page,
            Integer limit) {
        page = withDefault(page, 0);
        limit = withDefault(limit, 25);
        PanacheQuery<DatasetLogDAO> query;
        if (datasetId == null) {
            query = DatasetLogDAO.find("test.id = ?1 AND source = ?2 AND level >= ?3", Sort.descending("timestamp"), testId,
                    source, level);
        } else {
            query = DatasetLogDAO.find("dataset.id = ?1 AND source = ?2 AND level >= ?3", Sort.descending("timestamp"),
                    datasetId, source, level);
        }
        return query.page(Page.of(page, limit)).list().stream().map(DatasetLogMapper::from).collect(Collectors.toList());
    }

    @Override
    @WithRoles
    @RolesAllowed(Roles.TESTER)
    public long getDatasetLogCount(String source, int testId, int level, Integer datasetId) {
        if (datasetId == null) {
            return DatasetLogDAO.count("test.id = ?1 AND source = ?2 AND level >= ?3", testId, source, level);
        } else {
            return DatasetLogDAO.count("dataset.id = ?1 AND source = ?2 AND level >= ?3", datasetId, source, level);
        }
    }

    @Override
    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    public void deleteDatasetLogs(String source, int testId, Integer datasetId, Long from, Long to) {
        // Not using Instant.MIN/Instant.MAX as Hibernate converts to LocalDateTime internally
        Instant fromTs = from == null ? EPOCH_START : Instant.ofEpochMilli(from);
        Instant toTs = to == null ? FAR_FUTURE : Instant.ofEpochMilli(to);
        long deleted;
        if (datasetId == null) {
            deleted = DatasetLogDAO.delete("test.id = ?1 AND source = ?2 AND timestamp >= ?3 AND timestamp < ?4", testId,
                    source, fromTs, toTs);
        } else {
            deleted = DatasetLogDAO.delete(
                    "test.id = ?1 AND source = ?2 AND timestamp >= ?3 AND timestamp < ?4 AND dataset.id = ?5", testId, source,
                    fromTs, toTs, datasetId);
        }
        Log.debugf("Deleted %d logs for test %s", deleted, testId);
    }

    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Override
    public List<TransformationLog> getTransformationLog(int testId, int level, Integer runId, Integer page, Integer limit) {
        page = withDefault(page, 0);
        limit = withDefault(limit, 25);
        if (runId == null || runId <= 0) {
            List<TransformationLogDAO> logs = TransformationLogDAO
                    .find("test.id = ?1 AND level >= ?2", Sort.descending("timestamp"), testId, level)
                    .page(Page.of(page, limit)).list();
            return logs.stream().map(TransformationLogMapper::from).collect(Collectors.toList());
        } else {
            List<TransformationLogDAO> logs = TransformationLogDAO
                    .find("test.id = ?1 AND level >= ?2 AND run.id = ?3", Sort.descending("timestamp"), testId, level, runId)
                    .page(Page.of(page, limit)).list();
            return logs.stream().map(TransformationLogMapper::from).collect(Collectors.toList());
        }
    }

    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Override
    public long getTransformationLogCount(int testId, int level, Integer runId) {
        if (runId == null || runId <= 0) {
            return TransformationLogDAO.count("test.id = ?1 AND level >= ?2", testId, level);
        } else {
            return TransformationLogDAO.count("test.id = ?1 AND level >= ?2 AND run.id = ?3", testId, level, runId);
        }
    }

    @Override
    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    public void deleteTransformationLogs(int testId, Integer runId, Long from, Long to) {
        // Not using Instant.MIN/Instant.MAX as Hibernate converts to LocalDateTime internally
        Instant fromTs = from == null ? EPOCH_START : Instant.ofEpochMilli(from);
        Instant toTs = to == null ? FAR_FUTURE : Instant.ofEpochMilli(to);
        long deleted;
        if (runId == null || runId <= 0) {
            deleted = TransformationLogDAO.delete("test.id = ?1 AND timestamp >= ?2 AND timestamp < ?3", testId, fromTs, toTs);
        } else {
            deleted = TransformationLogDAO.delete("test.id = ?1 AND run.id = ?2 AND timestamp >= ?3 AND timestamp < ?4", testId,
                    runId, fromTs, toTs);
        }
        Log.debugf("Deleted %d logs for test %d, run %d", deleted, testId, runId == null ? -1 : 0);
    }

    @Override
    @WithRoles
    @RolesAllowed(Roles.TESTER)
    public List<ActionLog> getActionLog(int testId, int level, Integer page, Integer limit) {
        page = withDefault(page, 0);
        limit = withDefault(limit, 25);
        List<ActionLogDAO> logs = ActionLogDAO.find("testId = ?1 AND level >= ?2", Sort.descending("timestamp"), testId, level)
                .page(Page.of(page, limit)).list();
        return logs.stream().map(ActionLogMapper::from).collect(Collectors.toList());
    }

    @Override
    @WithRoles
    @RolesAllowed(Roles.TESTER)
    public long getActionLogCount(int testId, int level) {
        return ActionLogDAO.find("testId = ?1 AND level >= ?2", testId, level).count();
    }

    @Override
    @WithRoles
    @RolesAllowed(Roles.TESTER)
    @Transactional
    public void deleteActionLogs(int testId, Long from, Long to) {
        Instant fromTs = from == null ? EPOCH_START : Instant.ofEpochMilli(from);
        Instant toTs = to == null ? FAR_FUTURE : Instant.ofEpochMilli(to);
        long deleted = ActionLogDAO.delete("testId = ?1 AND timestamp >= ?2 AND timestamp < ?3", testId, fromTs, toTs);
        Log.debugf("Deleted %d logs for test %d", deleted, testId);
    }

    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    public void onTestDelete(int testId) {
        DatasetLogDAO.delete("test.id", testId);
        TransformationLogDAO.delete("test.id", testId);
    }

    @Scheduled(every = "{horreum.transformationlog.check}")
    @WithRoles(extras = Roles.HORREUM_SYSTEM)
    @Transactional
    void checkExpiredTransformationLogs() {
        Duration maxLifespan = Duration.parse(transformationLogMaxLifespan);
        long logsDeleted = TransformationLogDAO.delete("timestamp < ?1", timeService.now().minus(maxLifespan));
        Log.debugf("Deleted %d expired transformation log messages", logsDeleted);
    }
}
