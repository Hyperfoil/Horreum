package io.hyperfoil.tools.horreum.entity.alerting;

import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.validation.constraints.NotNull;

import io.hyperfoil.tools.horreum.entity.data.LabelDAO;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Variable emits a single value from the {@link RunDAO#data}
 * using {@link LabelDAO labels} and
 * JavaScript code in {@link #calculation} (calculation is not necessary if there's a single accessor).
 *
 */
@Entity(name = "variable")
public class VariableDAO extends PanacheEntityBase {
   @JsonProperty(required = true)
   @Id
   @GenericGenerator(
         name = "variableIdGenerator",
         strategy = "io.hyperfoil.tools.horreum.entity.SeqIdGenerator",
         parameters = {
               @Parameter(name = SequenceStyleGenerator.SEQUENCE_PARAM, value = SequenceStyleGenerator.DEF_SEQUENCE_NAME),
               @Parameter(name = SequenceStyleGenerator.INCREMENT_PARAM, value = "1"),
         }
   )
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "variableIdGenerator")
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
   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public JsonNode labels;

   @JsonInclude(Include.NON_NULL)
   public String calculation;

   @Schema(required = true, implementation = ChangeDetectionDAO[].class)
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
