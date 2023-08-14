package io.hyperfoil.tools.horreum.entity;

import javax.persistence.Embeddable;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.entity.alerting.VariableDAO;

@Embeddable
public class ExperimentComparison {
   @NotNull
   @ManyToOne(optional = false, fetch = FetchType.LAZY)
   @JoinColumn(name = "variable_id")
   public VariableDAO variable;

   @NotNull
   public String model;

   @NotNull
   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public JsonNode config;

   public void setVariableId(Integer id) {
      variable = VariableDAO.getEntityManager().getReference(VariableDAO.class, id);
   }

   public int getVariableId() {
      return variable.id;
   }
}
