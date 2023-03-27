package io.hyperfoil.tools.horreum.entity.alerting;

import java.time.Instant;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.ConstraintMode;
import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

// If the test has no dataset matching the rule uploaded for more than this duration (in ms)
// we send a notification about missing regular upload. If the value is non-positive
// no notifications are emitted.
@Entity(name = "MissingDataRule")
@Table(name = "missingdata_rule")
public class MissingDataRuleDAO extends PanacheEntityBase {
   @JsonProperty(required = true)
   @Id
   @GenericGenerator(
         name = "mdrIdGenerator",
         strategy = "io.hyperfoil.tools.horreum.entity.SeqIdGenerator",
         parameters = {
               @Parameter(name = SequenceStyleGenerator.SEQUENCE_PARAM, value = SequenceStyleGenerator.DEF_SEQUENCE_NAME),
               @Parameter(name = SequenceStyleGenerator.INCREMENT_PARAM, value = "1"),
         }
   )
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mdrIdGenerator")
   public Integer id;

   public String name;

   @ManyToOne(optional = false)
   @JoinColumn(name = "test_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
   @JsonIgnore
   public TestDAO test;

   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public ArrayNode labels;

   public String condition;

   @NotNull
   public long maxStaleness;

   @Column(name = "last_notification", columnDefinition = "timestamp")
   public Instant lastNotification;

   @JsonProperty("testId")
   public int testId() {
      return test.id;
   }

   @JsonProperty(value = "testId", required = true)
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
