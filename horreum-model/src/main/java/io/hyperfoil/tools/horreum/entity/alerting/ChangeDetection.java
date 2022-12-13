package io.hyperfoil.tools.horreum.entity.alerting;


import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
public class ChangeDetection extends PanacheEntityBase {
   @JsonProperty(required = true)
   @Id
   @GenericGenerator(
         name = "cdIdGenerator",
         strategy = "io.hyperfoil.tools.horreum.entity.SeqIdGenerator",
         parameters = {
               @Parameter(name = SequenceStyleGenerator.SEQUENCE_PARAM, value = SequenceStyleGenerator.DEF_SEQUENCE_NAME),
               @Parameter(name = SequenceStyleGenerator.INCREMENT_PARAM, value = "1"),
         }
   )
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cdIdGenerator")
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

   @Override
   public String toString() {
      return "ChangeDetection{" +
            "id=" + id +
            ", model='" + model + '\'' +
            ", config=" + config +
            '}';
   }
}
