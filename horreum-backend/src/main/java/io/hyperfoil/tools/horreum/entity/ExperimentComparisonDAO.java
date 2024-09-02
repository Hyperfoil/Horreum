package io.hyperfoil.tools.horreum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.entity.alerting.VariableDAO;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;

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
    public ObjectNode config;

    public void setVariableId(Integer id) {
        variable = VariableDAO.getEntityManager().getReference(VariableDAO.class, id);
    }

    public int getVariableId() {
        return variable.id;
    }
}
