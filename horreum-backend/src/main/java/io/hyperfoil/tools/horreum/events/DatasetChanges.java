package io.hyperfoil.tools.horreum.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.hyperfoil.tools.horreum.entity.alerting.Change;
import io.hyperfoil.tools.horreum.entity.json.DataSet;

public class DatasetChanges {
   public static final String EVENT_NEW = "datasetChanges/new";

   private static final long EMIT_DELAY = 1000;
   public DataSet.Info dataset;
   public String fingerprint;
   public String testName;
   private boolean notify;
   private final List<Change> changes = new ArrayList<>();
   private long emitTimestamp = Long.MIN_VALUE;

   public DatasetChanges() {}

   public DatasetChanges(DataSet.Info dataset, String fingerprint, String testName, boolean notify) {
      this.dataset = Objects.requireNonNull(dataset);
      this.fingerprint = fingerprint;
      this.testName = Objects.requireNonNull(testName);
      this.notify = notify;
   }

   public synchronized void addChange(Change.Event event) {
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

   public List<Change> changes() {
      return Collections.unmodifiableList(changes);
   }

   public long emitTimestamp() {
      return emitTimestamp;
   }
}
