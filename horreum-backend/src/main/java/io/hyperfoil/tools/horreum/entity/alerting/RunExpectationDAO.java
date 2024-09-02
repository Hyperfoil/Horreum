package io.hyperfoil.tools.horreum.entity.alerting;

import static jakarta.persistence.GenerationType.SEQUENCE;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity(name = "run_expectation")
public class RunExpectationDAO extends PanacheEntityBase {
    @Id
    @SequenceGenerator(name = "runExpectationIdGenerator", sequenceName = "run_expectation_seq")
    @GeneratedValue(strategy = SEQUENCE, generator = "runExpectationIdGenerator")
    public Long id;

    @NotNull
    public int testId;

    @NotNull
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    public Instant expectedBefore;

    public String expectedBy;

    public String backlink;
}
