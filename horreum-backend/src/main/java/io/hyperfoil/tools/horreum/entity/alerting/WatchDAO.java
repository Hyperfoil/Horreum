package io.hyperfoil.tools.horreum.entity.alerting;

import io.hyperfoil.tools.horreum.entity.CustomSequenceGenerator;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.List;

/**
 * Records parties interested in new {@link ChangeDAO changes} in given test.
 * It's not possible to subscribe to individual {@link VariableDAO}; all variables are watched.
 */
@Entity(name = "watch")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "testid"))
public class WatchDAO extends PanacheEntityBase {
   @Id
   @CustomSequenceGenerator(
         name = "subscriptionidgenerator",
         allocationSize = 1
   )
   public Integer id;

   @OneToOne(fetch = FetchType.LAZY, optional = false)
   // We are not using foreign-key constraint as we propagate the test-deletion event (which should remove watches)
   // over eventbus and delete the watch in an independent transaction.
   @JoinColumn(name = "testid", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT), updatable = false)
   public TestDAO test;

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

   private Integer getTestId() {
      return test.id;
   }

   private void setTestId(int id) {
      this.test = TestDAO.getEntityManager().getReference(TestDAO.class, id);
   }

   @Override
   public String toString() {
      return "Watch{" +
            "id=" + id +
            ", test=" + test.id +
            ", users=" + users +
            ", optout=" + optout +
            ", teams=" + teams +
            '}';
   }
}
