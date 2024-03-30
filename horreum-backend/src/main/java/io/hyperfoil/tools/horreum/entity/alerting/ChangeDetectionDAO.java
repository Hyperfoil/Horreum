package io.hyperfoil.tools.horreum.entity.alerting;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hyperfoil.tools.horreum.entity.SeqIdGenerator;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import static jakarta.persistence.GenerationType.SEQUENCE;
import static org.hibernate.id.OptimizableGenerator.INCREMENT_PARAM;

@Entity(name = "ChangeDetection")
@JsonIgnoreType
public class ChangeDetectionDAO extends PanacheEntityBase {
   @Id
   @GenericGenerator(
         name = "changeDetectionIdGenerator",
         type = SeqIdGenerator.class,
         parameters = { @Parameter(name = INCREMENT_PARAM, value = "1") }
   )
   @GeneratedValue(strategy = SEQUENCE, generator = "changeDetectionIdGenerator")
   public Integer id;

   @NotNull
   public String model;//current db options: [fixedThreshold, relativeDifference]

   /*
    * I see two basic shapes atm:
    * model=relativeDifference: {"filter": "mean", "window": 1, "threshold": 0.2, "minPrevious": 5}
    * model=fixedThreshold: {"max": {"value": null, "enabled": false, "inclusive": true}, "min": {"value": 95, "enabled": true, "inclusive": true}}
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
