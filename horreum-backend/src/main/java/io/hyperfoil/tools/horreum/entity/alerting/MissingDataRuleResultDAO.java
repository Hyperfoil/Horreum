package io.hyperfoil.tools.horreum.entity.alerting;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Immutable;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity(name = "MissingDataRuleResult")
@Table(name = "missingdata_ruleresult")
@Immutable
public class MissingDataRuleResultDAO extends PanacheEntityBase {
   @Embeddable
   public static class Pk implements Serializable {
      @Column(name = "rule_id", nullable = false, updatable = false)
      int ruleId;

      @Column(name = "dataset_id", nullable = false, updatable = false)
      int datasetId;

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         Pk pk = (Pk) o;
         return ruleId == pk.ruleId && datasetId == pk.datasetId;
      }

      @Override
      public int hashCode() {
         return Objects.hash(ruleId, datasetId);
      }
   }

   @EmbeddedId
   private Pk pk;

   @NotNull
   @Column(columnDefinition = "timestamp")
   public Instant timestamp;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "rule_id", insertable = false, updatable = false)
   public MissingDataRuleDAO rule;

   public MissingDataRuleResultDAO() {}

   public MissingDataRuleResultDAO(int ruleId, int datasetId, Instant timestamp) {
      this.pk = new Pk();
      pk.ruleId = ruleId;
      pk.datasetId = datasetId;
      this.timestamp = timestamp;
   }

   public int ruleId() {
      return pk.ruleId;
   }

   public int datasetId() {
      return pk.datasetId;
   }

   public static void deleteForDataset(int id) {
      MissingDataRuleResultDAO.delete("pk.datasetId", id);
   }

   public static void deleteForDataRule(int id) {
      MissingDataRuleResultDAO.delete("pk.ruleId", id);
   }

   public static void deleteOlder(int ruleId, Instant timestamp) {
      MissingDataRuleResultDAO.delete("pk.ruleId = ?1 AND timestamp < ?2", ruleId, timestamp);
   }

   @Override
   public String toString() {
      return "MissingDataRuleResult{" +
            "dataset_id=" + pk.datasetId +
            ", rule_id=" + pk.ruleId +
            ", timestamp=" + timestamp +
            '}';
   }
}
