package io.hyperfoil.tools.repo.entity.json;

import java.util.Objects;

import javax.json.bind.annotation.JsonbTransient;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Security model: view components are owned by {@link View} and this is owned by {@link Test}, therefore
 * we don't have to retain ownership info.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "view_id", "headerName"}))
public class ViewComponent extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Long id;

   @ManyToOne(fetch = FetchType.LAZY)
   @JsonbTransient
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
   public String accessor;

   // TODO: we'll probably change this into enum: PLAIN, ARRAY, SUM, MAX, MIN, COUNT
   @NotNull
   @Column(columnDefinition = "false")
   public Boolean isArray;

   /**
    * When this is <code>null</code> defaults to rendering as plain text.
    */
   public String render;

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ViewComponent that = (ViewComponent) o;
      return headerOrder == that.headerOrder &&
            Objects.equals(id, that.id) &&
            Objects.equals(headerName, that.headerName) &&
            Objects.equals(accessor, that.accessor) &&
            Objects.equals(render, that.render);
   }

   @Override
   public int hashCode() {
      return Objects.hash(id, headerOrder, headerName, accessor, render);
   }
}
