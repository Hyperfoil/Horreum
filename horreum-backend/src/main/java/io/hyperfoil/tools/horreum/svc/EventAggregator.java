package io.hyperfoil.tools.horreum.svc;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import io.hyperfoil.tools.horreum.bus.MessageBus;
import io.hyperfoil.tools.horreum.entity.alerting.ChangeDAO;
import io.hyperfoil.tools.horreum.entity.data.DataSetDAO;
import io.hyperfoil.tools.horreum.events.DatasetChanges;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.runtime.Startup;
import io.vertx.core.Vertx;

@Startup
@ApplicationScoped
public class EventAggregator {
   private final Map<Integer, DatasetChanges> datasetChanges = new HashMap<>();

   @Inject
   Vertx vertx;

   @Inject
   MessageBus messageBus;

   private long timerId = -1;

   @PostConstruct
   void init() {
      messageBus.subscribe(ChangeDAO.EVENT_NEW, "EventAggregator", ChangeDAO.Event.class, this::onNewChange);
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   public synchronized void onNewChange(ChangeDAO.Event event) {
      datasetChanges.computeIfAbsent(event.dataset.id, id -> {
         String fingerprint = DataSetDAO.getEntityManager().getReference(DataSetDAO.class, event.dataset.id).getFingerprint();
         return new DatasetChanges(event.dataset, fingerprint, event.testName, event.notify);
      }).addChange(event);
      handleDatasetChanges();
   }

   @Transactional
   void handleDatasetChanges() {
      long now = System.currentTimeMillis();
      while (true) {
         DatasetChanges next = this.datasetChanges.values().stream().reduce((dc1, dc2) -> dc1.emitTimestamp() < dc2.emitTimestamp() ? dc1 : dc2).orElse(null);
         if (next == null) {
            return;
         } else if (next.emitTimestamp() <= now) {
            messageBus.publish(DatasetChanges.EVENT_NEW, next.dataset.testId, next);
            datasetChanges.remove(next.dataset.id);
         } else {
            if (timerId >= 0) {
               vertx.cancelTimer(timerId);
            }
            timerId = vertx.setTimer(next.emitTimestamp() - now,
                  timerId -> messageBus.executeForTest(next.dataset.testId, this::handleDatasetChanges));
            return;
         }
      }
   }

}
