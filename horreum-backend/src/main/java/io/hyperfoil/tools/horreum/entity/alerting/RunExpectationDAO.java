package io.hyperfoil.tools.horreum.entity.alerting;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity(name = "run_expectation")
public class RunExpectationDAO extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Long id;

   @NotNull
   public int testId;

   @NotNull
   @Column(columnDefinition = "timestamptz")
   public Instant expectedBefore;

   public String expectedBy;

   public String backlink;
}
