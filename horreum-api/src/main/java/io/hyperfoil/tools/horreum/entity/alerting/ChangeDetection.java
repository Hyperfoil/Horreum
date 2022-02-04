package io.hyperfoil.tools.horreum.entity.alerting;


import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
public class ChangeDetection extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Integer id;

   @NotNull
   public String model;

   @NotNull
   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public JsonNode config;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "variable_id")
   @JsonIgnore
   public Variable variable;
}
