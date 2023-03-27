package io.hyperfoil.tools.horreum.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.hyperfoil.tools.horreum.entity.alerting.ChangeDAO;
import io.hyperfoil.tools.horreum.entity.data.DataSetDAO;

public class DatasetChanges {
   public static final String EVENT_NEW = "datasetChanges/new";

   private static final long EMIT_DELAY = 1000;
   public DataSetDAO.Info dataset;
   public String fingerprint;
   public String testName;
   private boolean notify;
   private final List<ChangeDAO> changes = new ArrayList<>();
   private long emitTimestamp = Long.MIN_VALUE;

   public DatasetChanges() {}

   public DatasetChanges(DataSetDAO.Info dataset, String fingerprint, String testName, boolean notify) {
      this.dataset = Objects.requireNonNull(dataset);
      this.fingerprint = fingerprint;
      this.testName = Objects.requireNonNull(testName);
      this.notify = notify;
   }

   public synchronized void addChange(ChangeDAO.Event event) {
      if (!event.dataset.equals(dataset) || !event.testName.equals(testName)) {
         throw new IllegalStateException();
      }
      notify = notify || event.notify;
      emitTimestamp = System.currentTimeMillis() + EMIT_DELAY;
      changes.add(event.change);
   }

   public boolean isNotify() {
      return notify;
   }

   public List<ChangeDAO> changes() {
      return Collections.unmodifiableList(changes);
   }

   public long emitTimestamp() {
      return emitTimestamp;
   }
}
