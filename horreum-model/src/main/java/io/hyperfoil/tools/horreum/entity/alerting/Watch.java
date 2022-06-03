package io.hyperfoil.tools.horreum.entity.alerting;

import java.util.List;

import javax.persistence.ConstraintMode;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.tools.horreum.entity.json.Test;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Records parties interested in new {@link Change changes} in given test.
 * It's not possible to subscribe to individual {@link Variable}; all variables are watched.
 */
@Entity(name = "watch")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "testid"))
public class Watch extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Integer id;

   @OneToOne(fetch = FetchType.LAZY, optional = false)
   // We are not using foreign-key constraint as we propagate the test-deletion event (which should remove watches)
   // over eventbus and delete the watch in an independent transaction.
   @JoinColumn(name = "testid", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
   @JsonIgnore
   public Test test;

   @NotNull
   @ElementCollection(fetch = FetchType.EAGER)
   @Fetch(FetchMode.SELECT)
   public List<String> users;

   @NotNull
   @ElementCollection(fetch = FetchType.EAGER)
   @Fetch(FetchMode.SELECT)
   public List<String> optout;

   @NotNull
   @ElementCollection(fetch = FetchType.EAGER)
   @Fetch(FetchMode.SELECT)
   public List<String> teams;

   @JsonProperty(value = "testId", required = true)
   private Integer getTestId() {
      return test.id;
   }
}
