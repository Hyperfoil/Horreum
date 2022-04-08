package io.hyperfoil.tools.horreum.svc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.transaction.Transactional;

import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.api.LogService;
import io.hyperfoil.tools.horreum.entity.alerting.DatasetLog;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.vertx.ConsumeEvent;

public class LogServiceImpl implements LogService {
   private static final Logger log = Logger.getLogger(LogServiceImpl.class);

   @WithRoles
   @RolesAllowed(Roles.TESTER)
   @Override
   public List<DatasetLog> getDatasetLog(String source, Integer testId, Integer page, Integer limit) {
      if (testId == null) {
         return Collections.emptyList();
      }
      if (page == null) {
         page = 0;
      }
      if (limit == null) {
         limit = 25;
      }
      return DatasetLog.find("testId = ?1 AND source = ?2", Sort.descending("timestamp"), testId, source)
            .page(Page.of(page, limit)).list();
   }

   @Override
   @WithRoles
   @RolesAllowed(Roles.TESTER)
   public long getDatasetLogCount(String source, Integer testId) {
      if (testId == null) return -1;
      return DatasetLog.count("testId = ?1 AND source = ?2", testId, source);
   }

   @Override
   @RolesAllowed(Roles.TESTER)
   @WithRoles
   @Transactional
   public void deleteDatasetLogs(String source, Integer testId, Long from, Long to) {
      if (testId == null) {
         throw ServiceException.badRequest("Missing test ID");
      }
      // Not using Instant.MIN/Instant.MAX as Hibernate converts to LocalDateTime internally
      Instant fromTs = from == null ? Instant.ofEpochMilli(0) : Instant.ofEpochMilli(from);
      Instant toTs = to == null ? Instant.ofEpochSecond(4 * (long) Integer.MAX_VALUE) : Instant.ofEpochMilli(to);
      long deleted = DatasetLog.delete("testId = ?1 AND source = ?2 AND timestamp >= ?3 AND timestamp < ?4", testId, source, fromTs, toTs);
      log.debugf("Deleted %d logs for test %s", deleted, testId);
   }

   @ConsumeEvent(value = Test.EVENT_DELETED, blocking = true)
   @Transactional
   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   public void onTestDelete(Test test) {
      DatasetLog.delete("testid", test.id);
   }
}
