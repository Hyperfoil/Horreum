package io.hyperfoil.tools.horreum.entity;

import javax.persistence.Embeddable;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.entity.alerting.Variable;

@Embeddable
public class ExperimentComparison {
   @NotNull
   @ManyToOne(optional = false, fetch = FetchType.LAZY)
   @JoinColumn(name = "variable_id")
   @JsonIgnore
   public Variable variable;

   @NotNull
   public String model;

   @NotNull
   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public JsonNode config;

   @JsonProperty("variableId")
   public void setVariableId(int id) {
      variable = Variable.getEntityManager().getReference(Variable.class, id);
   }

   @JsonProperty(value = "variableId", required = true)
   public int getVariableId() {
      return variable.id;
   }
}
