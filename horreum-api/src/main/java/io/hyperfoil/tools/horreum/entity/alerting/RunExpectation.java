package io.hyperfoil.tools.horreum.entity.alerting;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity(name = "run_expectation")
public class RunExpectation extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Long id;

   @NotNull
   public int testId;

   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public JsonNode tags;

   @NotNull
   @Column(columnDefinition = "timestamp")
   public Instant expectedBefore;

   public String expectedBy;

   public String backlink;
}
