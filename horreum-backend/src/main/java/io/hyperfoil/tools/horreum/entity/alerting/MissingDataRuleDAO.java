package io.hyperfoil.tools.horreum.entity.alerting;

import java.time.Instant;
import java.util.Objects;

import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

import com.fasterxml.jackson.databind.node.ArrayNode;

import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

// If the test has no dataset matching the rule uploaded for more than this duration (in ms)
// we send a notification about missing regular upload. If the value is non-positive
// no notifications are emitted.
@Entity(name = "MissingDataRule")
@Table(name = "missingdata_rule")
public class MissingDataRuleDAO extends PanacheEntityBase {
   @Id
   @GenericGenerator(
         name = "mdrIdGenerator",
         strategy = "io.hyperfoil.tools.horreum.entity.SeqIdGenerator",
         parameters = {
               @Parameter(name = SequenceStyleGenerator.INCREMENT_PARAM, value = "1"),
         }
   )
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mdrIdGenerator")
   public Integer id;

   public String name;

   @ManyToOne(optional = false)
   @JoinColumn(name = "test_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
   public TestDAO test;

   @Type(JsonBinaryType.class)
   @Column(columnDefinition = "jsonb")
   public ArrayNode labels;

   public String condition;

   @NotNull
   public long maxStaleness;

   @Column(name = "last_notification", columnDefinition = "timestamp")
   public Instant lastNotification;

   public int testId() {
      return test.id;
   }

   public void setTestId(int testId) {
      this.test = TestDAO.getEntityManager().getReference(TestDAO.class, testId);
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MissingDataRuleDAO that = (MissingDataRuleDAO) o;
      return maxStaleness == that.maxStaleness && Objects.equals(labels, that.labels) && Objects.equals(condition, that.condition);
   }

   @Override
   public int hashCode() {
      return Objects.hash(labels, condition, maxStaleness);
   }
}
