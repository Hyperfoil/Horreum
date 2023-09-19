package io.hyperfoil.tools.horreum.entity;

import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.entity.alerting.VariableDAO;
import org.hibernate.annotations.Type;

@Embeddable
public class ExperimentComparisonDAO {
   @NotNull
   @ManyToOne(optional = false, fetch = FetchType.LAZY)
   @JoinColumn(name = "variable_id")
   public VariableDAO variable;

   @NotNull
   public String model;

   @NotNull
   @Type(JsonBinaryType.class)
   @Column(columnDefinition = "jsonb")
   public JsonNode config;

   public void setVariableId(Integer id) {
      variable = VariableDAO.getEntityManager().getReference(VariableDAO.class, id);
   }

   public int getVariableId() {
      return variable.id;
   }
}
