package io.hyperfoil.tools.horreum.svc;

import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.events.DatasetChanges;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.runtime.Startup;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

@Startup
@ApplicationScoped
public class EventAggregator {
   private final Map<Integer, DatasetChanges> datasetChanges = new HashMap<>();

   @Inject
   Vertx vertx;

   @Inject
   EventBus eventBus;

   private long timerId = -1;

   @ConsumeEvent(value = Change.EVENT_NEW, blocking = true, ordered = true)
   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   public synchronized void onNewChange(Change.Event event) {
      datasetChanges.computeIfAbsent(event.dataset.id, id -> {
         String fingerprint = DataSet.getEntityManager().getReference(DataSet.class, event.dataset.id).getFingerprint();
         return new DatasetChanges(event.dataset, fingerprint, event.testName, event.notify);
      }).addChange(event);
      handleDatasetChanges();
   }

   private void handleDatasetChanges() {
      long now = System.currentTimeMillis();
      while (true) {
         DatasetChanges next = this.datasetChanges.values().stream().reduce((dc1, dc2) -> dc1.emitTimestamp() < dc2.emitTimestamp() ? dc1 : dc2).orElse(null);
         if (next == null) {
            return;
         } else if (next.emitTimestamp() <= now) {
            eventBus.publish(DatasetChanges.EVENT_NEW, next);
            datasetChanges.remove(next.dataset.id);
         } else {
            if (timerId >= 0) {
               vertx.cancelTimer(timerId);
            }
            timerId = vertx.setTimer(next.emitTimestamp() - now, timerId -> handleDatasetChanges());
            return;
         }
      }
   }

}
