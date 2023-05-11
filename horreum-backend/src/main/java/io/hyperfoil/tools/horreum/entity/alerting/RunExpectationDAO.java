package io.hyperfoil.tools.horreum.entity.alerting;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "run_expectation")
public class RunExpectationDAO extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Long id;

   @NotNull
   public int testId;

   @NotNull
   @JdbcTypeCode(SqlTypes.TIMESTAMP)
   public Instant expectedBefore;

   public String expectedBy;

   public String backlink;
}
