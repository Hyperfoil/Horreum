package io.hyperfoil.tools.horreum.svc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.api.LogService;
import io.hyperfoil.tools.horreum.entity.ActionLog;
import io.hyperfoil.tools.horreum.entity.alerting.DatasetLog;
import io.hyperfoil.tools.horreum.entity.alerting.TransformationLog;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.vertx.ConsumeEvent;

public class LogServiceImpl implements LogService {
   private static final Logger log = Logger.getLogger(LogServiceImpl.class);
   private static final Instant EPOCH_START = Instant.ofEpochMilli(0);
   private static final Instant FAR_FUTURE = Instant.ofEpochSecond(4 * (long) Integer.MAX_VALUE);

   @ConfigProperty(name = "horreum.transformationlog.max.lifespan")
   String transformationLogMaxLifespan;

   private Integer withDefault(Integer value, Integer defValue) {
      return value != null ? value : defValue;
   }

   @WithRoles
   @RolesAllowed(Roles.TESTER)
   @Override
   public List<DatasetLog> getDatasetLog(String source, int testId, int level, Integer datasetId, Integer page, Integer limit) {
      page = withDefault(page, 0);
      limit = withDefault(limit, 25);
      PanacheQuery<DatasetLog> query;
      if (datasetId == null) {
         query = DatasetLog.find("testId = ?1 AND source = ?2 AND level >= ?3", Sort.descending("timestamp"), testId, source, level);
      } else {
         query = DatasetLog.find("dataset_id = ?1 AND source = ?2 AND level >= ?3", Sort.descending("timestamp"), datasetId, source, level);
      }
      return query.page(Page.of(page, limit)).list();
   }

   @Override
   @WithRoles
   @RolesAllowed(Roles.TESTER)
   public long getDatasetLogCount(String source, int testId, int level, Integer datasetId) {
      if (datasetId == null) {
         return DatasetLog.count("testId = ?1 AND source = ?2 AND level >= ?3", testId, source, level);
      } else {
         return DatasetLog.count("dataset_id = ?1 AND source = ?2 AND level > ?3", datasetId, source, level);
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
         deleted = DatasetLog.delete("testId = ?1 AND source = ?2 AND timestamp >= ?3 AND timestamp < ?4", testId, source, fromTs, toTs);
      } else {
         deleted = DatasetLog.delete("testId = ?1 AND source = ?2 AND timestamp >= ?3 AND timestamp < ?4 AND dataset_id = ?5", testId, source, fromTs, toTs, datasetId);
      }
      log.debugf("Deleted %d logs for test %s", deleted, testId);
   }

   @RolesAllowed(Roles.TESTER)
   @WithRoles
   @Override
   public List<TransformationLog> getTransformationLog(int testId, int level, Integer runId, Integer page, Integer limit) {
      page = withDefault(page, 0);
      limit = withDefault(limit, 25);
      if (runId == null || runId <= 0) {
         return TransformationLog.find("testid = ?1 AND level >= ?2", Sort.descending("timestamp"), testId, level)
               .page(Page.of(page, limit)).list();
      } else {
         return TransformationLog.find("testid = ?1 AND level >= ?2 AND runid = ?3", Sort.descending("timestamp"), testId, level, runId)
               .page(Page.of(page, limit)).list();
      }
   }

   @RolesAllowed(Roles.TESTER)
   @WithRoles
   @Override
   public long getTransformationLogCount(int testId, int level, Integer runId) {
      if (runId == null || runId <= 0) {
         return TransformationLog.count("testid = ?1 AND level >= ?2", testId, level);
      } else {
         return TransformationLog.count("testid = ?1 AND level >= ?2 AND runid = ?3", testId, level, runId);
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
         deleted = TransformationLog.delete("testid = ?1 AND timestamp >= ?2 AND timestamp < ?3", testId, fromTs, toTs);
      } else {
         deleted = TransformationLog.delete("testid = ?1 AND runid = ?2 AND timestamp >= ?3 AND timestamp < ?4", testId, runId, fromTs, toTs);
      }
      log.debugf("Deleted %d logs for test %d, run %d", deleted, testId, runId == null ? -1 : 0);
   }

   @Override
   @WithRoles
   @RolesAllowed(Roles.TESTER)
   public List<ActionLog> getActionLog(int testId, int level, Integer page, Integer limit) {
      page = withDefault(page, 0);
      limit = withDefault(limit, 25);
      return ActionLog.find("testid = ?1 AND level >= ?2", Sort.descending("timestamp"), testId, level)
               .page(Page.of(page, limit)).list();
   }

   @Override
   @WithRoles
   @RolesAllowed(Roles.TESTER)
   public long getActionLogCount(int testId, int level) {
      return ActionLog.find("testid = ?1 AND level >= ?2", testId, level).count();
   }

   @Override
   @WithRoles
   @RolesAllowed(Roles.TESTER)
   @Transactional
   public void deleteActionLogs(int testId, Long from, Long to) {
      Instant fromTs = from == null ? EPOCH_START : Instant.ofEpochMilli(from);
      Instant toTs = to == null ? FAR_FUTURE : Instant.ofEpochMilli(to);
      long deleted = ActionLog.delete("testid = ?1 AND timestamp >= ?2 AND timestamp < ?3", testId, fromTs, toTs);
      log.debugf("Deleted %d logs for test %d", deleted, testId);
   }

   @ConsumeEvent(value = Test.EVENT_DELETED, blocking = true)
   @Transactional
   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   public void onTestDelete(Test test) {
      DatasetLog.delete("testid", test.id);
   }

   @Scheduled(every = "{horreum.transformationlog.check}")
   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   void checkExpiredTransformationLogs() {
      Duration maxLifespan = Duration.parse(transformationLogMaxLifespan);
      long logsDeleted = TransformationLog.delete("timestamp < ?1", Instant.now().minus(maxLifespan));
      log.debugf("Deleted %d expired transformation log messages", logsDeleted);
   }
}
