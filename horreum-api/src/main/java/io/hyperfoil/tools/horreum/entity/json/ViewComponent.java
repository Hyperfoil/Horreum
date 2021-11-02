package io.hyperfoil.tools.horreum.entity.json;

import java.util.Objects;
import java.util.stream.Stream;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Security model: view components are owned by {@link View} and this is owned by {@link Test}, therefore
 * we don't have to retain ownership info.
 */
@Entity(name = "viewcomponent")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "view_id", "headerName"}))
public class ViewComponent extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Integer id;

   @ManyToOne(fetch = FetchType.LAZY)
   @JsonIgnore
   @JoinColumn(name = "view_id")
   public View view;

   /**
    * In UI, headers are displayed based on {@link #headerOrder} and then {@link #headerName}.
    */
   @NotNull
   public int headerOrder;

   @NotNull
   public String headerName;

   @NotNull
   public String accessors;

   /**
    * When this is <code>null</code> defaults to rendering as plain text.
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public String render;

   public ViewComponent() {
   }

   public ViewComponent(String headerName, String accessors, String render) {
      this.headerName = headerName;
      this.accessors = accessors;
      this.render = render;
   }

   public String[] accessors() {
      return Stream.of(accessors.split("[,;] *")).map(String::trim).toArray(String[]::new);
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ViewComponent that = (ViewComponent) o;
      return headerOrder == that.headerOrder &&
            Objects.equals(id, that.id) &&
            Objects.equals(headerName, that.headerName) &&
            Objects.equals(accessors, that.accessors) &&
            Objects.equals(render, that.render);
   }

   @Override
   public int hashCode() {
      return Objects.hash(id, headerOrder, headerName, accessors, render);
   }
}
