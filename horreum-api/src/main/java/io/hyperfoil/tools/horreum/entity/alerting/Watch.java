package io.hyperfoil.tools.horreum.entity.alerting;

import java.util.List;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

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

   @ElementCollection(fetch = FetchType.EAGER)
   @Fetch(FetchMode.SELECT)
   public List<String> users;

   @ElementCollection(fetch = FetchType.EAGER)
   @Fetch(FetchMode.SELECT)
   public List<String> optout;

   @ElementCollection(fetch = FetchType.EAGER)
   @Fetch(FetchMode.SELECT)
   public List<String> teams;
}
