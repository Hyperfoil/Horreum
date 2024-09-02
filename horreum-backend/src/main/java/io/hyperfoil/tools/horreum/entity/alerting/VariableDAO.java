package io.hyperfoil.tools.horreum.entity.alerting;

import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.entity.CustomSequenceGenerator;
import io.hyperfoil.tools.horreum.entity.data.LabelDAO;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Variable emits a single value from the {@link RunDAO#data}
 * using {@link LabelDAO labels} and
 * JavaScript code in {@link #calculation} (calculation is not necessary if there's a single accessor).
 *
 */
@Entity(name = "variable")
@JsonIgnoreType
public class VariableDAO extends PanacheEntityBase {
    @Id
    @CustomSequenceGenerator(name = "variableidgenerator", allocationSize = 1)
    public Integer id;

    @NotNull
    public int testId;

    @NotNull
    public String name;

    @Column(name = "\"group\"")
    public String group;

    @Column(name = "\"order\"")
    @NotNull
    public int order;

    @NotNull
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public JsonNode labels;

    public String calculation;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "variable")
    public Set<ChangeDetectionDAO> changeDetection;

    @Override
    public String toString() {
        return "Variable{" +
                "id=" + id +
                ", testId=" + testId +
                ", name='" + name + '\'' +
                ", group='" + group + '\'' +
                ", order=" + order +
                ", labels=" + labels +
                ", calculation='" + calculation + '\'' +
                ", changeDetection=" + changeDetection +
                '}';
    }

    public void ensureLinked() {
        changeDetection.forEach(cd -> {
            cd.variable = this;
        });
    }

    public void flushIds() {
        id = null;
        changeDetection.forEach(cd -> {
            cd.id = null;
        });
    }
}
