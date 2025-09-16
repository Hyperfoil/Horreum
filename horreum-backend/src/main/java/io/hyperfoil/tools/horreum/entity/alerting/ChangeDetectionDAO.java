package io.hyperfoil.tools.horreum.entity.alerting;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity(name = "ChangeDetection")
@JsonIgnoreType
public class ChangeDetectionDAO extends PanacheEntityBase {
    @Id
    @SequenceGenerator(name = "changedetectionidgenerator", sequenceName = "changedetectionidgenerator", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "changedetectionidgenerator")
    public Integer id;

    @NotNull
    public String model;//current db options: [fixedThreshold, relativeDifference]

    /*
     * I see two basic shapes atm:
     * model=relativeDifference: {"filter": "mean", "window": 1, "threshold": 0.2, "minPrevious": 5}
     * model=fixedThreshold: {"max": {"value": null, "enabled": false, "inclusive": true}, "min": {"value": 95, "enabled": true,
     * "inclusive": true}}
     */
    @NotNull
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public ObjectNode config;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variable_id")
    public VariableDAO variable;

    @Override
    public String toString() {
        return "ChangeDetection{" +
                "id=" + id +
                ", model='" + model + '\'' +
                ", config=" + config +
                '}';
    }
}
