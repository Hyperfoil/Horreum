package io.hyperfoil.tools.horreum.svc;

import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.hyperfoil.tools.horreum.api.alerting.Change;
import io.hyperfoil.tools.horreum.bus.BlockingTaskDispatcher;
import io.hyperfoil.tools.horreum.entity.data.DatasetDAO;
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
   BlockingTaskDispatcher messageBus;

   @Inject
   ServiceMediator mediator;

   private long timerId = -1;

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   public synchronized void onNewChange(Change.Event event) {
      datasetChanges.computeIfAbsent(event.dataset.id, id -> {
         String fingerprint = DatasetDAO.getEntityManager().getReference(DatasetDAO.class, event.dataset.id).getFingerprint();
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
            mediator.executeBlocking(() -> mediator.newDatasetChanges(next)) ;
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
