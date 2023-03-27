package io.hyperfoil.tools.horreum.entity.data;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.hibernate.annotations.Type;
import org.hibernate.query.NativeQuery;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType;

import io.hyperfoil.tools.horreum.api.ApiIgnore;
import io.hyperfoil.tools.horreum.entity.ValidationErrorDAO;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.common.constraint.NotNull;

@Schema(name = "Dataset")
@Entity(name="dataset")
@RegisterForReflection
/**
 * Purpose of this object is to represent derived run data.
 */
public class DataSetDAO extends OwnedEntityBase {
   public static final String EVENT_NEW = "dataset/new";
   public static final String EVENT_LABELS_UPDATED = "dataset/updatedlabels";
   public static final String EVENT_MISSING_VALUES = "dataset/missing_values";
   public static final String EVENT_DELETED = "dataset/deleted";
   public static final String EVENT_VALIDATED = "dataset/validated";

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
   public RunDAO run;

   @NotNull
   public int ordinal;

   @CollectionTable
   @ElementCollection
   public Collection<ValidationErrorDAO> validationErrors;

   @JsonProperty("runId")
   public int getRunId() {
      return run.id;
   }

   @JsonProperty("runId")
   public void setRunId(int runId) {
      run = getEntityManager().getReference(RunDAO.class, runId);
   }

   @JsonIgnore
   @ApiIgnore
   public String getFingerprint() {
      @SuppressWarnings("unchecked")
      List<JsonNode> fingerprintList = getEntityManager()
            .createNativeQuery("SELECT fingerprint FROM fingerprint WHERE dataset_id = ?")
            .setParameter(1, id).unwrap(NativeQuery.class)
            .addScalar("fingerprint", JsonNodeBinaryType.INSTANCE)
            .getResultList();
      if (fingerprintList.size() > 0) {
         return fingerprintList.stream().findFirst().get().toString();
      } else {
         return "";
      }
   }

   @JsonIgnore
   public DataSetDAO.Info getInfo() {
      return new DataSetDAO.Info(id, run.id, ordinal, testid);
   }

   public static class EventNew {
      public DataSetDAO dataset;
      public boolean isRecalculation;

      public EventNew() {}

      public EventNew(DataSetDAO dataset, boolean isRecalculation) {
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
      public int testId;
      public int datasetId;
      public boolean isRecalculation;

      public LabelsUpdatedEvent() {
      }

      public LabelsUpdatedEvent(int testId, int datasetId, boolean isRecalculation) {
         this.testId = testId;
         this.datasetId = datasetId;
         this.isRecalculation = isRecalculation;
      }
   }

   public DataSetDAO() {}

   public DataSetDAO(RunDAO run, int ordinal, String description, JsonNode data) {
      this.run = run;
      this.start = run.start;
      this.stop = run.stop;
      this.testid = run.testid;
      this.owner = run.owner;
      this.access = run.access;

      this.ordinal = ordinal;
      this.description = description;
      this.data = data;
   }
   public static class Info {
      public int id;
      public int runId;
      public int ordinal;
      public int testId;

      public Info() {
      }

      public Info(int id, int runId, int ordinal, int testId) {
         this.id = id;
         this.runId = runId;
         this.ordinal = ordinal;
         this.testId = testId;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         Info info = (Info) o;
         return id == info.id && runId == info.runId && ordinal == info.ordinal && testId == info.testId;
      }

      @Override
      public int hashCode() {
         return Objects.hash(id, runId, ordinal, testId);
      }

      @Override
      public String toString() {
         return "DatasetInfo{" +
                 "id=" + id +
                 ", runId=" + runId +
                 ", ordinal=" + ordinal +
                 ", testId=" + testId +
                 '}';
      }
   }

}
