package io.hyperfoil.tools.horreum.entity.json;

import java.time.Instant;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.common.constraint.NotNull;

@Entity(name="dataset")
@RegisterForReflection
/**
 * Purpose of this object is to represent derived run data.
 */
public class DataSet extends OwnedEntityBase {
   public static final String EVENT_NEW = "dataset/new";
   public static final String EVENT_LABELS_UPDATED = "dataset/updatedlabels";
   public static final String EVENT_MISSING_VALUES = "dataset/missing_values";

   @Id
   @SequenceGenerator(
         name="datasetSequence",
         sequenceName="dataset_id_seq",
         allocationSize=1)
   @GeneratedValue(strategy=GenerationType.SEQUENCE,
         generator= "datasetSequence")
   public Integer id;

   @NotNull
   @Column(name="start", columnDefinition = "timestamp")
   public Instant start;

   @NotNull
   @Column(name="stop", columnDefinition = "timestamp")
   public Instant stop;

   public String description;

   @NotNull
   public Integer testid;

   @NotNull
   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   @Basic(fetch = FetchType.LAZY)
   public JsonNode data;

   @ManyToOne(cascade = CascadeType.DETACH, fetch = FetchType.LAZY)
   @JoinColumn(name = "runid")
   @JsonIgnore
   public Run run;

   @NotNull
   public int ordinal;

   public static class EventNew {
      public final DataSet dataset;
      public final boolean isRecalculation;

      public EventNew(DataSet dataset, boolean isRecalculation) {
         this.dataset = dataset;
         this.isRecalculation = isRecalculation;
      }

      @Override
      public String toString() {
         return "DataSet.EventNew{" +
               "dataset=" + dataset.id + " (" + dataset.run.id + "/" + dataset.ordinal +
               "), isRecalculation=" + isRecalculation +
               '}';
      }
   }

   public static class LabelsUpdatedEvent {
      public final int datasetId;
      public final boolean isRecalculation;

      public LabelsUpdatedEvent(int datasetId, boolean isRecalculation) {
         this.datasetId = datasetId;
         this.isRecalculation = isRecalculation;
      }
   }

   public DataSet() {}

   public DataSet(Instant st, Instant stp, String desc, Integer testId, JsonNode json, Run r, int ord, String ownr, Access acc) {
      this.start = st;
      stop = stp;
      description = desc;
      testid = testId;
      data = json;
      run = r;
      ordinal = ord;
      owner = ownr;
      access = acc;
   }

   public static class Info {
      public int id;
      public int runId;
      public int ordinal;

      public Info() {
      }

      public Info(int id, int runId, int ordinal) {
         this.id = id;
         this.runId = runId;
         this.ordinal = ordinal;
      }

      @Override
      public String toString() {
         return "DatasetInfo{" +
               "id=" + id +
               ", runId=" + runId +
               ", ordinal=" + ordinal +
               '}';
      }
   }
}
