package io.hyperfoil.tools.horreum.entity.alerting;

import java.util.List;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Records parties interested in new {@link Change changes} in given test.
 * It's not possible to subscribe to individual {@link Criterion}; all criteria are watched.
 */
@Entity(name = "watch")
public class Watch extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Integer id;

   public int testId;

   @ElementCollection
   public List<String> users;

   @ElementCollection
   public List<String> teams;
}
