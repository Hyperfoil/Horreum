package io.hyperfoil.tools.horreum.entity.data;

import java.util.Objects;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Security model: view components are owned by {@link ViewDAO} and this is owned by {@link TestDAO}, therefore
 * we don't have to retain ownership info.
 */
@Entity(name = "viewcomponent")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "view_id", "headerName"}))
public class ViewComponent extends PanacheEntityBase {
   @JsonProperty(required = true)
   @Id
   @GenericGenerator(
         name = "viewComponentIdGenerator",
         strategy = "io.hyperfoil.tools.horreum.entity.SeqIdGenerator",
         parameters = {
               @Parameter(name = SequenceStyleGenerator.SEQUENCE_PARAM, value = SequenceStyleGenerator.DEF_SEQUENCE_NAME),
               @Parameter(name = SequenceStyleGenerator.INCREMENT_PARAM, value = "1"),

         }
   )
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "viewComponentIdGenerator")
   public Integer id;

   @ManyToOne(fetch = FetchType.LAZY)
   @JsonIgnore
   @JoinColumn(name = "view_id")
   public ViewDAO view;

   /**
    * In UI, headers are displayed based on {@link #headerOrder} and then {@link #headerName}.
    */
   @NotNull
   public int headerOrder;

   @NotNull
   public String headerName;

   @NotNull
   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public JsonNode labels;

   /**
    * When this is <code>null</code> defaults to rendering as plain text.
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public String render;

   public ViewComponent() {
   }

   public ViewComponent(String headerName, String render, String... labels) {
      this.headerName = headerName;
      ArrayNode labelsNode = JsonNodeFactory.instance.arrayNode();
      for (String l : labels) {
         labelsNode.add(l);
      }
      this.labels = labelsNode;
      this.render = render;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ViewComponent that = (ViewComponent) o;
      return headerOrder == that.headerOrder &&
            Objects.equals(id, that.id) &&
            Objects.equals(headerName, that.headerName) &&
            Objects.equals(labels, that.labels) &&
            Objects.equals(render, that.render);
   }

   @Override
   public int hashCode() {
      return Objects.hash(id, headerOrder, headerName, labels, render);
   }
}
