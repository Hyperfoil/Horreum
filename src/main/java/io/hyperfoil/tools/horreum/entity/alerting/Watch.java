package io.hyperfoil.tools.horreum.entity.alerting;

import java.util.List;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

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

   public int testId;

   @ElementCollection
   public List<String> users;

   @ElementCollection
   public List<String> optout;

   @ElementCollection
   public List<String> teams;
}
